#version 330 core



layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec2 Aux;

uniform mat4 uViewProj;

out vec2 vUV;
out vec4 vColor;
out vec2 vAux;

void main() {
    gl_Position = uViewProj * vec4(Position, 1.0);
    vUV = UV;
    vColor = Color;
    vAux = Aux;
}
