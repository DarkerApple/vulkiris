#version 330

// Values of the pack's declared "settings", in declaration order:
// PackA.x = setting 0, PackA.y = 1, PackA.z = 2, PackA.w = 3,
// PackB.x = 4 ... PackB.w = 7. Undeclared slots are 0.
layout(std140) uniform VulkirisPackSettings {
    vec4 PackA;
    vec4 PackB;
};
