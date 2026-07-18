#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris Aurora: a teal-violet fantasy grade with slow aurora curtains dancing
// across the sky. PackA.x = overall intensity, PackA.y = aurora band strength.

void main() {
    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float intensity = PackA.x;
    float bands = PackA.y;
    float time = Screen.z;

    // Dreamy grade: cool shadows, teal mids, violet highlights.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 shadowTint = vec3(0.75, 0.85, 1.15);
    vec3 midTint = vec3(0.85, 1.08, 1.05);
    vec3 highTint = vec3(1.05, 0.92, 1.12);
    vec3 tint = mix(shadowTint, mix(midTint, highTint, smoothstep(0.5, 1.0, luma)), smoothstep(0.0, 0.5, luma));
    color = mix(color, color * tint, intensity * 0.8);

    // Aurora curtains on the sky (depth = 0 under 26.2's reversed depth buffer).
    float depth = texture(SceneDepthSampler, texCoord).r;
    if (depth < 1.0e-6 && bands > 0.001) {
        bool zeroToOne = BloomParams.w > 0.5;
        vec4 dirH = InvProjection * vec4(texCoord * 2.0 - 1.0, zeroToOne ? 0.5 : 0.0, 1.0);
        vec3 worldDir = normalize(mat3(InvViewRot) * normalize(dirH.xyz / dirH.w));
        float elevation = clamp(worldDir.y, 0.0, 1.0);
        if (elevation > 0.02) {
            // Two drifting curtain layers shaped by azimuth and elevation.
            float az = atan(worldDir.z, worldDir.x);
            float curtain = sin(az * 3.0 + time * 0.12 + sin(az * 7.0 + time * 0.07) * 0.8)
                    * sin(elevation * 9.0 - time * 0.18);
            curtain = pow(clamp(curtain * 0.5 + 0.5, 0.0, 1.0), 3.0);
            float heightFade = smoothstep(0.03, 0.25, elevation) * (1.0 - smoothstep(0.65, 0.95, elevation));
            // Stronger at night, faint by day.
            float nightBoost = mix(0.25, 1.0, Celestial.x);
            vec3 auroraColor = mix(vec3(0.15, 0.95, 0.55), vec3(0.55, 0.25, 0.95),
                    0.5 + 0.5 * sin(az * 2.0 - time * 0.1));
            color += auroraColor * curtain * heightFade * bands * nightBoost * 0.5;
        }
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
