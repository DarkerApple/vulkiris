#version 330

// Must match the byte layout written by dev.vulkiris.render.VulkirisUniforms.
layout(std140) uniform VulkirisParams {
    mat4 InvProjection; // inverse of the frame's projection matrix
    vec4 SunDir;        // xyz: view-space sun direction, w: sun visibility (0 at night/rain)
    vec4 UpDir;         // xyz: view-space world up, w: camera world-space Y
    vec4 Fog;           // rgb: fog color, w: fog density setting (0..1)
    vec4 Screen;        // xy: screen size, z: world time in seconds, w: 1 when camera is underwater
    vec4 GradeA;        // x: exposure, y: saturation, z: contrast, w: vignette strength
    vec4 BloomParams;   // x: bloom intensity, y: bloom threshold, z: sun scatter, w: 1 when NDC depth is 0..1
    vec4 Toggles;       // x: tonemap mode (0 off, 1 aces, 2 filmic), y: fog on, z: bloom on, w: rain factor
};
