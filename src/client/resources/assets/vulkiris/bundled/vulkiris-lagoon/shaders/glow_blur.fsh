#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris Lagoon, pass 2 of 3 (half resolution): 3x3 tent blur over the bright
// extract. Constant loop bounds keep the texture taps in uniform control flow.

void main() {
    // This pass renders at scale 0.5, so one texel is two screen pixels wide.
    vec2 texel = 2.0 / Screen.xy;
    vec3 sum = vec3(0.0);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            float weight = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0);
            vec2 uv = clamp(texCoord + vec2(x, y) * texel * 1.5, vec2(0.0), vec2(1.0));
            sum += texture(PreviousSampler, uv).rgb * weight;
        }
    }
    fragColor = vec4(sum / 16.0, 1.0);
}
