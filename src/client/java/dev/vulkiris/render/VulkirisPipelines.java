package dev.vulkiris.render;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import dev.vulkiris.VulkirisClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Post-processing pipelines built on the backend-agnostic Blaze3D API. The GLSL sources use the
 * same dialect as vanilla core shaders, so the game compiles them natively under OpenGL and
 * cross-compiles them to SPIR-V under the Vulkan backend. Pipelines are intentionally not
 * registered with {@link RenderPipelines} — the device compiles them lazily on first use, which
 * keeps a shader failure from aborting the whole resource reload.
 */
public final class VulkirisPipelines {
	public static RenderPipeline composite;
	public static RenderPipeline compositeLite;
	public static RenderPipeline bloomPrefilter;
	public static RenderPipeline bloomBlurH;
	public static RenderPipeline bloomBlurV;
	public static RenderPipeline fxaa;

	private VulkirisPipelines() {
	}

	public static void ensure() {
		if (composite != null) {
			return;
		}
		composite = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(id("pipeline/composite"))
				.withVertexShader(id("core/fullscreen"))
				.withFragmentShader(id("core/composite"))
				.withBindGroupLayout(BindGroupLayout.builder()
						.withSampler("SceneColorSampler")
						.withSampler("SceneDepthSampler")
						.withSampler("WaterDepthSampler")
						.withSampler("BloomSampler")
						.withUniform("VulkirisParams", UniformType.UNIFORM_BUFFER)
						.build())
				.build();
		compositeLite = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(id("pipeline/composite_lite"))
				.withVertexShader(id("core/fullscreen"))
				.withFragmentShader(id("core/composite_lite"))
				.withBindGroupLayout(BindGroupLayout.builder()
						.withSampler("SceneColorSampler")
						.withSampler("SceneDepthSampler")
						.withUniform("VulkirisParams", UniformType.UNIFORM_BUFFER)
						.build())
				.build();
		bloomPrefilter = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(id("pipeline/bloom_prefilter"))
				.withVertexShader(id("core/fullscreen"))
				.withFragmentShader(id("core/bloom_prefilter"))
				.withBindGroupLayout(BindGroupLayout.builder()
						.withSampler("SceneColorSampler")
						.withSampler("SceneDepthSampler")
						.withUniform("VulkirisParams", UniformType.UNIFORM_BUFFER)
						.build())
				.build();
		bloomBlurH = blurPipeline("pipeline/bloom_blur_h", true);
		bloomBlurV = blurPipeline("pipeline/bloom_blur_v", false);
		fxaa = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(id("pipeline/fxaa"))
				.withVertexShader(id("core/fullscreen"))
				.withFragmentShader(id("core/fxaa"))
				.withBindGroupLayout(BindGroupLayout.builder()
						.withSampler("SceneColorSampler")
						.build())
				.build();
		VulkirisClient.LOGGER.debug("Vulkiris pipelines created");
	}

	public static void close() {
		composite = null;
		compositeLite = null;
		bloomPrefilter = null;
		bloomBlurH = null;
		bloomBlurV = null;
		fxaa = null;
	}

	private static RenderPipeline blurPipeline(String location, boolean horizontal) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(id(location))
				.withVertexShader(id("core/fullscreen"))
				.withFragmentShader(id("core/bloom_blur"))
				.withBindGroupLayout(BindGroupLayout.builder()
						.withSampler("SceneColorSampler")
						.build());
		if (horizontal) {
			builder.withShaderDefine("VULKIRIS_HORIZONTAL");
		}
		return builder.build();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, path);
	}
}
