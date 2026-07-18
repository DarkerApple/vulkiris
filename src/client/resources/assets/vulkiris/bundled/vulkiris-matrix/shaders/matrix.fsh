#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

float codeHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Everything is code. PackA.x = strength, PackA.y = falling code shimmer.
void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 matrixColor = mix(vec3(0.0, 0.07, 0.01), vec3(0.5, 1.0, 0.45), pow(luma, 0.85));

    // Columns of drifting brightness, like rain running down a terminal.
    float column = floor(texCoord.x * Screen.x / 8.0);
    float drip = codeHash(vec2(column, floor(texCoord.y * Screen.y / 12.0 + Screen.z * (2.0 + 3.0 * codeHash(vec2(column, 0.0))))));
    matrixColor *= 0.85 + 0.3 * drip * PackA.y;

    color = mix(color, matrixColor, PackA.x);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
