#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// A little CRT monitor look: barrel distortion, scanlines, RGB stripes, and flicker.
void main() {
    // Barrel-distort the UVs around the center.
    vec2 centered = texCoord * 2.0 - 1.0;
    float r2 = dot(centered, centered);
    vec2 uv = (centered * (1.0 + 0.08 * r2)) * 0.5 + 0.5;

    // Outside the curved tube is black.
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 color = texture(PreviousSampler, uv).rgb;

    // Scanlines rolling slowly upward (Screen.z is world time in seconds).
    float scan = 0.85 + 0.15 * sin(uv.y * Screen.y * 3.1416 + Screen.z * 2.0);
    color *= scan;

    // Vertical RGB phosphor stripes.
    float stripe = mod(floor(uv.x * Screen.x), 3.0);
    vec3 mask = stripe < 1.0 ? vec3(1.05, 0.9, 0.9)
            : stripe < 2.0 ? vec3(0.9, 1.05, 0.9)
            : vec3(0.9, 0.9, 1.05);
    color *= mask;

    // Soft tube vignette and a hint of flicker.
    color *= 1.0 - 0.35 * r2;
    color *= 0.98 + 0.02 * sin(Screen.z * 60.0);

    fragColor = vec4(color, 1.0);
}
