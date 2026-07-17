#version 330

uniform sampler2D SceneColorSampler;

in vec2 texCoord;

out vec4 fragColor;

float lumaOf(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));

    vec3 center = texture(SceneColorSampler, texCoord).rgb;
    vec3 north = texture(SceneColorSampler, texCoord + vec2(0.0, texel.y)).rgb;
    vec3 south = texture(SceneColorSampler, texCoord - vec2(0.0, texel.y)).rgb;
    vec3 east = texture(SceneColorSampler, texCoord + vec2(texel.x, 0.0)).rgb;
    vec3 west = texture(SceneColorSampler, texCoord - vec2(texel.x, 0.0)).rgb;

    float lumaC = lumaOf(center);
    float lumaN = lumaOf(north);
    float lumaS = lumaOf(south);
    float lumaE = lumaOf(east);
    float lumaW = lumaOf(west);

    float lumaMin = min(lumaC, min(min(lumaN, lumaS), min(lumaE, lumaW)));
    float lumaMax = max(lumaC, max(max(lumaN, lumaS), max(lumaE, lumaW)));
    float contrast = lumaMax - lumaMin;

    // Below this contrast there is no visible aliasing to fix; keep the pixel untouched.
    if (contrast < max(0.045, lumaMax * 0.125)) {
        fragColor = vec4(center, 1.0);
        return;
    }

    // Blend along the dominant edge direction.
    vec2 gradient = vec2(abs(lumaW - lumaE), abs(lumaN - lumaS));
    vec3 resolved = gradient.x > gradient.y ? (north + south) * 0.5 : (east + west) * 0.5;
    float blend = clamp((contrast - 0.045) * 2.0, 0.0, 0.4);

    fragColor = vec4(mix(center, resolved, blend), 1.0);
}
