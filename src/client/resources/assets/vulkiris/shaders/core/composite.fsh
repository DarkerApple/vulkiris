#version 330

#moj_import <vulkiris:params.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D WaterDepthSampler;
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

vec3 viewPosAt(vec2 uv, float depth) {
    vec3 ndc;
    ndc.xy = uv * 2.0 - 1.0;
    ndc.z = BloomParams.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 viewH = InvProjection * vec4(ndc, 1.0);
    return abs(viewH.w) > 1.0e-7 ? viewH.xyz / viewH.w : vec3(0.0);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// Trilinear value noise for the volumetric fog wisps.
float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash3(i), hash3(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash3(i + vec3(0.0, 1.0, 0.0)), hash3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash3(i + vec3(0.0, 0.0, 1.0)), hash3(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash3(i + vec3(0.0, 1.0, 1.0)), hash3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

void main() {
    vec3 color = texture(SceneColorSampler, texCoord).rgb;
    float baseLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float underwater = Screen.w;
    float rain = CameraPos.w;
    float time = Screen.z;

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
    vec3 worldDir = normalize(mat3(InvViewRot) * viewDir);
    // Derivatives must be taken in uniform control flow; hoist them out of all branches.
    vec3 viewPosDdx = dFdx(viewPos);
    vec3 viewPosDdy = dFdy(viewPos);
    vec3 faceNormal = normalize(cross(viewPosDdx, viewPosDdy));
    if (dot(faceNormal, viewDir) > 0.0) {
        faceNormal = -faceNormal;
    }

    // --- Ambient occlusion (computed at half res in the bloom prefilter, blurred for free). ---
    vec4 bloomTap = texture(BloomSampler, texCoord);
    if (!isSky) {
        color *= mix(1.0, clamp(bloomTap.a, 0.0, 1.0), Extra.y * (1.0 - underwater * 0.5));
    }

    // --- Directional sunlight: warm sun-facing surfaces, cool shadow sides. ---
    if (Style.w > 0.001 && !isSky) {
        float lit = clamp(dot(faceNormal, SunDirView.xyz), 0.0, 1.0) * SunDirView.w;
        float shade = (1.0 - clamp(dot(faceNormal, SunDirView.xyz), 0.0, 1.0)) * SunDirView.w;
        float s = Style.w;
        color *= vec3(1.0 + 0.26 * lit * s, 1.0 + 0.15 * lit * s, 1.0 - 0.05 * lit * s);
        color *= vec3(1.0 - 0.09 * shade * s, 1.0 - 0.035 * shade * s, 1.0 + 0.05 * shade * s);
    }

    // --- Depth-normal sun specular on all surfaces ("PBR-ish" gloss). ---
    if (Extra2.x > 0.001 && !isSky) {
        float spec = pow(max(dot(reflect(viewDir, faceNormal), SunDirView.xyz), 0.0), 24.0) * SunDirView.w;
        color += vec3(1.0, 0.93, 0.8) * spec * Extra2.x * 0.35 * (0.35 + 0.65 * baseLuma);
    }

    // --- Block-outline edge factor, shared by rim lighting and the toon outline. ---
    // Two detectors over the same four depth taps: a silhouette test (background well
    // behind this surface) and a view-distance Laplacian, which stays ~0 across flat
    // faces but spikes at block edges, corners, and creases — the per-block outline.
    float silhouette = 0.0;
    if ((Style.y > 0.001 || Style.z > 0.5) && !isSky) {
        vec2 texel = 2.0 / Screen.xy;
        float distLeft;
        float distRight;
        float distUp;
        float distDown;
        {
            float d = textureLod(SceneDepthSampler, clamp(texCoord + vec2(-texel.x, 0.0), vec2(0.0), vec2(1.0)), 0.0).r;
            distLeft = d < 1.0e-6 ? 4000.0 : length(viewPosAt(texCoord + vec2(-texel.x, 0.0), d));
            d = textureLod(SceneDepthSampler, clamp(texCoord + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0)), 0.0).r;
            distRight = d < 1.0e-6 ? 4000.0 : length(viewPosAt(texCoord + vec2(texel.x, 0.0), d));
            d = textureLod(SceneDepthSampler, clamp(texCoord + vec2(0.0, -texel.y), vec2(0.0), vec2(1.0)), 0.0).r;
            distUp = d < 1.0e-6 ? 4000.0 : length(viewPosAt(texCoord + vec2(0.0, -texel.y), d));
            d = textureLod(SceneDepthSampler, clamp(texCoord + vec2(0.0, texel.y), vec2(0.0), vec2(1.0)), 0.0).r;
            distDown = d < 1.0e-6 ? 4000.0 : length(viewPosAt(texCoord + vec2(0.0, texel.y), d));
        }

        float behind = max(max(distLeft, distRight), max(distUp, distDown)) - viewDist;
        float bigSilhouette = smoothstep(0.6, 3.5, behind);

        float crease = max(
                abs(distLeft + distRight - 2.0 * viewDist),
                abs(distUp + distDown - 2.0 * viewDist));
        float creaseThreshold = 0.02 + 0.014 * viewDist;
        float creaseEdge = smoothstep(creaseThreshold, creaseThreshold * 2.5, crease);

        // Fade block outlines out with distance so far terrain stays clean.
        float nearFade = smoothstep(150.0, 60.0, viewDist);
        silhouette = clamp(max(bigSilhouette, creaseEdge * nearFade), 0.0, 1.0);
    }

    // --- Shine-style rim lighting: crisp bright outlines around blocks. ---
    if (Style.y > 0.001 && silhouette > 0.001) {
        vec3 rimColor = mix(Fog.rgb * 1.3, vec3(1.0, 0.95, 0.8), SunDirView.w * 0.6);
        color += rimColor * silhouette * Style.y * (0.35 + 0.25 * baseLuma);
    }

    // --- Water surface shading: depth absorption, animated sun glint, fresnel/SSR reflections. ---
    if (Extra.z > 0.5 && !isSky) {
        float waterDepth = textureLod(WaterDepthSampler, texCoord, 0.0).r;
        // Reversed depth: larger value = closer. A translucent surface drawn after the
        // capture makes the final depth strictly closer than the captured one.
        float delta = depth - waterDepth;
        if (delta > 1.0e-7) {
            vec3 behindPos = viewPosAt(texCoord, waterDepth);
            float thickness = clamp(length(behindPos) - viewDist, 0.0, 64.0);

            // Only treat upward-facing translucent surfaces as water (skips glass panes).
            float upFacing = smoothstep(0.55, 0.8, dot(faceNormal, UpDir.xyz));
            if (upFacing > 0.0 && thickness > 0.05) {
                vec3 worldPos = CameraPos.xyz + mat3(InvViewRot) * viewPos;

                // Procedural wave normal in world space; higher quality tiers add detail octaves.
                vec2 wave = vec2(
                    sin(worldPos.x * 0.9 + time * 1.7) + 0.5 * sin(worldPos.x * 2.3 - time * 2.6 + worldPos.z * 0.8),
                    sin(worldPos.z * 1.1 + time * 1.4) + 0.5 * sin(worldPos.z * 2.7 + time * 2.2 + worldPos.x * 0.6));
                if (Quality.x > 12.0) {
                    wave += 0.3 * vec2(
                        sin(worldPos.x * 5.1 + worldPos.z * 2.9 + time * 3.4),
                        sin(worldPos.z * 5.7 - worldPos.x * 2.3 + time * 3.1));
                    wave += 0.15 * vec2(
                        sin(worldPos.x * 11.0 - worldPos.z * 6.5 - time * 4.4),
                        sin(worldPos.z * 12.3 + worldPos.x * 7.1 + time * 4.9));
                }
                vec3 waveWorld = normalize(vec3(wave.x * 0.06, 1.0, wave.y * 0.06));
                mat3 viewRot = transpose(mat3(InvViewRot));
                vec3 waterNormal = normalize(mix(faceNormal, viewRot * waveWorld, 0.75 * upFacing));

                // Absorption: the more water in front of the scene, the deeper the tint.
                float absorb = 1.0 - exp(-thickness * 0.09);
                color = mix(color, color * vec3(0.55, 0.78, 0.88), absorb * 0.65 * upFacing);

                // Reflection color: sky/fog fallback, upgraded by an SSR march when enabled.
                vec3 reflectColor = Fog.rgb * 1.06;
                float reflectStrength = 0.5;
                vec3 reflected = reflect(viewDir, waterNormal);
                int ssrSteps = int(Extra2.z + 0.5);
                if (ssrSteps > 0 && reflected.z < -0.02) {
                    // Finer steps at higher counts; exponential growth keeps the reach similar.
                    vec3 rayPos = viewPos + waterNormal * 0.1;
                    float stepSize = ssrSteps >= 24 ? 0.3 : 0.5;
                    float growth = ssrSteps >= 48 ? 1.10 : ssrSteps >= 24 ? 1.16 : 1.22;
                    float lastStep = stepSize;
                    for (int i = 0; i < 48; i++) {
                        if (i >= ssrSteps) {
                            break;
                        }
                        rayPos += reflected * stepSize;
                        lastStep = stepSize;
                        stepSize *= growth;
                        vec4 clip = Projection * vec4(rayPos, 1.0);
                        if (clip.w < 1.0e-4) {
                            break;
                        }
                        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
                        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                            break;
                        }
                        float sampleDepth = textureLod(SceneDepthSampler, uv, 0.0).r;
                        if (sampleDepth < 1.0e-6) {
                            continue;
                        }
                        vec3 samplePos = viewPosAt(uv, sampleDepth);
                        float dz = samplePos.z - rayPos.z;
                        if (dz > 0.05 && dz < lastStep * 4.0) {
                            vec2 hitUv = uv;
                            if (ssrSteps >= 24) {
                                // Binary-search refinement for crisper contact points.
                                vec3 lo = rayPos - reflected * lastStep;
                                vec3 hi = rayPos;
                                for (int r = 0; r < 4; r++) {
                                    vec3 mid = (lo + hi) * 0.5;
                                    vec4 midClip = Projection * vec4(mid, 1.0);
                                    vec2 midUv = midClip.xy / max(midClip.w, 1.0e-4) * 0.5 + 0.5;
                                    float midDepth = textureLod(SceneDepthSampler, clamp(midUv, vec2(0.0), vec2(1.0)), 0.0).r;
                                    vec3 midSample = viewPosAt(midUv, midDepth);
                                    if (midDepth > 1.0e-6 && midSample.z - mid.z > 0.05) {
                                        hi = mid;
                                        hitUv = midUv;
                                    } else {
                                        lo = mid;
                                    }
                                }
                            }
                            reflectColor = textureLod(SceneColorSampler, clamp(hitUv, vec2(0.0), vec2(1.0)), 0.0).rgb;
                            reflectStrength = 0.7;
                            break;
                        }
                    }
                }

                // Fresnel: grazing angles reflect more.
                float fresnel = pow(1.0 - clamp(dot(-viewDir, waterNormal), 0.0, 1.0), 5.0);
                color = mix(color, reflectColor, fresnel * reflectStrength * upFacing);

                // Sun glint sparkle from the animated normal.
                float glint = pow(max(dot(reflected, SunDirView.xyz), 0.0), 220.0) * SunDirView.w;
                color += vec3(1.0, 0.9, 0.7) * glint * 1.6 * upFacing;
            }
        }
    }

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

        float sunAmount = pow(max(dot(viewDir, SunDirView.xyz), 0.0), 8.0) * SunDirView.w * BloomParams.z;
        vec3 sunTint = Fog.rgb * vec3(1.30, 1.07, 0.82);
        vec3 fogTint = mix(Fog.rgb, sunTint, clamp(sunAmount, 0.0, 1.0));
        vec3 underwaterTint = Fog.rgb * vec3(0.75, 0.95, 1.1);
        fogTint = mix(fogTint, underwaterTint, underwater);

        // Volumetric fog below replaces part of this; thin it out so they don't double up.
        if (Quality.z > 0.5) {
            fogAmount *= 0.6;
        }
        color = mix(color, fogTint, fogAmount);
    }

    // --- Volumetric noise fog: animated ground mist with sun in-scattering (heavy tiers). ---
    if (Quality.z > 0.5 && Toggles.y > 0.5 && underwater < 0.5 && !isSky) {
        int fogSteps = int(Quality.z + 0.5);
        float maxDist = min(viewDist, 96.0);
        float stepLen = maxDist / float(fogSteps);
        vec3 worldStep = normalize(mat3(InvViewRot) * viewDir) * stepLen;
        vec3 samplePoint = CameraPos.xyz + worldStep * (0.3 + 0.7 * hash2(gl_FragCoord.xy));
        float accum = 0.0;
        for (int i = 0; i < 20; i++) {
            if (i >= fogSteps) {
                break;
            }
            float heightFade = exp(-max(samplePoint.y - 62.0, 0.0) / 40.0);
            float wisp = valueNoise(samplePoint * 0.045 + vec3(time * 0.06, time * 0.015, time * 0.04));
            accum += heightFade * (0.35 + 1.1 * wisp * wisp);
            samplePoint += worldStep;
        }
        float volFog = 1.0 - exp(-accum * stepLen * 0.010 * Fog.w * (1.0 + rain));
        volFog = clamp(volFog, 0.0, 0.55);
        float sunPhase = pow(max(dot(viewDir, SunDirView.xyz), 0.0), 4.0) * SunDirView.w;
        vec3 volColor = mix(Fog.rgb, Fog.rgb * vec3(1.35, 1.08, 0.8), sunPhase);
        color = mix(color, volColor, volFog * 0.8);
    }

    // --- Screen-space god rays: march toward the sun accumulating sky visibility. ---
    if (Extra.w > 0.001 && SunScreen.z > 0.5 && SunDirView.w > 0.01 && underwater < 0.5) {
        int rayTaps = int(Quality.y + 0.5);
        vec2 rayStep = (SunScreen.xy - texCoord) / float(rayTaps);
        // Cap the march length so shafts stay soft on ultrawide angles.
        float stepLength = length(rayStep);
        float maxStep = 0.85 / float(rayTaps);
        if (stepLength > maxStep) {
            rayStep *= maxStep / stepLength;
        }
        float illumination = 0.0;
        float decay = 1.0;
        // Jittered start hides banding at low tap counts.
        vec2 uv = texCoord + rayStep * hash2(gl_FragCoord.xy);
        for (int i = 0; i < 48; i++) {
            if (i >= rayTaps) {
                break;
            }
            uv += rayStep;
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                break;
            }
            float d = textureLod(SceneDepthSampler, uv, 0.0).r;
            illumination += (d < 1.0e-6 ? 1.0 : 0.0) * decay;
            decay *= 1.0 - 2.0 / float(rayTaps);
        }
        illumination /= float(rayTaps) * 0.5;
        float toSun = max(dot(viewDir, SunDirView.xyz), 0.0);
        vec3 shaftColor = Fog.rgb * vec3(1.35, 1.05, 0.75);
        color += shaftColor * clamp(illumination, 0.0, 1.0) * pow(toSun, 3.0) * Extra.w * SunDirView.w * 0.5;
    }

    // --- Layered sun halo on the sky: tight glow, corona, and wide atmosphere. ---
    if (isSky && SunDirView.w > 0.01 && underwater < 0.5 && Style.w > 0.001) {
        float sunDot = max(dot(viewDir, SunDirView.xyz), 0.0);
        float lowSun = 1.0 - clamp(SunDirWorld.y * 2.0, 0.0, 1.0);
        vec3 haloColor = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 0.62, 0.30), lowSun);
        float halo = pow(sunDot, 900.0) * 1.1 + pow(sunDot, 90.0) * 0.35 + pow(sunDot, 12.0) * 0.10;
        color += haloColor * halo * SunDirView.w * (0.4 + 0.6 * Style.w);
    }

    // --- Sky, sunset, and cloud grading (clouds write depth, so far pixels catch it too). ---
    float skyIntensity = SunDirWorld.w;
    if (skyIntensity > 0.001 && underwater < 0.5) {
        float farBlend = isSky ? 1.0 : smoothstep(250.0, 600.0, viewDist);
        if (farBlend > 0.001) {
            float sunElevation = SunDirWorld.y;
            // Strongest right at sunrise/sunset, fading as the sun climbs or fully sets.
            float dusk = (1.0 - smoothstep(0.10, 0.42, abs(sunElevation))) * (1.0 - rain * 0.7);

            vec2 flatDir = normalize(worldDir.xz + vec2(1.0e-5));
            vec2 flatSun = normalize(SunDirWorld.xz + vec2(1.0e-5));
            float toSun = dot(flatDir, flatSun) * 0.5 + 0.5;
            float horizonBand = 1.0 - clamp(abs(worldDir.y), 0.0, 1.0);
            horizonBand *= horizonBand;

            // Warm gradient toward the sun, cool violet away from it.
            vec3 sunsetGlow = vec3(1.00, 0.42, 0.16) * pow(toSun, 3.0);
            vec3 antiGlow = vec3(0.28, 0.22, 0.42) * pow(1.0 - toSun, 2.0) * 0.5;
            color += (sunsetGlow + antiGlow) * (dusk * horizonBand * skyIntensity * 0.55 * farBlend);

            // Deeper blue zenith on clear days.
            float day = smoothstep(0.15, 0.45, sunElevation) * (1.0 - rain);
            float zenith = clamp(worldDir.y, 0.0, 1.0);
            color = mix(color, color * vec3(0.82, 0.91, 1.10), day * zenith * skyIntensity * 0.35 * farBlend);

            // Cool, slightly darker nights.
            float night = smoothstep(0.08, 0.25, -sunElevation);
            color = mix(color, color * vec3(0.82, 0.88, 1.05), night * skyIntensity * 0.30 * farBlend);
        }
    }

    // --- Bloom, plus colored light bleed into nearby dark areas. ---
    color += bloomTap.rgb * (BloomParams.x * Toggles.z);
    color += bloomTap.rgb * (Extra2.y * clamp(1.0 - baseLuma, 0.0, 1.0));

    // --- Exposure + tonemap in approximately-linear space. ---
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

    // --- Toon shading: quantized lighting bands plus dark silhouette outlines. ---
    if (Style.z > 0.5) {
        float toonLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float bands = 5.0;
        float quantized = (floor(toonLuma * bands) + 0.5) / bands;
        color *= mix(1.0, quantized / max(toonLuma, 1.0e-4), 0.7);
        color *= 1.0 - silhouette * 0.55;
    }

    // --- Color grade. ---
    float saturation = GradeA.y * (1.0 - rain * 0.15) * (1.0 - underwater * 0.1);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, saturation);
    color = clamp((color - 0.5) * GradeA.z + 0.5, 0.0, 1.0);
    color = mix(color, color * vec3(0.88, 0.97, 1.05), underwater * 0.6);

    // Warm white balance.
    float warmth = Extra.x;
    color *= vec3(1.0 + warmth, 1.0 + warmth * 0.2, 1.0 - warmth * 1.2);

    // --- Easter-egg looks (chat-triggered, cross-faded). ---
    float eggStrength = SunScreen.w;
    if (eggStrength > 0.001) {
        float eggMode = Extra2.w;
        float eggLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (eggMode > 2.5) {
            // HEROBRINE: drained, cold, and a little too dark to be comfortable.
            vec3 eerie = mix(vec3(eggLuma), color, 0.25) * vec3(0.88, 0.98, 0.94) * 0.82;
            float pulse = 0.97 + 0.03 * sin(time * 0.7);
            color = mix(color, eerie * pulse, eggStrength);
            vec2 ec = texCoord - 0.5;
            color *= 1.0 - eggStrength * 0.45 * smoothstep(0.08, 0.55, dot(ec, ec));
        } else if (eggMode > 1.5) {
            // MATRIX: everything is code.
            vec3 rampDark = vec3(0.0, 0.07, 0.01);
            vec3 rampBright = vec3(0.5, 1.0, 0.45);
            vec3 matrixColor = mix(rampDark, rampBright, pow(eggLuma, 0.85));
            color = mix(color, matrixColor, eggStrength * 0.8);
        } else if (eggMode > 0.5) {
            // SANNABI: neon-noir night city — deep navy shadows, electric cyan
            // highlights, and warm signage accents surviving the duotone.
            vec3 duoShadow = vec3(0.05, 0.09, 0.26);
            vec3 duoHighlight = vec3(0.62, 0.88, 1.05);
            vec3 duotone = mix(duoShadow, duoHighlight, pow(eggLuma, 1.1));
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

    // --- Film grain (Real-Life mode and custom configs). ---
    if (Quality.w > 0.001) {
        float grain = hash2(gl_FragCoord.xy + vec2(fract(time) * 251.0, fract(time * 0.61) * 379.0)) - 0.5;
        color += grain * Quality.w * (0.4 + 0.6 * (1.0 - dot(color, vec3(0.333))));
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
