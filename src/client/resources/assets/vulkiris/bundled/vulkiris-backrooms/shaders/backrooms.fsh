#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

float staticHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Found-footage VCR tape in the place you noclipped into. Only your flashlight is
// fully bright. PackA.x = tape wear, PackA.y = flashlight, PackA.z = static.
void main() {
    float time = Screen.z;
    vec2 uv = texCoord;

    // Tape wear: slight horizontal wobble on scan bands.
    float wobble = (staticHash(vec2(floor(uv.y * 240.0), floor(time * 12.0))) - 0.5) * 0.004 * PackA.x;
    uv.x = clamp(uv.x + wobble, 0.0, 1.0);

    vec3 color = texture(PreviousSampler, uv).rgb;

    // Drained mono-yellow fluorescent grade.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 vcr = mix(color, mix(vec3(luma), color, 0.4) * vec3(1.1, 1.04, 0.72), PackA.x);

    // Scanlines + interlace flicker.
    vcr *= 1.0 - PackA.x * 0.14 * (0.5 + 0.5 * sin(uv.y * Screen.y * 3.1416));
    vcr *= 1.0 - PackA.x * 0.03 * (0.5 + 0.5 * sin(time * 24.0));

    // Static: fine animated snow plus occasional horizontal dropout lines.
    float snow = staticHash(gl_FragCoord.xy + vec2(fract(time) * 511.0, fract(time * 0.37) * 293.0));
    vcr += (snow - 0.5) * 0.2 * PackA.z;
    float dropout = step(0.988, staticHash(vec2(floor(uv.y * 90.0), floor(time * 7.0))));
    vcr = mix(vcr, vec3(snow), dropout * 0.55 * PackA.z);

    // Flashlight: only the beam is fully bright; the rest of the room goes dim.
    vec2 ec = (texCoord - 0.5) * vec2(Screen.x / Screen.y, 1.0);
    float beam = smoothstep(0.52, 0.1, length(ec));
    float dark = mix(1.0, 0.18, PackA.y);
    vcr *= mix(dark, 1.06, beam * PackA.y) / max(1.0, 1.0);

    fragColor = vec4(clamp(vcr, 0.0, 1.0), 1.0);
}
