#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

vec3 viewPosAt(vec2 uv, float depth) {
    vec3 ndc;
    ndc.xy = uv * 2.0 - 1.0;
    ndc.z = BloomParams.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 viewH = InvProjection * vec4(ndc, 1.0);
    return abs(viewH.w) > 1.0e-7 ? viewH.xyz / viewH.w : vec3(0.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Cheap 8-tap SSAO on the depth buffer, computed at half resolution.
// The separable Gaussian that blurs the bloom also denoises this for free.
float ambientOcclusion(vec3 center, vec3 centerDdx, vec3 centerDdy) {
    float dist = length(center);
    vec3 normal = normalize(cross(centerDdx, centerDdy));
    if (dot(normal, normalize(center)) > 0.0) {
        normal = -normal;
    }

    float radiusWorld = 0.7;
    // Project the world-space radius into UV space: InvProjection[1][1] is tan(fov/2).
    float radiusUv = radiusWorld / (2.0 * max(dist, 0.5) * InvProjection[1][1]);
    radiusUv = clamp(radiusUv, 0.001, 0.05);

    float angle = hash(gl_FragCoord.xy) * 6.2831853;
    float c = cos(angle);
    float s = sin(angle);
    int taps = int(Quality.x + 0.5);
    float occlusion = 0.0;
    for (int i = 0; i < 24; i++) {
        if (i >= taps) {
            break;
        }
        float t = (float(i) + 0.5) / float(taps);
        float a = t * 6.2831853 * 2.0;
        vec2 offset = vec2(cos(a) * c - sin(a) * s, cos(a) * s + sin(a) * c) * (radiusUv * t);
        vec2 uv = clamp(texCoord + offset, vec2(0.001), vec2(0.999));
        float sampleDepth = textureLod(SceneDepthSampler, uv, 0.0).r;
        if (sampleDepth < 1.0e-6) {
            continue;
        }
        vec3 samplePos = viewPosAt(uv, sampleDepth);
        vec3 v = samplePos - center;
        float len = max(length(v), 1.0e-4);
        float rangeCheck = smoothstep(0.0, 1.0, radiusWorld / len);
        occlusion += max(dot(normal, v / len) - 0.08, 0.0) * rangeCheck;
    }

    // Normalized so the same strength setting reads equally at every tap count.
    return clamp(1.0 - occlusion * (2.24 / float(taps)), 0.0, 1.0);
}

void main() {
    // 4 bilinear taps = 4x4 box downsample into the half-resolution bloom target.
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
    vec3 color = vec3(0.0);
    color += texture(SceneColorSampler, texCoord + texel * vec2(-0.5, -0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(0.5, -0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(-0.5, 0.5)).rgb;
    color += texture(SceneColorSampler, texCoord + texel * vec2(0.5, 0.5)).rgb;
    color *= 0.25;

    // Soft-knee threshold keeps only highlights, without a hard cutoff edge.
    float threshold = BloomParams.y;
    float knee = 0.15;
    float brightness = max(color.r, max(color.g, color.b));
    float soft = clamp(brightness - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1.0e-4);
    float weight = max(soft, brightness - threshold) / max(brightness, 1.0e-4);

    // Derivatives must be taken in uniform control flow, so compute them unconditionally.
    float centerDepth = texture(SceneDepthSampler, texCoord).r;
    vec3 centerPos = viewPosAt(texCoord, centerDepth);
    vec3 centerDdx = dFdx(centerPos);
    vec3 centerDdy = dFdy(centerPos);
    float ao = 1.0;
    if (Extra.y > 0.001 && centerDepth >= 1.0e-6) {
        ao = ambientOcclusion(centerPos, centerDdx, centerDdy);
    }

    fragColor = vec4(color * clamp(weight, 0.0, 1.0), ao);
}
