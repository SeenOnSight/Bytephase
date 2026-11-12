#version 330 core


in vec2 uv;
out vec4 outColor;
uniform sampler2D Mask;
uniform vec3 uSkip;            
uniform sampler2D Glow;      
uniform vec2  texel;
uniform float pxScale;
uniform float time;
uniform int   chroma;
uniform float outline;

float cov(vec2 p) {
    vec4 c = texture(Mask, p);
    return c.a * step(0.002, max(max(c.r, c.g), c.b)) * step(0.03, distance(c.rgb, uSkip));
}
vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }


vec3 playerColor(float m, vec4 g) {
    if (chroma == 1) {
        float h = fract(gl_FragCoord.y * texel.y * 0.6 + gl_FragCoord.x * texel.x * 0.3 + time * 0.25);
        return hsv2rgb(vec3(h, 0.75, 1.0));
    }
    vec3 c = m > 0.01 ? texture(Mask, uv).rgb : g.rgb / max(g.a, 1e-4);
    float mx = max(max(c.r, c.g), c.b);
    return mx > 1e-3 ? c / mx * min(1.0, mx * 1.6 + 0.2) : vec3(1.0);
}

float edgeRing(float radiusPx) {
    float e = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853982 + 0.3927;
        e += 1.0 - cov(uv + vec2(cos(a), sin(a)) * texel * radiusPx);
    }
    return e * 0.125;
}

float outerGlow(vec4 g) { return pow(clamp(g.a * 2.0, 0.0, 1.0), 0.6); }

uniform float intensity;
uniform float aspect;

vec3 prismField(vec2 p, float t, vec3 targetColor) {
    vec3 light = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 c = vec2(sin(t * (0.21 + fi * 0.05) + fi * 1.7) * 0.55 * aspect,
                      cos(t * (0.17 + fi * 0.04) + fi * 2.3) * 0.38);
        float r = 0.30 + 0.1 * sin(t * 0.3 + fi);
        float g = exp(-dot(p - c, p - c) / (r * r));
        float pulse = 0.5 + 0.5 * sin(t * 1.4 + fi * 1.9);
        vec3 highlight = min(vec3(1.0), targetColor * 1.35);
        light += mix(targetColor, highlight, 0.5 + 0.5 * sin(fi * 1.9 + t * 0.35)) * g * (0.4 + 0.6 * pulse);
    }
    float rays = 0.5 + 0.5 * sin((p.x + p.y * 0.4) * 9.0 + t * 0.6);
    return light * (0.6 + 0.3 * rays);
}

void main() {
    float m = cov(uv);
    vec4  g = textureLod(Glow, uv, 0.0);
    if (m < 0.01 && g.a < 0.002) discard;
    vec3 pc = playerColor(m, g);
    vec3 highlight = min(vec3(1.0), pc * 1.35);
    vec3 tint = mix(pc, highlight, 0.5 + 0.5 * sin(time * 0.8));

    if (m > 0.01) {
        vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
        vec3 col = tint * 0.06 + prismField(p, time, pc) * 1.3;
        float edge  = edgeRing(1.6 * pxScale);
        float rim   = smoothstep(0.0, 0.25, edge) * outline;
        float inner = 1.0 - smoothstep(0.5, 0.95, g.a);
        col += pc * rim * 1.1 + tint * inner * 0.35;
        outColor = vec4(col * m, m);
    } else {
        float s = outerGlow(g) * 0.7 * intensity * (0.75 + 0.25 * sin(time * 1.4));
        outColor = vec4(tint * s, clamp(s * 0.2, 0.0, 1.0));
    }
}
