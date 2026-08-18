#version 450 core
layout(location = 0) in vec4 vColor;
layout(location = 1) in vec2 vUv;
layout(set = 0, binding = 0) uniform sampler2D uTex;
layout(location = 0) out vec4 FragColor;

void main() {
    FragColor = texture(uTex, vUv) * vColor;
}
