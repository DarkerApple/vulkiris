package dev.vulkiris.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.vulkiris.render.VulkirisRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * The built-in "Vulkiris Default" shader: the preset-driven screen-space stack
 * (bloom/AO/fog/god rays/water/sky/grading) implemented in {@link VulkirisRenderer}.
 */
public final class DefaultPipeline implements VulkirisPipeline {
	public static final String ID = "vulkiris:default";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void onLevelRendered(
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f fogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		VulkirisRenderer.onLevelRendered(cameraState, levelRenderState, modelViewMatrix, fogColor, deltaTracker, mainTarget);
	}

	@Override
	public void captureWaterDepth(GameRenderer gameRenderer) {
		VulkirisRenderer.captureWaterDepth(gameRenderer);
	}

	@Override
	public void clearFailure() {
		VulkirisRenderer.clearFailure();
	}

	@Override
	public void shutdown() {
		VulkirisRenderer.shutdown();
	}
}
