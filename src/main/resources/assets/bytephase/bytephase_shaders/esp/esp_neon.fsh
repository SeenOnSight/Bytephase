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
uniform vec3  uAccent, uAccent2;
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 7.1; a *= 0.5; } return s / 0.9375; }

void main() {
    float m = cov(uv);
    vec4  g = textureLod(Glow, uv, 0.0);
    if (m < 0.01 && g.a < 0.002) discard;
    vec3 pc = playerColor(m, g);
    float hum = 0.92 + 0.08 * sin(time * 40.0) * step(0.96, hash11(floor(time * 5.0)));
    if (m > 0.01) {
        float edge = edgeRing(2.4 * pxScale);
        float tube = smoothstep(0.05, 0.4, edge);
        vec3 col = pc * 0.05 + pc * tube * 1.4 * hum + vec3(1.0) * pow(tube, 3.0) * 0.5 * hum;
        float a = max(0.25, tube);
        outColor = vec4(col * m * a, m * a);
    } else {
        float s = outerGlow(g) * 0.9 * intensity * hum;
        outColor = vec4(pc * s, clamp(s * 0.25, 0.0, 1.0));
    }
}
