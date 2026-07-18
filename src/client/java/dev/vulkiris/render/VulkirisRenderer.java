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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Runs the Vulkiris post chain once per frame, right after the level has been rendered.
 * With default settings this is three half-resolution passes, one full-resolution pass, one
 * full-resolution color copy, and one depth copy (for water). Ambient occlusion piggybacks on
 * the bloom chain's alpha channel, sharing its blur for free.
 */
public final class VulkirisRenderer {
	private static final PostFxTargets TARGETS = new PostFxTargets();

	private static GpuSampler linearSampler;
	private static GpuSampler nearestSampler;
	private static boolean failed;
	/** Set when this frame captured the depth buffer before translucent terrain was drawn. */
	private static boolean waterDepthCaptured;

	private VulkirisRenderer() {
	}

	/**
	 * Called from {@code LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN}. Snapshots the depth
	 * buffer before water/translucent terrain writes to it, so the composite pass can find
	 * translucent surfaces and how much water sits in front of the opaque scene.
	 */
	public static void captureWaterDepth(GameRenderer gameRenderer) {
		VulkirisConfig config = VulkirisConfig.get();
		if (!config.enabled || !config.water || failed) {
			return;
		}
		RenderTarget mainTarget = gameRenderer.mainRenderTarget();
		if (mainTarget == null || mainTarget.getDepthTexture() == null) {
			return;
		}
		try {
			int width = Math.max(1, mainTarget.width);
			int height = Math.max(1, mainTarget.height);
			TARGETS.ensure(width, height);
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
					mainTarget.getDepthTexture(), TARGETS.sceneCopy.getDepthTexture(),
					0, 0, 0, 0, 0, width, height);
			waterDepthCaptured = true;
		} catch (Throwable t) {
			failed = true;
			VulkirisClient.LOGGER.error("Vulkiris water depth capture failed; effects disabled (toggle or reload config to retry)", t);
		}
	}

	public static void onLevelRendered(
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		boolean waterDepthValid = waterDepthCaptured;
		waterDepthCaptured = false;

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
			renderChain(config, cameraState, levelRenderState, modelViewMatrix, fogColor, deltaTracker, mainTarget, waterDepthValid);
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
			RenderTarget mainTarget,
			boolean waterDepthValid) {
		VulkirisPipelines.ensure();
		ensureSamplers();
		int width = Math.max(1, mainTarget.width);
		int height = Math.max(1, mainTarget.height);
		TARGETS.ensure(width, height);

		boolean bloomChainRuns = config.bloom || config.aoStrength > 0.0f;
		VulkirisUniforms.prepare(config, cameraState, levelRenderState, modelViewMatrix, fogColor,
				deltaTracker, mainTarget, waterDepthValid, bloomChainRuns);

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try {
			GpuBufferSlice params = VulkirisUniforms.upload(encoder);

			// Snapshot the scene color; depth is sampled from the live buffer (no pass writes it).
			copyColor(encoder, mainTarget, TARGETS.sceneCopy, width, height);

			if (bloomChainRuns) {
				// The prefilter writes bloom color into rgb and ambient occlusion into alpha;
				// both then share the same separable blur.
				fullscreenPass(encoder, VulkirisPipelines.bloomPrefilter, TARGETS.bloomA.getColorTextureView(), pass -> {
					pass.bindTexture("SceneColorSampler", TARGETS.sceneCopy.getColorTextureView(), linearSampler);
					pass.bindTexture("SceneDepthSampler", mainTarget.getDepthTextureView(), nearestSampler);
					pass.setUniform("VulkirisParams", params);
				});
				fullscreenPass(encoder, VulkirisPipelines.bloomBlurH, TARGETS.bloomB.getColorTextureView(), pass ->
						pass.bindTexture("SceneColorSampler", TARGETS.bloomA.getColorTextureView(), linearSampler));
				fullscreenPass(encoder, VulkirisPipelines.bloomBlurV, TARGETS.bloomA.getColorTextureView(), pass ->
						pass.bindTexture("SceneColorSampler", TARGETS.bloomB.getColorTextureView(), linearSampler));
			}

			fullscreenPass(encoder, VulkirisPipelines.composite, mainTarget.getColorTextureView(), pass -> {
				pass.bindTexture("SceneColorSampler", TARGETS.sceneCopy.getColorTextureView(), nearestSampler);
				pass.bindTexture("SceneDepthSampler", mainTarget.getDepthTextureView(), nearestSampler);
				// Pre-translucent depth snapshot; stale when water is off, but gated in the shader.
				pass.bindTexture("WaterDepthSampler", TARGETS.sceneCopy.getDepthTextureView(), nearestSampler);
				// Always bound: the bind group requires it even when bloom and AO are off.
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
