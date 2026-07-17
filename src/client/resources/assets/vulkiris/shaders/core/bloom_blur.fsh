#version 330

uniform sampler2D SceneColorSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
#ifdef VULKIRIS_HORIZONTAL
    vec2 direction = vec2(texel.x, 0.0);
#else
    vec2 direction = vec2(0.0, texel.y);
#endif

    // 9-tap separable Gaussian at half resolution, slightly widened for a softer halo.
    float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 color = texture(SceneColorSampler, texCoord).rgb * weights[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = direction * (float(i) * 1.5);
        color += texture(SceneColorSampler, texCoord + offset).rgb * weights[i];
        color += texture(SceneColorSampler, texCoord - offset).rgb * weights[i];
    }

    fragColor = vec4(color, 1.0);
}
