#version 330 core

layout(location = 0) in vec3 Position;

uniform mat4 uInvViewProj;

out vec4 vNear;
out vec4 vFar;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    
    
    vNear = uInvViewProj * vec4(Position.xy, -1.0, 1.0);
    vFar  = uInvViewProj * vec4(Position.xy,  1.0, 1.0);
}
