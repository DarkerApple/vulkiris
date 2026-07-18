package dev.vulkiris.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * A Vulkiris rendering pipeline. Vulkiris is a shader platform for Minecraft's Vulkan (and
 * OpenGL) backend: the built-in {@link DefaultPipeline} implements this today, and future
 * shader-pack-backed pipelines (the long-term goal: loading Iris-style packs on the vanilla
 * Vulkan backend) plug in through the same seam via {@link PipelineManager}.
 */
public interface VulkirisPipeline {
	/** Stable identifier, e.g. {@code vulkiris:default} or a shader pack id. */
	String id();

	/** Runs the pipeline's frame work; called after the level has been rendered. */
	void onLevelRendered(
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget);

	/** Called right before translucent terrain is drawn; snapshot what you need. */
	void captureWaterDepth(GameRenderer gameRenderer);

	/** Clears a remembered failure so the pipeline retries on the next frame. */
	void clearFailure();

	/** Releases every GPU resource the pipeline owns. */
	void shutdown();
}
