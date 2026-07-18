#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

// Neon-noir night city: navy shadows, electric cyan highlights, warm accents
// surviving the duotone. PackA.x = strength, PackA.y = warm accent amount.
void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 duotone = mix(vec3(0.05, 0.09, 0.26), vec3(0.62, 0.88, 1.05), pow(luma, 1.1));
    float warmMask = clamp((color.r - max(color.g, color.b)) * 3.0, 0.0, 1.0) * luma * PackA.y;
    vec3 graded = mix(duotone, color * vec3(1.25, 0.75, 0.55), warmMask);
    graded = clamp((graded - 0.5) * 1.12 + 0.5, 0.0, 1.0);
    color = mix(color, graded, PackA.x);
    vec2 ec = texCoord - 0.5;
    color *= 1.0 - PackA.x * 0.25 * smoothstep(0.2, 0.62, dot(ec, ec));
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
