#version 330
// Zero SkyBox - End sky vertex stage (fullscreen triangle in clip space)

in vec3 Position;
in vec2 UV0;

out vec2 texCoord0;

void main() {
    texCoord0 = UV0;
    gl_Position = vec4(Position.xy, 1.0, 1.0);
}
