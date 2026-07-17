package dev.vulkiris.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.vulkiris.config.VulkirisConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Owns the per-frame std140 uniform buffer shared by the Vulkiris shaders. The layout must match
 * the {@code VulkirisParams} block in {@code assets/vulkiris/shaders/include/params.glsl}:
 *
 * <pre>
 * mat4 InvProjection   (offset   0)
 * vec4 SunDir          (offset  64)  xyz view-space sun direction, w sun visibility
 * vec4 UpDir           (offset  80)  xyz view-space world up,      w camera world Y
 * vec4 Fog             (offset  96)  rgb fog color,                w fog density setting
 * vec4 Screen          (offset 112)  xy screen size, z time seconds, w underwater flag
 * vec4 GradeA          (offset 128)  exposure, saturation, contrast, vignette strength
 * vec4 BloomParams     (offset 144)  bloom intensity, bloom threshold, sun scatter, depth-is-zero-to-one flag
 * vec4 Toggles         (offset 160)  tonemap mode, fog enabled, bloom enabled, rain factor
 * </pre>
 */
public final class VulkirisUniforms {
	private static final int SIZE_BYTES = 176;

	private static final ByteBuffer data = ByteBuffer.allocateDirect(SIZE_BYTES).order(ByteOrder.nativeOrder());
	private static final Matrix4f invProjection = new Matrix4f();
	private static final Vector3f sunDirWorld = new Vector3f();
	private static final Vector3f sunDirView = new Vector3f();
	private static final Vector3f upDirView = new Vector3f();
	private static final Vector3f fogColor = new Vector3f();

	private static GpuBuffer buffer;
	private static GpuBufferSlice slice;

	private VulkirisUniforms() {
	}

	public static void prepare(
			VulkirisConfig config,
			CameraRenderState cameraState,
			LevelRenderState levelRenderState,
			Matrix4fc modelViewMatrix,
			Vector4f vanillaFogColor,
			DeltaTracker deltaTracker,
			RenderTarget mainTarget) {
		invProjection.set(cameraState.projectionMatrix).invert();

		SkyRenderState sky = levelRenderState.skyRenderState;
		float sunAngle = sky.sunAngle;
		// SkyRenderer draws the sun with RotY(-90°) · RotX(sunAngle) applied to +Y, which is this vector.
		sunDirWorld.set(-(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0.0f);

		float rain = 0.0f;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			rain = clamp01(minecraft.level.getRainLevel(1.0f));
		}
		float sunVisibility = clamp01((sunDirWorld.y + 0.08f) / 0.24f) * (1.0f - rain * 0.6f);

		Matrix4fc view = modelViewMatrix != null ? modelViewMatrix : cameraState.viewRotationMatrix;
		view.transformDirection(sunDirWorld, sunDirView).normalize();
		view.transformDirection(0.0f, 1.0f, 0.0f, upDirView).normalize();

		unpackRgb(sky.skyColor, fogColor);
		if (vanillaFogColor != null) {
			// Blend toward the fog color the game actually used this frame (biome/weather aware).
			fogColor.lerp(new Vector3f(vanillaFogColor.x, vanillaFogColor.y, vanillaFogColor.z), 0.5f);
		}

		float partialTick = deltaTracker == null ? 0.0f : deltaTracker.getGameTimeDeltaPartialTick(false);
		float timeSeconds = ((levelRenderState.gameTime % 24000L) + partialTick) / 20.0f;
		boolean underwater = cameraState.fogType == FogType.WATER;
		boolean depthZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();

		data.clear();
		invProjection.get(data);
		data.position(64);
		putVec4(sunDirView.x, sunDirView.y, sunDirView.z, sunVisibility);
		putVec4(upDirView.x, upDirView.y, upDirView.z, (float) cameraState.pos.y);
		putVec4(fogColor.x, fogColor.y, fogColor.z, config.fogDensity);
		putVec4(Math.max(1, mainTarget.width), Math.max(1, mainTarget.height), timeSeconds, underwater ? 1.0f : 0.0f);
		putVec4(config.exposure, config.saturation, config.contrast, config.vignette);
		putVec4(config.bloomIntensity, config.bloomThreshold, config.sunScatter, depthZeroToOne ? 1.0f : 0.0f);
		putVec4(config.tonemapMode(), config.fog ? 1.0f : 0.0f, config.bloom ? 1.0f : 0.0f, rain);
		data.rewind();
	}

	public static GpuBufferSlice upload(CommandEncoder encoder) {
		if (buffer == null) {
			buffer = RenderSystem.getDevice().createBuffer(
					() -> "vulkiris params",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					SIZE_BYTES);
			slice = buffer.slice();
		}
		encoder.writeToBuffer(slice, data);
		return slice;
	}

	public static void close() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
			slice = null;
		}
	}

	private static void putVec4(float x, float y, float z, float w) {
		data.putFloat(x);
		data.putFloat(y);
		data.putFloat(z);
		data.putFloat(w);
	}

	private static void unpackRgb(int packed, Vector3f out) {
		out.set(((packed >> 16) & 255) / 255.0f, ((packed >> 8) & 255) / 255.0f, (packed & 255) / 255.0f);
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
