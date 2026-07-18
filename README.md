# Vulkiris

A **lightweight shader mod** for Minecraft Java **26.2** that works with the new
**Vulkan rendering backend** (and OpenGL too). No shader packs, no deferred pipeline,
no shadow maps — just a handful of carefully chosen post-processing effects that make
the game look noticeably better for a fraction of a millisecond per frame.

## Effects

| Effect | Cost | Default |
| --- | --- | --- |
| Filmic tonemapping (ACES or soft filmic, adjustable strength) + exposure/saturation/contrast + warm white balance | ~free (part of the single composite pass) | on |
| Soft bloom (half-resolution prefilter + separable Gaussian) | 3 half-res passes | on |
| Screen-space ambient occlusion | ~free (piggybacks on the bloom chain: computed in the prefilter's alpha channel and denoised by the bloom blur) | on |
| Water surface shading: animated sun glint, depth-based absorption, fresnel sky tint | ~free (same composite pass; one depth copy per frame) | on |
| Aerial-perspective fog with height falloff and sun scattering | ~free (same composite pass) | on |
| Sky grading: sunset/sunrise gradients (clouds catch them too), deep-blue day zenith, cool nights | ~free (same composite pass) | on |
| Underwater & rain color response | ~free | on |
| Subtle vignette | ~free | on |
| FXAA | 1 full-res pass | off |

With defaults the whole chain is **three half-resolution passes, one full-resolution
pass, one color copy, and one depth copy**. There are no per-block or per-entity
shader changes, so chunk rendering performance (including Sodium's) is untouched.

## Controls & settings

- **O** — open the in-game settings screen (sliders and toggles for everything;
  the world stays visible un-blurred so you can judge changes live)
- **K** — toggle all effects
- *Reload Config* — unbound by default, rebindable under *Controls → Vulkiris*

Settings persist to `config/vulkiris.json`:

```json5
{
  "enabled": true,
  "tonemap": "aces",        // "aces" | "filmic" | "off"
  "tonemapStrength": 0.7,   // 0 – 1, how strongly the curve is applied
  "exposure": 0.82,         // 0.25 – 4 ("Brightness" in the UI)
  "saturation": 1.06,
  "contrast": 1.02,
  "warmth": 0.05,           // 0 – 0.25, warm white balance
  "bloom": true,
  "bloomIntensity": 0.32,
  "bloomThreshold": 0.72,   // lower = more things glow
  "fog": true,
  "fogDensity": 0.45,
  "sunScatter": 0.6,        // warm glow around the sun in fog
  "skyIntensity": 0.6,      // sunset gradients, day/night sky grading
  "aoStrength": 0.55,       // screen-space ambient occlusion, 0 disables
  "water": true,            // water glint/absorption/fresnel
  "vignette": 0.18,
  "fxaa": false
}
```

## How it works (and why it runs on Vulkan)

Minecraft 26.2 added an experimental Vulkan backend behind **Video Settings → Graphics
API → Prefer Vulkan**. Since 1.21.5 all vanilla rendering goes through the
backend-agnostic Blaze3D `GpuDevice` API, and under Vulkan the game cross-compiles
vanilla-dialect GLSL to SPIR-V at runtime (shaderc, auto-bound uniforms).

Vulkiris leans on exactly that machinery instead of fighting it:

- One mixin (`LevelRenderer#render` at `RETURN`) runs the post chain after the world
  (terrain, entities, particles, clouds, weather) has been drawn, before the HUD.
- A Fabric API event (`LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN`) snapshots the
  depth buffer before water is drawn, which is how the composite pass finds water
  surfaces and how much water sits in front of the scene — no terrain shader
  replacement needed, so it composes with Sodium.
- All pipelines are built in code from vanilla's `POST_PROCESSING_SNIPPET` with a
  `BindGroupLayout` describing the samplers plus one std140 uniform block.
- All draws go through `CommandEncoder`/`RenderPass` — zero raw OpenGL calls.
- The GLSL is written in the **same dialect as vanilla core shaders** (`#version 330`,
  no explicit binding qualifiers), so the exact same sources compile natively on the
  OpenGL backend and through the Vulkan GLSL→SPIR-V compiler.
- The depth reconstruction reads the device's actual NDC convention
  (`DeviceInfo#isZZeroToOne`) and handles 26.2's reversed depth buffer.
- Ambient occlusion costs almost nothing extra: it is computed at half resolution in
  the bloom prefilter's alpha channel, then the bloom's separable Gaussian denoises
  it in the same passes.
- Clouds write depth in 26.2, so the sky/sunset grading blends over sky *and*
  far-distance pixels — clouds pick up sunset colors along with the sky.

If a shader fails to compile (Vulkan validation is stricter than GL), Vulkiris logs
the error and disables itself for the session instead of crashing the render thread —
toggle the effects or reload the config to retry.

## Requirements

- Minecraft Java **26.2**
- Fabric Loader **0.18.4+**
- Fabric API (uses `fabric-lifecycle-events-v1`, `fabric-key-mapping-api-v1`,
  `fabric-rendering-v1`)
- Java **25**

Works with both **Prefer Vulkan** and **Prefer OpenGL** graphics APIs. Compatible with
Sodium — Vulkiris only reads and writes the main render target.

## Building

```sh
./gradlew build
```

The jar lands in `build/libs/`. Requires Java 25 and network access to
`maven.fabricmc.net` and Mojang's servers on the first run (Minecraft 26.x is
unobfuscated, so there are no mappings to configure).

## Project layout

```
src/client/java/dev/vulkiris/
  VulkirisClient.java          entrypoint, keybinds, lifecycle, event hooks
  config/VulkirisConfig.java   JSON config
  gui/VulkirisSettingsScreen.java  in-game settings screen (O)
  mixin/LevelRendererMixin.java  the single render hook
  render/
    VulkirisPipelines.java     RenderPipeline definitions
    VulkirisUniforms.java      per-frame std140 uniform buffer
    PostFxTargets.java         offscreen render targets
    VulkirisRenderer.java      the per-frame pass chain + water depth capture
src/client/resources/assets/vulkiris/shaders/
  core/fullscreen.vsh          fullscreen triangle
  core/composite.fsh           water + fog + sky + bloom + AO + tonemap + grade
  core/bloom_prefilter.fsh     bloom threshold/downsample + SSAO (alpha)
  core/bloom_blur.fsh          separable Gaussian (H/V via shader define)
  core/fxaa.fsh                optional anti-aliasing
  include/params.glsl          shared uniform block
```

## Roadmap

- Waving foliage via terrain pipeline variants (needs a stable hook that
  doesn't conflict with Sodium's terrain path)
- Volumetric-style cloud lighting

## License

MIT — see [LICENSE](LICENSE).
