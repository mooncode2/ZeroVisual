#version 450 core

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec4 inColor;
layout(location = 2) in vec2 inUv;

layout(push_constant) uniform PC {
    mat4 uProj;
} pc;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec2 vUv;

void main() {
    gl_Position = pc.uProj * vec4(inPos, 1.0);
    vColor = inColor;
    vUv = inUv;
}
