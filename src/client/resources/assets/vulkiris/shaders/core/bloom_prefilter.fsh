#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D SceneColorSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // 4 bilinear taps = 4x4 box downsample into the half-resolution bloom target.
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
    vec3 color = vec3(0.0);
    color += texture(SceneColorSampler, texCoord + texel * vec2(-0.5, -0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(0.5, -0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(-0.5, 0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(0.5, 0.5)).rgb;
    color *= 0.25;

    // Soft-knee threshold keeps only highlights, without a hard cutoff edge.
    float threshold = BloomParams.y;
    float knee = 0.15;
    float brightness = max(color.r, max(color.g, color.b));
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1.0e-4);
    float weight = max(soft, brightness - threshold) / max(brightness, 1.0e-4);

    fragColor = vec4(color * clamp(weight, 0.0, 1.0), 1.0);
}
