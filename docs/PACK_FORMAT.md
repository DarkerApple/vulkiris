# Vulkiris shader pack format (v1)

Make your own shader and share it — a Vulkiris pack is a folder (or `.zip`) dropped into
the game's `shaderpacks/` directory. Select it in game: **O → Shader Packs…**
(there's an *Open Pack Folder* button right there).

Because packs are compiled by Minecraft's own shader compiler, the same pack runs on
**both the OpenGL and Vulkan backends** — write vanilla-dialect GLSL (`#version 330`, no
`layout(binding=…)` qualifiers) and the platform does the rest.

## Layout

```
shaderpacks/
└── my-pack/                  (or my-pack.zip)
    ├── vulkiris.pack.json
    └── shaders/
        ├── first.fsh
        └── final.fsh
```

## vulkiris.pack.json

```json
{
	"name": "My Pack",
	"author": "you",
	"passes": [
		{ "fragment": "shaders/first.fsh", "scale": 0.5 },
		{ "fragment": "shaders/final.fsh" }
	]
}
```

- `passes` — up to 8 fullscreen passes, run in order every frame after the world renders.
- `fragment` — pack-relative path to the pass's fragment shader.
- `scale` — optional (default `1.0`, range `0.1–1.0`): the pass's output resolution
  relative to the screen. Great for cheap blur chains. The **last** pass always writes
  the full-resolution screen.

## Writing a pass

Every pass is a fragment shader over a fullscreen triangle with `in vec2 texCoord`
(0–1) and `out vec4 fragColor`, plus three samplers and one uniform block available:

| Name | What it is |
| --- | --- |
| `sampler2D SceneColorSampler` | The untouched scene, snapshotted before your passes ran |
| `sampler2D SceneDepthSampler` | The live depth buffer (26.2 uses **reversed depth**: sky ≈ 0.0) |
| `sampler2D PreviousSampler` | Output of the previous pass (first pass: the scene snapshot) |
| `VulkirisParams` uniform block | Time, sun direction, camera, fog color, matrices, and more |

Declare only what you use — undeclared samplers/uniforms are fine, but anything you
declare must be one of the above.

Pull in the shared uniform block with an import (expanded by Vulkiris at load time):

```glsl
#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    // Screen.z is world time in seconds; make everything pulse:
    color *= 0.8 + 0.2 * sin(Screen.z * 3.0);
    fragColor = vec4(color, 1.0);
}
```

`#moj_import "my/util.glsl"` (quotes) imports a file from inside your pack.

See [`params.glsl`](../src/client/resources/assets/vulkiris/shaders/include/params.glsl)
for every field in the uniform block (matrices, sun/camera vectors, time, fog color…).

## Tips

- Depth → view-space position: see `composite.fsh` in this repo for the
  `InvProjection` reconstruction pattern, including the reversed-depth handling.
- Iterate fast: edit your pack, then re-select it in the pack screen (or hit
  *Refresh* + click it) — no game restart needed.
- If a shader fails to compile, Vulkiris logs the compiler error and falls back
  gracefully after a few retries; check the game log for the message.
- The Vulkan backend validates more strictly than OpenGL. If it compiles on GL but
  not Vulkan, the log's `ShaderCompileException` message says why.

## Example

Copy [`docs/example-packs/crt`](example-packs/crt) into `shaderpacks/` and select
"crt" in the pack screen — a scanline/CRT look demonstrating time-based animation,
vignette math, and the params block.
