#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

// Minimal composite for the Lite fast path: fog + scatter, sun/moon halo, tonemap,
// grade, warmth, vignette, and Easter eggs — no bloom, AO, water, rays, or outlines.
// Fewer samplers and far less ALU than the full composite; used when no heavy effect
// is enabled so Potato-class presets get real FPS back.

vec3 tonemapAces(vec3 x) {
    vec3 numerator = x * (2.51 * x + 0.03);
    vec3 denominator = x * (2.43 * x + 0.59) + 0.14;
    return clamp((numerator / denominator) * (1.0 / 0.8037), 0.0, 1.0);
}

vec3 tonemapFilmic(vec3 x) {
    vec3 c = clamp(x, 0.0, 1.0);
    vec3 curved = c * c * (3.0 - 2.0 * c);
    return mix(min(x, vec3(1.0)), curved, 0.4);
}

void main() {
    vec3 color = texture(SceneColorSampler, texCoord).rgb;
    float underwater = Screen.w;
    float rain = CameraPos.w;
    float time = Screen.z;
    float moonCool = Celestial.x;

    float depth = texture(SceneDepthSampler, texCoord).r;
    bool isSky = depth < 1.0e-6;
    bool depthZeroToOne = BloomParams.w > 0.5;
    vec3 ndc;
    ndc.xy = texCoord * 2.0 - 1.0;
    ndc.z = depthZeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 dirH = InvProjection * vec4(ndc.xy, depthZeroToOne ? 0.5 : 0.0, 1.0);
    vec3 viewDir = normalize(dirH.xyz / dirH.w);
    vec4 viewH = InvProjection * vec4(ndc, 1.0);
    vec3 viewPos = (isSky || abs(viewH.w) < 1.0e-7) ? viewDir * 4000.0 : viewH.xyz / viewH.w;
    float viewDist = isSky ? 4000.0 : length(viewPos);

    // --- Fog with celestial scatter. ---
    if (Toggles.y > 0.5) {
        float fragmentY = UpDir.w + dot(viewPos, UpDir.xyz);
        float heightFalloff = isSky ? 0.35 : exp(-max(fragmentY - 62.0, 0.0) / 96.0);
        float density = 0.0035 * Fog.w * (0.3 + 0.7 * heightFalloff);
        density *= 1.0 + rain * 0.8;
        density *= 1.0 + underwater * 2.5;
        float fogAmount = 1.0 - exp(-viewDist * density);
        if (isSky) {
            float horizon = pow(1.0 - clamp(dot(viewDir, UpDir.xyz), 0.0, 1.0), 3.0);
            fogAmount = min(fogAmount, 0.9) * 0.35 * horizon;
        }
        fogAmount = clamp(fogAmount, 0.0, 0.8);

        float sunAmount = pow(max(dot(viewDir, SunDirView.xyz), 0.0), 8.0) * SunDirView.w * BloomParams.z;
        vec3 sunTint = Fog.rgb * mix(vec3(1.30, 1.07, 0.82), vec3(0.85, 0.95, 1.25), moonCool);
        vec3 fogTint = mix(Fog.rgb, sunTint, clamp(sunAmount, 0.0, 1.0));
        fogTint = mix(fogTint, Fog.rgb * vec3(0.75, 0.95, 1.1), underwater);
        color = mix(color, fogTint, fogAmount);
    }

    // --- Sun/moon halo on the sky. ---
    if (isSky && SunDirView.w > 0.01 && underwater < 0.5 && Style.w > 0.001) {
        float sunDot = max(dot(viewDir, SunDirView.xyz), 0.0);
        float lowSun = 1.0 - clamp(SunDirWorld.y * 2.0, 0.0, 1.0);
        vec3 haloColor = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 0.62, 0.30), lowSun);
        haloColor = mix(haloColor, vec3(0.80, 0.88, 1.1), moonCool);
        float halo = pow(sunDot, 900.0) * 1.1 + pow(sunDot, 90.0) * 0.35 + pow(sunDot, 12.0) * 0.10;
        color += haloColor * halo * SunDirView.w * (0.4 + 0.6 * Style.w) * (1.0 - moonCool * 0.45);
    }

    // --- Exposure + tonemap. ---
    float tonemapMode = Toggles.x;
    vec3 linearColor = color * color * GradeA.x;
    vec3 curved = linearColor;
    if (tonemapMode > 1.5) {
        curved = tonemapFilmic(linearColor);
    } else if (tonemapMode > 0.5) {
        curved = tonemapAces(linearColor);
    }
    linearColor = mix(linearColor, curved, Toggles.w);
    color = sqrt(clamp(linearColor, 0.0, 1.0));

    // --- Grade. ---
    float saturation = GradeA.y * (1.0 - rain * 0.15) * (1.0 - underwater * 0.1);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, saturation);
    color = clamp((color - 0.5) * GradeA.z + 0.5, 0.0, 1.0);
    color = mix(color, color * vec3(0.88, 0.97, 1.05), underwater * 0.6);
    float warmth = Extra.x;
    color *= vec3(1.0 + warmth, 1.0 + warmth * 0.2, 1.0 - warmth * 1.2);

    // --- Easter eggs (cheap, kept even on the fast path). ---
    float eggStrength = SunScreen.w;
    if (eggStrength > 0.001) {
        float eggMode = Extra2.w;
        float eggLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (eggMode > 2.5) {
            vec3 eerie = mix(vec3(eggLuma), color, 0.25) * vec3(0.88, 0.98, 0.94) * 0.82;
            float pulse = 0.97 + 0.03 * sin(time * 0.7);
            color = mix(color, eerie * pulse, eggStrength);
            vec2 ec = texCoord - 0.5;
            color *= 1.0 - eggStrength * 0.45 * smoothstep(0.08, 0.55, dot(ec, ec));
        } else if (eggMode > 1.5) {
            vec3 matrixColor = mix(vec3(0.0, 0.07, 0.01), vec3(0.5, 1.0, 0.45), pow(eggLuma, 0.85));
            color = mix(color, matrixColor, eggStrength * 0.8);
        } else if (eggMode > 0.5) {
            vec3 duotone = mix(vec3(0.05, 0.09, 0.26), vec3(0.62, 0.88, 1.05), pow(eggLuma, 1.1));
            float warmAccent = clamp((color.r - max(color.g, color.b)) * 3.0, 0.0, 1.0) * eggLuma;
            vec3 sannabi = mix(duotone, color * vec3(1.25, 0.75, 0.55), warmAccent);
            sannabi = clamp((sannabi - 0.5) * 1.12 + 0.5, 0.0, 1.0);
            color = mix(color, sannabi, eggStrength * 0.85);
            vec2 ec = texCoord - 0.5;
            color *= 1.0 - eggStrength * 0.25 * smoothstep(0.2, 0.62, dot(ec, ec));
        }
    }

    // --- Vignette. ---
    vec2 fromCenter = texCoord - 0.5;
    float vignette = 1.0 - GradeA.w * smoothstep(0.25, 0.68, dot(fromCenter, fromCenter));
    color *= vignette;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
