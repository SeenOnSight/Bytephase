#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 localPixel;
out vec4 vertexColor;
flat out vec2 sizePixel;
flat out vec4 borderColor;
flat out float radiusPixel;
flat out float amountPixel;
flat out int flags;
out vec2 screenUV;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPixel = UV0;
    sizePixel = vec2(UV2);
    vertexColor = Color;
    uint packed = (uint(UV1.x) & 65535u) | ((uint(UV1.y) & 65535u) << 16);
    borderColor = vec4(float((packed >> 16) & 255u), float((packed >> 8) & 255u),
                       float(packed & 255u), float(packed >> 24)) / 255.0;
    radiusPixel = round(Normal.x * 127.0);
    amountPixel = round(Normal.y * 127.0);
    flags = int(round(Normal.z * 127.0));
    screenUV = gl_Position.xy / gl_Position.w * 0.5 + 0.5;
}
