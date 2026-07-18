# Vulkiris

A **lightweight shader mod** for Minecraft Java **26.2** that works with the new
**Vulkan rendering backend** (and OpenGL too). No shader packs, no deferred pipeline,
no shadow maps — a carefully chosen set of screen-space effects, scaled through eight
presets from *Potato* to *Real-Life*, that make the game look dramatically better for
a small, predictable GPU cost.

## Presets

Cycle in game with **P**, or pick in the settings screen (**O**) / ModMenu:

| Preset | What you get |
| --- | --- |
| 🥔 Potato | Tonemap + grade + fog only — a single fullscreen pass |
| Light | + soft bloom, light AO |
| Medium *(default)* | + god rays, light bleed, sun specular, water shading |
| Super | Stronger everything |
| Super+ | + water reflections (SSR low) |
| Fabulous | + FXAA, SSR, warm grade, full skies |
| Extreme | Maximum strengths, SSR high |
| Real-Life | Filmic neutral grade, strong AO/SSR/specular, cinematic vignette |

**Make your own:** tweak anything (the preset becomes *Custom*), then **Export to
Clipboard** and paste the JSON to a friend — they hit **Import from Clipboard** and it
installs as a user preset (stored in `config/vulkiris-presets/`, included in the
**P** cycle). Drop `.json` preset files in that folder to install them manually.

## Effects

| Effect | Inspired by | Cost |
| --- | --- | --- |
| Filmic tonemapping (ACES/filmic, adjustable strength) + exposure/saturation/contrast + warm white balance | BSL-style grading | ~free (single composite pass) |
| Soft bloom + **colored light bleed** (torch/lava glow tints nearby dark areas) | Complementary glow | 3 half-res passes |
| Screen-space ambient occlusion | — | ~free (piggybacks the bloom chain's alpha channel) |
| **God rays / sun shafts** (screen-space march toward the sun) | BSL | 14 depth taps, in-pass |
| Water: animated sun glint, depth absorption, fresnel + **SSR reflections** | Photon-style water | in-pass; SSR gated by preset |
| **Sun specular** on all surfaces from depth normals ("PBR-ish" gloss) | LabPBR feel, no resource packs needed | ~free |
| Aerial-perspective fog with height falloff + sun scattering | — | ~free |
| Sky grading: sunset/sunrise gradients (vanilla clouds catch them too), deep-blue day zenith, cool nights | — | ~free |
| Underwater & rain response, vignette, optional FXAA | — | ~free / 1 pass |

Every effect lives in the *same* composite pass gated by uniforms, so higher presets
add zero extra render passes — only ALU and texture taps. Chunk rendering (including
Sodium's) is untouched.

> **Honesty note:** true voxel colored lighting and texture-based PBR (LabPBR
> normal/specular maps) require replacing terrain shaders and materials, which would
> break the lightweight, Sodium-compatible design. Vulkiris approximates those looks
> in screen space instead.

## Controls

- **P** — next preset (shows the name on the action bar)
- **O** — settings screen (two pages: *Look* and *Effects*; the world stays
  un-blurred behind it so you can judge changes live)
- **K** — toggle all effects
- *Reload Config* — unbound by default; all keys rebindable under *Controls → Vulkiris*

Also integrates with **ModMenu** (v20+): the config button opens the same settings
screen. Settings persist to `config/vulkiris.json`.

## Languages

English and **한국어 (Korean)** — follows the game language automatically.

## How it works (and why it runs on Vulkan)

Minecraft 26.2 added an experimental Vulkan backend behind **Video Settings → Graphics
API → Prefer Vulkan**. Since 1.21.5 all vanilla rendering goes through the
backend-agnostic Blaze3D `GpuDevice` API, and under Vulkan the game cross-compiles
vanilla-dialect GLSL to SPIR-V at runtime (shaderc, auto-bound uniforms).

Vulkiris leans on exactly that machinery instead of fighting it:

- One mixin (`LevelRenderer#render` at `RETURN`) runs the post chain after the world
  is drawn, before the HUD.
- A Fabric API event (`LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN`) snapshots the
  depth buffer before water is drawn — that's how the composite finds water surfaces
  and their depth, with no terrain shader replacement.
- All pipelines are built from vanilla's `POST_PROCESSING_SNIPPET`; all draws go
  through `CommandEncoder`/`RenderPass` — zero raw OpenGL calls.
- The GLSL uses the same dialect as vanilla core shaders (`#version 330`, no binding
  qualifiers), so identical sources compile on both backends.
- Depth math honors the device NDC convention (`DeviceInfo#isZZeroToOne`) and 26.2's
  reversed depth buffer.
- AO is computed into the bloom prefilter's alpha channel and denoised by the bloom's
  Gaussian — zero extra passes. God rays, SSR, and specular are uniform-gated branches
  in the composite pass.

If a shader fails to compile (Vulkan validation is stricter than GL), Vulkiris logs
the error and disables itself for the session instead of crashing the render thread —
toggle the effects or reload the config to retry.

## Requirements

- Minecraft Java **26.2** · Fabric Loader **0.18.4+** · Java **25**
- Fabric API (`fabric-lifecycle-events-v1`, `fabric-key-mapping-api-v1`,
  `fabric-rendering-v1`)
- Optional: ModMenu 20+ for the config button

Works with **Prefer Vulkan** and **Prefer OpenGL**. Compatible with Sodium.

## Building

```sh
./gradlew build
```

The jar lands in `build/libs/`. Requires Java 25 and network access to
`maven.fabricmc.net`, `maven.terraformersmc.com`, and Mojang's servers on the first
run (Minecraft 26.x is unobfuscated, so there are no mappings to configure).

## Project layout

```
src/client/java/dev/vulkiris/
  VulkirisClient.java              entrypoint, keybinds, lifecycle, event hooks
  config/VulkirisConfig.java       JSON config
  config/VulkirisPresets.java      built-in + user presets, clipboard share
  gui/VulkirisSettingsScreen.java  settings screen (O / ModMenu)
  compat/VulkirisModMenu.java      ModMenu entrypoint
  mixin/LevelRendererMixin.java    the single render hook
  render/                          pipelines, uniforms, targets, pass chain
src/client/resources/assets/vulkiris/
  lang/en_us.json, lang/ko_kr.json
  shaders/core/*.vsh|*.fsh         composite, bloom, fxaa, fullscreen
  shaders/include/params.glsl      shared uniform block
```

## License

MIT — see [LICENSE](LICENSE).
