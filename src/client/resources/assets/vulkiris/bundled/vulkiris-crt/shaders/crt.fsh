#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris CRT: barrel distortion, rolling scanlines, RGB phosphor stripes, and
// tube flicker. PackA.x = curvature, PackA.y = scanline strength.

void main() {
    float curvature = PackA.x;
    float scanStrength = PackA.y;
    float time = Screen.z;

    // Barrel-distort the UVs around the center.
    vec2 centered = texCoord * 2.0 - 1.0;
    float r2 = dot(centered, centered);
    vec2 uv = (centered * (1.0 + curvature * r2)) * 0.5 + 0.5;

    // Outside the curved tube is black.
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 color = texture(PreviousSampler, uv).rgb;

    // Scanlines rolling slowly upward.
    float scan = 1.0 - scanStrength + scanStrength * sin(uv.y * Screen.y * 3.1416 + time * 2.0);
    color *= scan;

    // Vertical RGB phosphor stripes.
    float stripe = mod(floor(uv.x * Screen.x), 3.0);
    vec3 mask = stripe < 1.0 ? vec3(1.05, 0.9, 0.9)
            : stripe < 2.0 ? vec3(0.9, 1.05, 0.9)
            : vec3(0.9, 0.9, 1.05);
    color *= mask;

    // Soft tube vignette and a hint of flicker.
    color *= 1.0 - 0.35 * r2 * (0.4 + 3.0 * curvature);
    color *= 0.98 + 0.02 * sin(time * 60.0);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
