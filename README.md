# Vulkiris

**Vulkan + Iris.** Vulkiris is a shader platform for Minecraft Java **26.2**'s new
**Vulkan rendering backend** (with OpenGL supported too). The long-term goal is what
the name says: bringing Iris-style shader pack loading to the vanilla Vulkan renderer.
What ships today is the platform's **built-in default shader** — a screen-space effect
stack scaled through eight presets from *Potato* to *Real-Life*, which will remain the
out-of-the-box look once pack loading lands.

> Vulkiris is an independent project, not affiliated with the Iris Shaders team.

## Architecture: platform + pipelines

Rendering flows through one seam: `PipelineManager` holds the active
`VulkirisPipeline`, and the game hooks (the `LevelRenderer` mixin and the
pre-translucent depth capture) only ever talk to that interface. The built-in effects
are `vulkiris:default` — the first implementation. Shader-pack-backed pipelines will
plug into the same seam, so packs can be switched at runtime without touching the
hooks.

### Shader packs — here now (format v1)

Vulkiris loads shader packs from the game's **`shaderpacks/`** folder (folders or
zips). In game: **O → Shader Packs…** — an Iris-style list with *Open Pack Folder*
and *Refresh* buttons; selection applies instantly and persists. Pack GLSL is
compiled by Minecraft's own backend compiler, so **one pack runs on both OpenGL and
Vulkan** unchanged.

Making a pack is a `vulkiris.pack.json` plus fragment shaders — up to eight ordered
fullscreen passes with scene color, depth, the previous pass, and the shared
`VulkirisParams` uniform block (time, sun, camera, matrices, fog) available. See
**[docs/PACK_FORMAT.md](docs/PACK_FORMAT.md)** for the full guide
(plain-text edition: [docs/VULKIRIS_PACK_GUIDE.txt](docs/VULKIRIS_PACK_GUIDE.txt)) and
[docs/example-packs/crt](docs/example-packs/crt) for a copy-paste CRT demo.

### Roadmap

1. ~~Default pipeline~~ · ~~pack discovery~~ · ~~post-chain packs (format v1)~~ — done.
2. **Format v2** — multi-input passes, custom targets/formats, water-depth input,
   pack-defined settings exposed in the Vulkiris UI.
3. **Full programs** — translate pack terrain/entity programs to vanilla-compilable
   GLSL and map them onto the Vulkan backend's pipeline system. This is the hard,
   long-tail step: the vanilla Vulkan compiler is stricter than GL and the gbuffer
   model differs, so Iris-style packs will land incrementally.

## Presets

Cycle in game with **P**, or pick in the settings screen (**O**) / ModMenu:

| Preset | Quality tier | What you get |
| --- | --- | --- |
| 🥔 Potato | Lite | Tonemap + grade + fog only — a single fullscreen pass |
| Light | Lite | + soft bloom, light AO |
| Medium *(default)* | High | + god rays (28 taps), volumetric mist, wide bloom, 16-tap AO, water shading |
| Super | High | Stronger everything |
| Super+ | **Ultra** | 48-tap god rays, 20-step volumetric fog, 24-tap AO, SSR low |
| Fabulous | **Ultra** | + FXAA, SSR high with contact refinement, warm grade, full skies |
| Extreme | **Ultra** | Maximum strengths, SSR ultra (48 steps) |
| Real-Life | **Ultra** | Filmic neutral grade, SSR ultra, strong AO/specular, film grain, cinematic vignette |

From Medium up the presets are deliberately **not** lightweight — they spend real GPU
time on quality (more ray-march steps, volumetric fog, double-pass bloom). They stay
fully screen-space though, which is exactly what keeps them **Sodium-compatible**:
Vulkiris never touches terrain rendering, only the finished frame.

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
| **God rays / sun shafts** (screen-space march toward the sun, jittered) | BSL | 14–48 depth taps by quality tier |
| **Volumetric noise fog** — animated ground mist with sun in-scattering | BSL/Complementary | 12–20 march steps, High tier and up |
| Water: animated multi-octave waves, sun glint, depth absorption, fresnel + **SSR reflections with binary-search contact refinement** | Photon-style water | in-pass; up to 48 steps by preset |
| **Sun specular** on all surfaces from depth normals ("PBR-ish" gloss) | LabPBR feel, no resource packs needed | ~free |
| **Selective bloom** — sky barely blooms, saturated emissives (torches, lava, glowstone) glow vibrantly with boosted color | Shine | ~free (mask in the prefilter) |
| **Rim lighting** — bright silhouette edges catching sky/sun light | Shine | 4 depth taps, in-pass |
| **Toon shading** — quantized light bands + dark outlines (off by default) | Shine's cel look | ~free |
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

## Easter eggs 🥚

Certain words typed in chat transform the whole screen (the message is swallowed, so
nobody sees you do it — type the word again to go back).

<details>
<summary>Spoilers</summary>

- `SANNABI` (or `산나비`) — neon-noir city: deep navy shadows, electric cyan
  highlights, warm signage accents
- `MATRIX` — everything is code
- `HEROBRINE` — you probably shouldn't have typed that

</details>

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
