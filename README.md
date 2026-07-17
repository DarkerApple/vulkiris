# Vulkiris

A **lightweight shader mod** for Minecraft Java **26.2** that works with the new
**Vulkan rendering backend** (and OpenGL too). No shader packs, no deferred pipeline,
no shadow maps — just a handful of carefully chosen post-processing effects that make
the game look noticeably better for a fraction of a millisecond per frame.

## Effects

| Effect | Cost | Default |
| --- | --- | --- |
| Filmic tonemapping (ACES or soft filmic) + exposure/saturation/contrast grade | ~free (part of the single composite pass) | on (`aces`) |
| Soft bloom (half-resolution prefilter + separable Gaussian) | 3 half-res passes | on |
| Aerial-perspective fog with height falloff and sun scattering | ~free (same composite pass) | on |
| Underwater & rain color response | ~free | on |
| Subtle vignette | ~free | on |
| FXAA | 1 full-res pass | off |

Worst case the whole chain is **three half-resolution passes, two full-resolution
passes, and two texture copies**; with defaults it is three half-res passes and one
full-res pass. There are no per-block or per-entity shader changes, so chunk rendering
performance (including Sodium's) is untouched.

## How it works (and why it runs on Vulkan)

Minecraft 26.2 added an experimental Vulkan backend behind **Video Settings → Graphics
API → Prefer Vulkan**. Since 1.21.5 all vanilla rendering goes through the
backend-agnostic Blaze3D `GpuDevice` API, and under Vulkan the game cross-compiles
vanilla-dialect GLSL to SPIR-V at runtime (shaderc, auto-bound uniforms).

Vulkiris leans on exactly that machinery instead of fighting it:

- One mixin (`LevelRenderer#render` at `RETURN`) runs the post chain after the world
  (terrain, entities, particles, clouds, weather) has been drawn, before the HUD.
- All pipelines are built in code from vanilla's `POST_PROCESSING_SNIPPET` with a
  `BindGroupLayout` describing the samplers plus one std140 uniform block.
- All draws go through `CommandEncoder`/`RenderPass` — zero raw OpenGL calls.
- The GLSL is written in the **same dialect as vanilla core shaders** (`#version 330`,
  no explicit binding qualifiers), so the exact same sources compile natively on the
  OpenGL backend and through the Vulkan GLSL→SPIR-V compiler.
- The depth reconstruction reads the device's actual NDC convention
  (`DeviceInfo#isZZeroToOne`) and handles 26.2's reversed depth buffer, so fog is
  correct on both backends.

If a shader fails to compile (Vulkan validation is stricter than GL), Vulkiris logs
the error and disables itself for the session instead of crashing the render thread —
toggle the effects or reload the config to retry.

## Requirements

- Minecraft Java **26.2**
- Fabric Loader **0.18.4+**
- Fabric API (uses `fabric-lifecycle-events-v1` and `fabric-key-mapping-api-v1`)
- Java **25**

Works with both **Prefer Vulkan** and **Prefer OpenGL** graphics APIs. Compatible with
Sodium — Vulkiris only reads and writes the main render target.

## Controls & configuration

- **K** — toggle effects (rebindable under *Controls → Vulkiris*)
- *Reload Config* — unbound by default, rebindable

Settings live in `config/vulkiris.json` (created on first launch):

```json5
{
  "enabled": true,
  "tonemap": "aces",        // "aces" | "filmic" | "off"
  "exposure": 1.0,          // 0.25 – 4.0
  "saturation": 1.08,       // 0 – 2
  "contrast": 1.02,         // 0.5 – 1.5
  "bloom": true,
  "bloomIntensity": 0.35,   // 0 – 2
  "bloomThreshold": 0.72,   // 0 – 1, lower = more things glow
  "fog": true,
  "fogDensity": 0.45,       // 0 – 1
  "sunScatter": 0.6,        // 0 – 2, warm glow around the sun in fog
  "vignette": 0.18,         // 0 disables
  "fxaa": false
}
```

Edit the file and press the *Reload Config* key (or toggle twice) — no restart needed.

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
  VulkirisClient.java          entrypoint, keybinds, lifecycle
  config/VulkirisConfig.java   JSON config
  mixin/LevelRendererMixin.java  the single render hook
  render/
    VulkirisPipelines.java     RenderPipeline definitions
    VulkirisUniforms.java      per-frame std140 uniform buffer
    PostFxTargets.java         offscreen render targets
    VulkirisRenderer.java      the per-frame pass chain
src/client/resources/assets/vulkiris/shaders/
  core/fullscreen.vsh          fullscreen triangle
  core/composite.fsh           fog + bloom + tonemap + grade + vignette
  core/bloom_prefilter.fsh     threshold + downsample
  core/bloom_blur.fsh          separable Gaussian (H/V via shader define)
  core/fxaa.fsh                optional anti-aliasing
  include/params.glsl          shared uniform block
```

## Roadmap

- Waving foliage & water via terrain pipeline variants (needs a stable hook that
  doesn't conflict with Sodium's terrain path)
- Optional depth-based ambient occlusion
- In-game settings screen

## License

MIT — see [LICENSE](LICENSE).
