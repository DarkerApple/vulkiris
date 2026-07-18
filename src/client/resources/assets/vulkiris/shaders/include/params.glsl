#version 330

// Must match the byte layout written by dev.vulkiris.render.VulkirisUniforms.
layout(std140) uniform VulkirisParams {
    mat4 InvProjection; // inverse of the frame's projection matrix
    mat4 InvViewRot;    // view-space direction -> world-space direction
    mat4 Projection;    // the frame's projection matrix (SSR/reprojection)
    vec4 SunDirView;    // xyz: view-space sun direction, w: sun visibility (0 at night/rain)
    vec4 SunDirWorld;   // xyz: world-space sun direction, w: sky intensity setting
    vec4 UpDir;         // xyz: view-space world up, w: camera world-space Y
    vec4 CameraPos;     // xyz: camera world position, w: rain factor
    vec4 Fog;           // rgb: fog color, w: fog density setting (0..1)
    vec4 Screen;        // xy: screen size, z: world time in seconds, w: 1 when camera is underwater
    vec4 GradeA;        // x: exposure, y: saturation, z: contrast, w: vignette strength
    vec4 BloomParams;   // x: bloom intensity, y: bloom threshold, z: sun scatter, w: 1 when NDC depth is 0..1
    vec4 Toggles;       // x: tonemap mode (0 off, 1 aces, 2 filmic), y: fog on, z: bloom on, w: tonemap strength
    vec4 Extra;         // x: warmth, y: ao strength, z: water shading on, w: god rays strength
    vec4 Extra2;        // x: sun specular, y: light bleed, z: ssr steps (0/12/24), w: easter-egg mode
    vec4 SunScreen;     // xy: sun position in UV space, z: 1 when usable, w: easter-egg strength
};
