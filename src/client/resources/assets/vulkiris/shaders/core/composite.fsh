#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D BloomSampler;

in vec2 texCoord;

out vec4 fragColor;

// Narkowicz ACES fit, normalized so pure white maps back to white on SDR input.
vec3 tonemapAces(vec3 x) {
    vec3 numerator = x * (2.51 * x + 0.03);
    vec3 denominator = x * (2.43 * x + 0.59) + 0.14;
    return clamp((numerator / denominator) * (1.0 / 0.8037), 0.0, 1.0);
}

// Gentle S-curve; blended lightly so it reads as contrast, not a new look.
// The Hermite polynomial is only monotonic on [0,1], so saturate first — bloom
// and exposure can push the input above 1.
vec3 tonemapFilmic(vec3 x) {
    vec3 c = clamp(x, 0.0, 1.0);
    vec3 curved = c * c * (3.0 - 2.0 * c);
    return mix(min(x, vec3(1.0)), curved, 0.4);
}

void main() {
    vec3 color = texture(SceneColorSampler, texCoord).rgb;
    float underwater = Screen.w;
    float rain = Toggles.w;

    // --- Reconstruct view-space position from depth (26.2 uses a reversed depth buffer). ---
    float depth = texture(SceneDepthSampler, texCoord).r;
    bool isSky = depth < 1.0e-6;
    bool depthZeroToOne = BloomParams.w > 0.5;
    vec3 ndc;
    ndc.xy = texCoord * 2.0 - 1.0;
    ndc.z = depthZeroToOne ? depth : depth * 2.0 - 1.0;

    // The view direction through a pixel is independent of depth, so unproject a fixed
    // mid-frustum depth where w is guaranteed non-zero in either NDC convention.
    vec4 dirH = InvProjection * vec4(ndc.xy, depthZeroToOne ? 0.5 : 0.0, 1.0);
    vec3 viewDir = normalize(dirH.xyz / dirH.w);

    vec4 viewH = InvProjection * vec4(ndc, 1.0);
    vec3 viewPos = (isSky || abs(viewH.w) < 1.0e-7) ? viewDir * 4000.0 : viewH.xyz / viewH.w;
    float viewDist = isSky ? 4000.0 : length(viewPos);

    // --- Aerial-perspective fog with sun scattering. ---
    if (Toggles.y > 0.5) {
        float fragmentY = UpDir.w + dot(viewPos, UpDir.xyz);
        float heightFalloff = isSky ? 0.35 : exp(-max(fragmentY - 62.0, 0.0) / 96.0);
        float density = 0.0035 * Fog.w * (0.3 + 0.7 * heightFalloff);
        density *= 1.0 + rain * 0.8;
        density *= 1.0 + underwater * 2.5;
        float fogAmount = 1.0 - exp(-viewDist * density);
        if (isSky) {
            // Only haze the horizon, never the zenith, sun, or stars.
            float horizon = pow(1.0 - clamp(dot(viewDir, UpDir.xyz), 0.0, 1.0), 3.0);
            fogAmount = min(fogAmount, 0.9) * 0.35 * horizon;
        }
        fogAmount = clamp(fogAmount, 0.0, 0.8);

        float sunAmount = pow(max(dot(viewDir, SunDir.xyz), 0.0), 8.0) * SunDir.w * BloomParams.z;
        vec3 sunTint = Fog.rgb * vec3(1.30, 1.07, 0.82);
        vec3 fogTint = mix(Fog.rgb, sunTint, clamp(sunAmount, 0.0, 1.0));
        vec3 underwaterTint = Fog.rgb * vec3(0.75, 0.95, 1.1);
        fogTint = mix(fogTint, underwaterTint, underwater);

        color = mix(color, fogTint, fogAmount);
    }

    // --- Bloom. ---
    color += texture(BloomSampler, texCoord).rgb * (BloomParams.x * Toggles.z);

    // --- Exposure + tonemap in approximately-linear space. ---
    float tonemapMode = Toggles.x;
    vec3 linearColor = color * color * GradeA.x;
    if (tonemapMode > 1.5) {
        linearColor = tonemapFilmic(linearColor);
    } else if (tonemapMode > 0.5) {
        linearColor = tonemapAces(linearColor);
    }
    color = sqrt(clamp(linearColor, 0.0, 1.0));

    // --- Color grade. ---
    float saturation = GradeA.y * (1.0 - rain * 0.15) * (1.0 - underwater * 0.1);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, saturation);
    color = clamp((color - 0.5) * GradeA.z + 0.5, 0.0, 1.0);
    color = mix(color, color * vec3(0.88, 0.97, 1.05), underwater * 0.6);

    // --- Vignette. ---
    vec2 fromCenter = texCoord - 0.5;
    float vignette = 1.0 - GradeA.w * smoothstep(0.25, 0.68, dot(fromCenter, fromCenter));
    color *= vignette;

    fragColor = vec4(color, 1.0);
}
