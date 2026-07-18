package dev.vulkiris.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.vulkiris.VulkirisClient;
import dev.vulkiris.config.VulkirisConfig;
import dev.vulkiris.pack.PackRepository;
import dev.vulkiris.pack.ShaderPack;
import dev.vulkiris.render.VulkirisUniforms;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Runs a user shader pack: an ordered list of fullscreen passes, each reading the pristine
 * scene snapshot, the live depth buffer, and the previous pass's output, plus the shared
 * {@code VulkirisParams} uniform block. Pack GLSL is compiled by the game's own backend
 * (GL natively, Vulkan via its GLSL→SPIR-V compiler) through
 * {@code GpuDevice#precompilePipeline} with a custom {@link ShaderSource} that resolves the
 * pack's files; anything else (the shared fullscreen vertex shader) falls back to the
 * regular shader manager. Vanilla clears the device pipeline cache on resource reload, so a
 * failed frame triggers a bounded recompile-and-retry before the pack is disabled.
 */
public final class PackPipeline implements VulkirisPipeline {
	private static final Vector4fc CLEAR = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
	private static final int MAX_CONSECUTIVE_FAILURES = 3;

	private final String packId;

	private ShaderPack pack;
	private final List<RenderPipeline> passPipelines = new ArrayList<>();
	private final Map<String, String> fragmentSources = new HashMap<>();
	private RenderTarget sceneCopy;
	private final List<RenderTarget> passTargets = new ArrayList<>();
	private GpuSampler linearSampler;
	private GpuSampler nearestSampler;
	private int width = -1;
	private int height = -1;
	private boolean compiled;
	private int consecutiveFailures;
	private boolean failed;
	private boolean waterDepthCaptured;
	// Small ring so per-frame writes never land in a buffer the GPU is still reading.
	private static final int SETTINGS_RING = 3;
	private final com.mojang.blaze3d.buffers.GpuBuffer[] settingsBuffers =
			new com.mojang.blaze3d.buffers.GpuBuffer[SETTINGS_RING];
	private final GpuBufferSlice[] settingsSlices = new GpuBufferSlice[SETTINGS_RING];
	private int settingsRingIndex;
	private final java.nio.ByteBuffer settingsData =
			java.nio.ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder());

	public PackPipeline(String packId) {
		this.packId = packId;
	}

	@Override
	public String id() {
		return "pack:" + this.packId;
	}

	@Override
	public void onLevelRendered(
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		VulkirisConfig config = VulkirisConfig.get();
		if (!config.enabled || this.failed) {
			return;
		}
		if (cameraState == null || cameraState.isPanoramicMode || levelRenderState == null) {
			return;
		}
		if (mainTarget == null || mainTarget.getColorTextureView() == null || mainTarget.getDepthTextureView() == null) {
			return;
		}
		try {
			this.renderFrame(config, cameraState, levelRenderState, modelViewMatrix, fogColor, deltaTracker, mainTarget);
			this.consecutiveFailures = 0;
		} catch (Throwable t) {
			// Resource reloads clear the device pipeline cache; recompile and retry before giving up.
			this.compiled = false;
			this.consecutiveFailures++;
			if (this.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
				this.failed = true;
				VulkirisClient.LOGGER.error("Shader pack '{}' failed repeatedly; disabled (reselect it to retry)", this.packId, t);
			} else {
				VulkirisClient.LOGGER.warn("Shader pack '{}' frame failed; recompiling ({}/{})",
						this.packId, this.consecutiveFailures, MAX_CONSECUTIVE_FAILURES, t);
			}
		}
	}

	private void renderFrame(
			VulkirisConfig config,
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) throws Exception {
		this.ensureLoaded();
		int targetWidth = Math.max(1, mainTarget.width);
		int targetHeight = Math.max(1, mainTarget.height);
		this.ensureTargets(targetWidth, targetHeight);
		this.ensureSamplers();
		if (!this.compiled) {
			this.compileAll();
		}

		VulkirisUniforms.prepare(config, cameraState, levelRenderState, modelViewMatrix, fogColor,
				deltaTracker, mainTarget, false, false);

		boolean waterDepthValid = this.waterDepthCaptured;
		this.waterDepthCaptured = false;

		// No encoder.submit() here — vanilla submits once per frame before present, and an
		// extra submit blocks on in-flight work, capping FPS at the display refresh rate
		// on the Vulkan backend (see VulkirisRenderer.renderChain).
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		{
			GpuBufferSlice params = VulkirisUniforms.upload(encoder);
			GpuBufferSlice packSettings = this.uploadSettings(encoder);
			encoder.copyTextureToTexture(
					mainTarget.getColorTexture(), this.sceneCopy.getColorTexture(),
					0, 0, 0, 0, 0, targetWidth, targetHeight);

			RenderTarget previous = this.sceneCopy;
			for (int i = 0; i < this.passPipelines.size(); i++) {
				boolean last = i == this.passPipelines.size() - 1;
				RenderTarget output = last ? mainTarget : this.passTargets.get(i);
				RenderPipeline pipeline = this.passPipelines.get(i);
				RenderTarget previousFinal = previous;
				try (RenderPass pass = encoder.createRenderPass(
						() -> "vulkiris " + pipeline.getLocation(),
						output.getColorTextureView(),
						Optional.empty(),
						null,
						OptionalDouble.empty())) {
					pass.setPipeline(pipeline);
					RenderSystem.bindDefaultUniforms(pass);
					pass.bindTexture("SceneColorSampler", this.sceneCopy.getColorTextureView(), this.nearestSampler);
					pass.bindTexture("SceneDepthSampler", mainTarget.getDepthTextureView(), this.nearestSampler);
					pass.bindTexture("PreviousSampler", previousFinal.getColorTextureView(), this.linearSampler);
					pass.bindTexture("WaterDepthSampler",
							waterDepthValid ? this.sceneCopy.getDepthTextureView() : mainTarget.getDepthTextureView(),
							this.nearestSampler);
					pass.setUniform("VulkirisParams", params);
					pass.setUniform("VulkirisPackSettings", packSettings);
					pass.draw(3, 1, 0, 0);
				}
				previous = output;
			}
		}
	}

	private void ensureLoaded() throws Exception {
		if (this.pack != null) {
			return;
		}
		this.pack = PackRepository.open(this.packId);
		this.fragmentSources.clear();
		this.passPipelines.clear();
		List<ShaderPack.Pass> passes = this.pack.passes();
		for (int i = 0; i < passes.size(); i++) {
			Identifier fragmentId = Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "packsrc/" + this.packId + "/pass" + i);
			this.fragmentSources.put(fragmentId.getPath(), this.pack.readShader(passes.get(i).fragment()));
			this.passPipelines.add(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "pipeline/pack/" + this.packId + "/" + i))
					.withVertexShader(Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "core/fullscreen"))
					.withFragmentShader(fragmentId)
					.withBindGroupLayout(BindGroupLayout.builder()
							.withSampler("SceneColorSampler")
							.withSampler("SceneDepthSampler")
							.withSampler("PreviousSampler")
							.withSampler("WaterDepthSampler")
							.withUniform("VulkirisParams", UniformType.UNIFORM_BUFFER)
							.withUniform("VulkirisPackSettings", UniformType.UNIFORM_BUFFER)
							.build())
					.build());
		}
		VulkirisClient.LOGGER.info("Loaded shader pack '{}' ({} passes)", this.pack.name(), passes.size());
	}

	private void compileAll() {
		ShaderSource source = this::resolveShader;
		for (RenderPipeline pipeline : this.passPipelines) {
			if (!RenderSystem.getDevice().precompilePipeline(pipeline, source).isValid()) {
				throw new IllegalStateException("Shader failed to compile: " + pipeline.getLocation());
			}
		}
		this.compiled = true;
	}

	private String resolveShader(Identifier id, ShaderType type) {
		if (VulkirisClient.MOD_ID.equals(id.getNamespace()) && type == ShaderType.FRAGMENT) {
			String packSource = this.fragmentSources.get(id.getPath());
			if (packSource != null) {
				return packSource;
			}
		}
		return Minecraft.getInstance().getShaderManager().getShader(id, type);
	}

	private void ensureTargets(int targetWidth, int targetHeight) {
		if (this.sceneCopy != null && this.width == targetWidth && this.height == targetHeight
				&& this.passTargets.size() == Math.max(0, this.passPipelines.size() - 1)) {
			return;
		}
		this.closeTargets();
		this.width = targetWidth;
		this.height = targetHeight;
		this.sceneCopy = createTarget(targetWidth, targetHeight, this.pack.waterDepth());
		List<ShaderPack.Pass> passes = this.pack.passes();
		for (int i = 0; i < passes.size() - 1; i++) {
			float scale = passes.get(i).scale();
			this.passTargets.add(createTarget(
					Math.max(1, Math.round(targetWidth * scale)),
					Math.max(1, Math.round(targetHeight * scale))));
		}
	}

	private static RenderTarget createTarget(int targetWidth, int targetHeight) {
		return createTarget(targetWidth, targetHeight, false);
	}

	private static RenderTarget createTarget(int targetWidth, int targetHeight, boolean useDepth) {
		RenderTargetDescriptor descriptor = new RenderTargetDescriptor(targetWidth, targetHeight, useDepth, CLEAR, GpuFormat.RGBA8_UNORM);
		RenderTarget target = descriptor.allocate();
		descriptor.prepare(target);
		return target;
	}

	/** Uploads the pack's declared setting values (padded to 8 floats, two vec4s). */
	private GpuBufferSlice uploadSettings(CommandEncoder encoder) {
		this.settingsRingIndex = (this.settingsRingIndex + 1) % SETTINGS_RING;
		int index = this.settingsRingIndex;
		if (this.settingsBuffers[index] == null) {
			this.settingsBuffers[index] = RenderSystem.getDevice().createBuffer(
					() -> "vulkiris pack settings " + index,
					com.mojang.blaze3d.buffers.GpuBuffer.USAGE_UNIFORM | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST,
					32);
			this.settingsSlices[index] = this.settingsBuffers[index].slice();
		}
		this.settingsData.clear();
		List<ShaderPack.Setting> settings = this.pack.settings();
		for (int i = 0; i < 8; i++) {
			this.settingsData.putFloat(i < settings.size()
					? dev.vulkiris.pack.PackSettingsStore.value(this.packId, settings.get(i))
					: 0.0f);
		}
		this.settingsData.rewind();
		encoder.writeToBuffer(this.settingsSlices[index], this.settingsData);
		return this.settingsSlices[index];
	}

	private void ensureSamplers() {
		if (this.linearSampler == null) {
			this.linearSampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.of(0.0));
		}
		if (this.nearestSampler == null) {
			this.nearestSampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.of(0.0));
		}
	}

	@Override
	public void captureWaterDepth(GameRenderer gameRenderer) {
		if (this.pack == null || !this.pack.waterDepth() || this.failed || !VulkirisConfig.get().enabled) {
			return;
		}
		RenderTarget mainTarget = gameRenderer.mainRenderTarget();
		if (mainTarget == null || mainTarget.getDepthTexture() == null || this.sceneCopy == null
				|| this.sceneCopy.getDepthTexture() == null
				|| this.width != Math.max(1, mainTarget.width) || this.height != Math.max(1, mainTarget.height)) {
			return;
		}
		try {
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
					mainTarget.getDepthTexture(), this.sceneCopy.getDepthTexture(),
					0, 0, 0, 0, 0, this.width, this.height);
			this.waterDepthCaptured = true;
		} catch (Throwable t) {
			VulkirisClient.LOGGER.warn("Shader pack '{}' water depth capture failed", this.packId, t);
		}
	}

	@Override
	public void clearFailure() {
		this.failed = false;
		this.consecutiveFailures = 0;
	}

	@Override
	public void shutdown() {
		this.closeTargets();
		this.width = -1;
		this.height = -1;
		if (this.linearSampler != null) {
			this.linearSampler.close();
			this.linearSampler = null;
		}
		if (this.nearestSampler != null) {
			this.nearestSampler.close();
			this.nearestSampler = null;
		}
		VulkirisUniforms.close();
		for (int i = 0; i < SETTINGS_RING; i++) {
			if (this.settingsBuffers[i] != null) {
				this.settingsBuffers[i].close();
				this.settingsBuffers[i] = null;
				this.settingsSlices[i] = null;
			}
		}
		this.passPipelines.clear();
		this.fragmentSources.clear();
		this.compiled = false;
		if (this.pack != null) {
			try {
				this.pack.close();
			} catch (Exception e) {
				VulkirisClient.LOGGER.warn("Error closing shader pack '{}'", this.packId, e);
			}
			this.pack = null;
		}
	}

	private void closeTargets() {
		if (this.sceneCopy != null) {
			this.sceneCopy.destroyBuffers();
			this.sceneCopy = null;
		}
		for (RenderTarget target : this.passTargets) {
			target.destroyBuffers();
		}
		this.passTargets.clear();
	}
}
