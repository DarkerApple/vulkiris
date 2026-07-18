#version 330

#moj_import <vulkiris:params.glsl>
#moj_import <vulkiris:pack_settings.glsl>

uniform sampler2D PreviousSampler;

in vec2 texCoord;
out vec4 fragColor;

float staticHash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Found-footage VCR tape in the place you noclipped into. The flashlight in your
// hand is the only light: its beam leaves a lit trail from the bottom right and a
// bright spot where it lands, and everything outside it is completely black.
// PackA.x = tape wear, PackA.y = flashlight, PackA.z = static.
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

    // Flashlight held in the hand: the beam starts just off-screen at the bottom
    // right (where the viewmodel is), widens as a lit trail across the room, and
    // ends in a bright spot that sways with your grip.
    float aspect = Screen.x / Screen.y;
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    vec2 hand = vec2(0.38 * aspect, -0.55);
    vec2 spot = vec2(sin(time * 1.7) * 0.025, 0.03 + cos(time * 2.3) * 0.018);
    vec2 toSpot = spot - hand;
    float along = clamp(dot(p - hand, toSpot) / dot(toSpot, toSpot), 0.0, 1.0);
    float offAxis = length(p - (hand + toSpot * along));
    float coneWidth = mix(0.04, 0.17, along);
    float trail = smoothstep(coneWidth, coneWidth * 0.3, offAxis) * mix(0.30, 0.85, along);
    float hit = smoothstep(0.30, 0.07, length(p - spot));
    float light = clamp((hit + trail) * PackA.y * 1.2, 0.0, 1.0);

    // Only the beam is lit; at full flashlight the rest of the room is pure black.
    float unlit = clamp(1.0 - PackA.y * 1.15, 0.0, 1.0);
    vcr *= mix(unlit, 1.08, light);
    vcr *= mix(vec3(1.0), vec3(1.05, 1.0, 0.88), light); // warm bulb tint

    // Signal-level artifacts sit on top of the darkness, like a real tape.
    vcr *= 1.0 - PackA.x * 0.14 * (0.5 + 0.5 * sin(texCoord.y * Screen.y * 3.1416));
    vcr *= 1.0 - PackA.x * 0.03 * (0.5 + 0.5 * sin(time * 24.0));
    float snow = staticHash(gl_FragCoord.xy + vec2(fract(time) * 511.0, fract(time * 0.37) * 293.0));
    vcr += (snow - 0.5) * 0.2 * PackA.z;
    float dropout = step(0.988, staticHash(vec2(floor(texCoord.y * 90.0), floor(time * 7.0))));
    vcr = mix(vcr, vec3(snow), dropout * 0.55 * PackA.z);

    // Tape frame: the corners always fall off to complete black, like a worn recording.
    vec2 c = (texCoord - 0.5) * 2.0;
    vcr *= 1.0 - smoothstep(0.85, 1.30, length(c));

    fragColor = vec4(clamp(vcr, 0.0, 1.0), 1.0);
}
