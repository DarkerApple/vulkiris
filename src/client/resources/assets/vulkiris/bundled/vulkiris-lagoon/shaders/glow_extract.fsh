#version 330

uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris Lagoon, pass 1 of 3 (half resolution): keep only the bright parts of
// the scene so the next pass can blur them into a soft sun-kissed glow.

void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float bright = smoothstep(0.55, 0.95, luma);
    fragColor = vec4(color * bright, 1.0);
}
