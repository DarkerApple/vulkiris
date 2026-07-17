package dev.vulkiris.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.vulkiris.VulkirisClient;
import dev.vulkiris.config.VulkirisConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Runs the Vulkiris post chain once per frame, right after the level has been rendered.
 * Worst case (bloom + FXAA on) this costs three half-resolution passes, two full-resolution
 * passes, and two texture copies; with defaults it is three half-res passes and one full-res pass.
 */
public final class VulkirisRenderer {
	private static final PostFxTargets TARGETS = new PostFxTargets();

	private static GpuSampler linearSampler;
	private static GpuSampler nearestSampler;
	private static boolean failed;

	private VulkirisRenderer() {
	}

	public static void onLevelRendered(
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		VulkirisConfig config = VulkirisConfig.get();
		if (!config.enabled || failed) {
			return;
		}
		if (cameraState == null || cameraState.isPanoramicMode || levelRenderState == null) {
			return;
		}
		if (mainTarget == null || mainTarget.getColorTextureView() == null || mainTarget.getDepthTextureView() == null) {
			return;
		}
		try {
			renderChain(config, cameraState, levelRenderState, modelViewMatrix, fogColor, deltaTracker, mainTarget);
		} catch (Throwable t) {
			// A shader that fails to compile (stricter validation under Vulkan) must not crash-loop
			// the render thread; disable ourselves until the user toggles or reloads the config.
			failed = true;
			VulkirisClient.LOGGER.error("Vulkiris post-processing failed; effects disabled (toggle or reload config to retry)", t);
		}
	}

	public static void clearFailure() {
		failed = false;
	}

	public static void shutdown() {
		TARGETS.close();
		VulkirisUniforms.close();
		VulkirisPipelines.close();
		if (linearSampler != null) {
			linearSampler.close();
			linearSampler = null;
		}
		if (nearestSampler != null) {
			nearestSampler.close();
			nearestSampler = null;
		}
	}

	private static void renderChain(
			VulkirisConfig config,
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		VulkirisPipelines.ensure();
		ensureSamplers();
		int width = Math.max(1, mainTarget.width);
		int height = Math.max(1, mainTarget.height);
		TARGETS.ensure(width, height);

		VulkirisUniforms.prepare(config, cameraState, levelRenderState, modelViewMatrix, fogColor, deltaTracker, mainTarget);

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try {
			GpuBufferSlice params = VulkirisUniforms.upload(encoder);

			copyColor(encoder, mainTarget, TARGETS.sceneCopy, width, height);
			encoder.copyTextureToTexture(
					mainTarget.getDepthTexture(), TARGETS.sceneCopy.getDepthTexture(),
					0, 0, 0, 0, 0, width, height);

			if (config.bloom) {
				fullscreenPass(encoder, VulkirisPipelines.bloomPrefilter, TARGETS.bloomA.getColorTextureView(), pass -> {
					pass.bindTexture("SceneColorSampler", TARGETS.sceneCopy.getColorTextureView(), linearSampler);
					pass.setUniform("VulkirisParams", params);
				});
				fullscreenPass(encoder, VulkirisPipelines.bloomBlurH, TARGETS.bloomB.getColorTextureView(), pass ->
						pass.bindTexture("SceneColorSampler", TARGETS.bloomA.getColorTextureView(), linearSampler));
				fullscreenPass(encoder, VulkirisPipelines.bloomBlurV, TARGETS.bloomA.getColorTextureView(), pass ->
						pass.bindTexture("SceneColorSampler", TARGETS.bloomB.getColorTextureView(), linearSampler));
			}

			fullscreenPass(encoder, VulkirisPipelines.composite, mainTarget.getColorTextureView(), pass -> {
				pass.bindTexture("SceneColorSampler", TARGETS.sceneCopy.getColorTextureView(), nearestSampler);
				pass.bindTexture("SceneDepthSampler", TARGETS.sceneCopy.getDepthTextureView(), nearestSampler);
				// Always bound: the bind group requires it even when the shader weights it to zero.
				pass.bindTexture("BloomSampler", TARGETS.bloomA.getColorTextureView(), linearSampler);
				pass.setUniform("VulkirisParams", params);
			});

			if (config.fxaa) {
				copyColor(encoder, mainTarget, TARGETS.sceneCopy, width, height);
				fullscreenPass(encoder, VulkirisPipelines.fxaa, mainTarget.getColorTextureView(), pass ->
						pass.bindTexture("SceneColorSampler", TARGETS.sceneCopy.getColorTextureView(), linearSampler));
			}
		} finally {
			encoder.submit();
		}
	}

	private static void fullscreenPass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView output, Consumer<RenderPass> bind) {
		try (RenderPass pass = encoder.createRenderPass(
				() -> "vulkiris " + pipeline.getLocation(),
				output,
				Optional.empty(),
				null,
				OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			bind.accept(pass);
			// vertexCount, instanceCount, firstVertex, firstInstance — one fullscreen triangle.
			pass.draw(3, 1, 0, 0);
		}
	}

	private static void copyColor(CommandEncoder encoder, RenderTarget from, RenderTarget to, int width, int height) {
		encoder.copyTextureToTexture(
				from.getColorTexture(), to.getColorTexture(),
				0, 0, 0, 0, 0, width, height);
	}

	private static void ensureSamplers() {
		if (linearSampler == null) {
			linearSampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR,
					1, OptionalDouble.of(0.0));
		}
		if (nearestSampler == null) {
			nearestSampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.NEAREST, FilterMode.NEAREST,
					1, OptionalDouble.of(0.0));
		}
	}
}
