#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

// Drained, cold, and a little too dark to be comfortable. PackA.x = dread.
void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 eerie = mix(vec3(luma), color, 0.25) * vec3(0.88, 0.98, 0.94) * 0.82;
    float pulse = 0.96 + 0.04 * sin(Screen.z * 0.7);
    color = mix(color, eerie * pulse, PackA.x);
    vec2 ec = texCoord - 0.5;
    color *= 1.0 - PackA.x * 0.5 * smoothstep(0.06, 0.5, dot(ec, ec));
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
