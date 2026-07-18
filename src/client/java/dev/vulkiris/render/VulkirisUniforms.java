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
 * mat4 InvViewRot      (offset  64)  view-space direction -> world-space direction
 * mat4 Projection      (offset 128)  for reprojecting view-space points (SSR, god rays)
 * vec4 SunDirView      (offset 192)  xyz view-space sun direction, w sun visibility
 * vec4 SunDirWorld     (offset 208)  xyz world-space sun direction, w sky intensity setting
 * vec4 UpDir           (offset 224)  xyz view-space world up,      w camera world Y
 * vec4 CameraPos       (offset 240)  xyz camera world position,    w rain factor
 * vec4 Fog             (offset 256)  rgb fog color,                w fog density setting
 * vec4 Screen          (offset 272)  xy screen size, z time seconds, w underwater flag
 * vec4 GradeA          (offset 288)  exposure, saturation, contrast, vignette strength
 * vec4 BloomParams     (offset 304)  bloom intensity, bloom threshold, sun scatter, depth-is-zero-to-one flag
 * vec4 Toggles         (offset 320)  tonemap mode, fog enabled, bloom enabled, tonemap strength
 * vec4 Extra           (offset 336)  warmth, ao strength, water enabled, god rays strength
 * vec4 Extra2          (offset 352)  sun specular, light bleed, ssr steps, easter-egg mode
 * vec4 SunScreen       (offset 368)  xy sun position in UV space, z on-screen flag, w easter-egg strength
 * vec4 Quality         (offset 384)  ao taps, god-ray taps, volumetric fog steps, film grain
 * vec4 Style           (offset 400)  selective bloom flag, rim light, toon flag, sunlight strength
 * </pre>
 */
public final class VulkirisUniforms {
	private static final int SIZE_BYTES = 416;

	private static final ByteBuffer data = ByteBuffer.allocateDirect(SIZE_BYTES).order(ByteOrder.nativeOrder());
	private static final Matrix4f invProjection = new Matrix4f();
	private static final Matrix4f invViewRot = new Matrix4f();
	private static final Vector3f sunDirWorld = new Vector3f();
	private static final Vector3f sunDirView = new Vector3f();
	private static final Vector3f upDirView = new Vector3f();
	private static final Vector3f fogColor = new Vector3f();
	private static final Vector4f sunClip = new Vector4f();

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
			RenderTarget mainTarget,
			boolean waterDepthValid,
			boolean bloomChainRuns) {
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
		invViewRot.set(view).invert();
		view.transformDirection(sunDirWorld, sunDirView).normalize();
		view.transformDirection(0.0f, 1.0f, 0.0f, upDirView).normalize();

		// Project the sun direction to screen UV for the god-ray march.
		sunClip.set(sunDirView.x, sunDirView.y, sunDirView.z, 0.0f);
		cameraState.projectionMatrix.transform(sunClip);
		float sunU = 0.0f;
		float sunV = 0.0f;
		float sunOnScreen = 0.0f;
		if (sunClip.w > 1.0e-4f) {
			sunU = sunClip.x / sunClip.w * 0.5f + 0.5f;
			sunV = sunClip.y / sunClip.w * 0.5f + 0.5f;
			// Allow the sun to sit a bit outside the viewport; the shafts still read correctly.
			if (sunU > -0.4f && sunU < 1.4f && sunV > -0.4f && sunV < 1.4f) {
				sunOnScreen = 1.0f;
			}
		}

		unpackRgb(sky.skyColor, fogColor);
		if (vanillaFogColor != null) {
			// Blend toward the fog color the game actually used this frame (biome/weather aware).
			fogColor.lerp(new Vector3f(vanillaFogColor.x, vanillaFogColor.y, vanillaFogColor.z), 0.5f);
		}

		float partialTick = deltaTracker == null ? 0.0f : deltaTracker.getGameTimeDeltaPartialTick(false);
		float timeSeconds = ((levelRenderState.gameTime % 24000L) + partialTick) / 20.0f;
		boolean underwater = cameraState.fogType == FogType.WATER;
		boolean depthZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		boolean waterOn = config.water && waterDepthValid && !underwater;
		float aoStrength = bloomChainRuns ? config.aoStrength : 0.0f;
		float lightBleed = config.bloom ? config.lightBleed : 0.0f;

		data.clear();
		invProjection.get(data);
		data.position(64);
		invViewRot.get(data);
		data.position(128);
		cameraState.projectionMatrix.get(data);
		data.position(192);
		putVec4(sunDirView.x, sunDirView.y, sunDirView.z, sunVisibility);
		putVec4(sunDirWorld.x, sunDirWorld.y, sunDirWorld.z, config.skyIntensity);
		putVec4(upDirView.x, upDirView.y, upDirView.z, (float) cameraState.pos.y);
		putVec4((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z, rain);
		putVec4(fogColor.x, fogColor.y, fogColor.z, config.fogDensity);
		putVec4(Math.max(1, mainTarget.width), Math.max(1, mainTarget.height), timeSeconds, underwater ? 1.0f : 0.0f);
		putVec4(config.exposure, config.saturation, config.contrast, config.vignette);
		putVec4(config.bloomIntensity, config.bloomThreshold, config.sunScatter, depthZeroToOne ? 1.0f : 0.0f);
		putVec4(config.tonemapMode(), config.fog ? 1.0f : 0.0f, config.bloom ? 1.0f : 0.0f, config.tonemapStrength);
		VulkirisEggs.tick();
		putVec4(config.warmth, aoStrength, waterOn ? 1.0f : 0.0f, config.godRays);
		putVec4(config.sunSpecular, lightBleed, config.ssrSteps, VulkirisEggs.mode());
		putVec4(sunU, sunV, sunOnScreen, VulkirisEggs.strength());
		int aoTaps = config.quality >= 2 ? 24 : config.quality == 1 ? 16 : 8;
		int rayTaps = config.quality >= 2 ? 48 : config.quality == 1 ? 28 : 14;
		int fogSteps = config.quality >= 2 ? 20 : config.quality == 1 ? 12 : 0;
		putVec4(aoTaps, rayTaps, fogSteps, config.filmGrain);
		putVec4(config.selectiveBloom ? 1.0f : 0.0f, config.rimLight, config.toon ? 1.0f : 0.0f, config.sunlight);
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
