#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#ifndef SDF_NO_FOG
#moj_import <minecraft:fog.glsl>
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif

uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    float distance = texture(Sampler0, texCoord0).r - (128.0 / 255.0);
    // Derivatives measure one final framebuffer pixel, including GUI scaling.
    float coverage = clamp(distance / max(fwidth(distance), 0.0001) + 0.5, 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a < 0.001) discard;
#ifdef SDF_NO_FOG
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
