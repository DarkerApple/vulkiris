#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris Lagoon, pass 3 of 3 (full resolution): tropical postcard grade with
// turquoise water tint, animated caustics on submerged surfaces, an underwater
// view wobble, and the soft glow blurred by the half-res passes.
// PackA.x = glow, PackA.y = warmth, PackA.z = caustics, PackA.w = water tint.

// Two interfering wave sets — cheap, loop-free caustic shimmer.
float causticPattern(vec2 p, float t) {
    float a = sin(p.x * 1.7 + t * 0.8) + sin(p.y * 2.1 - t * 0.6);
    float b = sin(p.x * 0.9 - p.y * 1.3 + t * 1.1);
    float v = 0.5 + 0.5 * sin(a * 1.5 + b + t * 0.4);
    return v * v * v;
}

void main() {
    float glowAmt = PackA.x;
    float warmth = PackA.y;
    float causticAmt = PackA.z;
    float tintAmt = PackA.w;
    float time = Screen.z;
    float underwater = Screen.w;

    // Gentle view wobble while the camera is submerged. The offset is scaled by
    // the underwater flag instead of branched, so every tap stays in uniform
    // control flow.
    vec2 wobble = vec2(sin(texCoord.y * 36.0 + time * 1.6), cos(texCoord.x * 32.0 - time * 1.3));
    vec2 uv = clamp(texCoord + wobble * (0.0035 * underwater), vec2(0.0), vec2(1.0));

    vec3 color = texture(SceneColorSampler, uv).rgb;
    vec3 glow = texture(PreviousSampler, uv).rgb;
    float depth = texture(SceneDepthSampler, uv).r;
    float waterDepth = texture(WaterDepthSampler, uv).r;

    // 26.2 uses reversed depth: the sky sits at ~0, near geometry at ~1.
    float ground = depth < 1.0e-6 ? 0.0 : 1.0;

    // Reconstruct the world position of this pixel; BloomParams.w tells us which
    // NDC depth convention the active backend uses.
    vec3 ndc;
    ndc.xy = uv * 2.0 - 1.0;
    ndc.z = BloomParams.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 viewH = InvProjection * vec4(ndc, 1.0);
    vec3 viewPos = abs(viewH.w) > 1.0e-7 ? viewH.xyz / viewH.w : vec3(0.0);
    vec3 worldPos = mat3(InvViewRot) * viewPos + CameraPos.xyz;
    float viewDist = length(viewPos);

    // The water snapshot was taken before translucents drew, so wherever the live
    // depth is strictly closer (larger, reversed) there is water in front.
    float behindWater = (depth - waterDepth) > 1.0e-7 ? 1.0 : 0.0;
    float wet = max(behindWater, underwater);

    // Dancing caustics on wet ground, fading with distance and daylight. The moon
    // still shimmers, just dimmer.
    float daylight = SunDirView.w * (1.0 - 0.6 * Celestial.x);
    float caustic = causticPattern(worldPos.xz * 0.55, time);
    float distFade = 1.0 - smoothstep(24.0, 96.0, viewDist);
    float shimmer = caustic * wet * ground * distFade * causticAmt;
    color += vec3(0.35, 0.75, 0.8) * shimmer * (0.15 + 0.45 * daylight);

    // Turquoise tint for everything seen through (or from inside) the water.
    vec3 lagoonColor = vec3(0.16, 0.62, 0.66);
    float brightness = dot(color, vec3(1.0 / 3.0));
    color = mix(color, lagoonColor * (0.35 + 0.65 * brightness), 0.45 * tintAmt * wet);

    // Soft glow from the half-res blur chain.
    color += glow * glowAmt * 0.6;

    // Tropical postcard grade: golden highlights, gentle teal shadows — cooled
    // toward silver when the moon is the active light.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 warmHigh = mix(vec3(1.08, 1.0, 0.88), vec3(0.94, 0.98, 1.1), Celestial.x);
    vec3 coolShadow = vec3(0.92, 1.0, 1.06);
    vec3 gradeTint = mix(coolShadow, warmHigh, smoothstep(0.15, 0.85, luma));
    color = mix(color, color * gradeTint, warmth);

    // A touch of extra saturation so the lagoon pops.
    color = mix(vec3(luma), color, 1.0 + 0.15 * warmth);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
