#version 330
#moj_import <minecraft:dynamictransforms.glsl>

#ifdef UI_GLASS
uniform sampler2D Sampler0;
#endif
in vec2 localPixel;
in vec4 vertexColor;
flat in vec2 sizePixel;
flat in vec4 borderColor;
flat in float radiusPixel;
flat in float amountPixel;
flat in int flags;
in vec2 screenUV;
out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
void main() {
    float radius = min(radiusPixel, min(sizePixel.x, sizePixel.y) * 0.5);
    float d = sdRoundRect(localPixel - sizePixel * 0.5, sizePixel * 0.5, radius);
    float coverage = 1.0 - smoothstep(-0.5, 0.5, d);
    vec4 fill = vertexColor;
    if ((flags & 4) != 0) {
        float spread = max(amountPixel, 1.0);
        float halo = exp(-max(d, 0.0) * max(d, 0.0) / (spread * spread));
        float mask = (flags & 64) != 0 ? 1.0 : 1.0 - coverage;
        fragColor = vec4(fill.rgb, fill.a * halo * mask) * ColorModulator;
        if (fragColor.a < 0.002) discard;
        return;
    }
    float t = clamp(localPixel.y / sizePixel.y, 0.0, 1.0);
    if ((flags & 32) != 0) t = 1.0 - t;
    if ((flags & 1) != 0 && (flags & 16) == 0) {
        fill.rgb = mix(fill.rgb, vec3(1.0), 0.15 * (1.0 - t));
        if (fill.a < 0.6) fill.a *= mix(1.0, 0.22, t);
    }
#ifdef UI_GLASS
    if ((flags & 16) != 0) {
        vec2 uv = localPixel / sizePixel;
        if ((flags & 32) != 0) {
            vec2 sourceSize = vec2(textureSize(Sampler0, 0));
            float sourceAspect = sourceSize.x / max(sourceSize.y, 1.0);
            float panelAspect = sizePixel.x / max(sizePixel.y, 1.0);
            if ((flags & 1) != 0)
                uv = (uv - 0.5) * vec2(min(1.0, panelAspect / sourceAspect),
                                      min(1.0, sourceAspect / panelAspect)) + 0.5;
            else if ((flags & 8) != 0) {
                uv = (uv - 0.5) * vec2(max(1.0, panelAspect / sourceAspect),
                                      max(1.0, sourceAspect / panelAspect)) + 0.5;
                if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
            }
        } else uv.y = 1.0 - uv.y;
        fill = texture(Sampler0, uv) * fill;
    }
    if ((flags & 2) != 0) {
        vec3 scene = texture(Sampler0, screenUV).rgb;
        float gray = dot(scene, vec3(0.2126, 0.7152, 0.0722));
        scene = vec3(gray);
        scene = mix(scene, vec3(8.0) / 255.0, 0.25);
        fill.rgb = mix(scene, fill.rgb, fill.a);
        fill.a = 1.0;
    }
#endif
    float edge = smoothstep(-amountPixel - 0.5, -amountPixel + 0.5, d);
    float fillAlpha = fill.a;
    float strokeAlpha = borderColor.a * edge;
    float alpha = strokeAlpha + fillAlpha * (1.0 - strokeAlpha);
    vec3 rgb = (borderColor.rgb * strokeAlpha + fill.rgb * fillAlpha * (1.0 - strokeAlpha))
        / max(alpha, 0.00001);
    if ((flags & 8) != 0 && (flags & 16) == 0) {
        // Highlight follows the rounded edge; no separate straight second line.
        float highlight = edge * (1.0 - smoothstep(0.0, 12.0, localPixel.y)) * 0.45;
        rgb = mix(rgb, vec3(1.0), highlight);
    }
    fragColor = vec4(rgb, alpha * coverage) * ColorModulator;
    if (fragColor.a < 0.002) discard;
}
