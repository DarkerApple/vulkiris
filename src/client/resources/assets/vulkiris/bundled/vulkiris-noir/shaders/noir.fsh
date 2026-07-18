#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;

out vec4 fragColor;

// Vulkiris Noir: hard-boiled cinema — deep blacks, silver highlights, letterbox bars,
// and a whisper of grain. PackA.x = monochrome amount, PackA.y = letterbox height.

float grainHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    // Letterbox bars.
    float bar = PackA.y;
    if (texCoord.y < bar || texCoord.y > 1.0 - bar) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 color = texture(PreviousSampler, texCoord).rgb;
    float mono = PackA.x;
    float time = Screen.z;

    // Silver-process monochrome with a crushed-shadow S-curve.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float curved = smoothstep(0.06, 0.94, luma);
    curved = curved * curved * (3.0 - 2.0 * curved);
    vec3 silver = vec3(curved) * vec3(0.98, 1.0, 1.04);
    color = mix(color, silver, mono);

    // Moody vignette + a whisper of animated grain.
    vec2 fromCenter = texCoord - 0.5;
    color *= 1.0 - 0.4 * smoothstep(0.15, 0.62, dot(fromCenter, fromCenter));
    color += (grainHash(gl_FragCoord.xy + vec2(fract(time) * 289.0, fract(time * 0.71) * 431.0)) - 0.5) * 0.05;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
