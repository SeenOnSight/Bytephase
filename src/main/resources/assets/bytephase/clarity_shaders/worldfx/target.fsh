#version 330 core


in vec2 vUV;
in vec4 vColor;
in vec2 vAux;
out vec4 fragColor;

uniform float uTime, uFlash, uIn;
uniform int   uStyle;    
uniform vec3  uColor2;
uniform vec3  uRight, uUp, uFwd;

const float PI = 3.14159265, TAU = 6.2831853;
float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
vec3 hsv2rgb(vec3 c) { vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0); return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y); }
float aa(float d) { float w = fwidth(d) * 0.8 + 1e-4; return smoothstep(w, -w, d); }

float sdHeart(vec2 p) {
    p.y = -p.y * 1.1 + 0.55; p.x = abs(p.x) * 1.1;
    if (p.y + p.x > 1.0) return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - 0.3535534;
    return sqrt(min(dot(p - vec2(0.0, 1.0), p - vec2(0.0, 1.0)), dot(p - 0.5 * max(p.x + p.y, 0.0), p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}
float star4(vec2 uv, float sharp) { vec2 a = abs(uv); return max(exp(-a.x * sharp) * exp(-a.y * 2.5), exp(-a.y * sharp) * exp(-a.x * 2.5)); }
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float vnoise(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y); }
float vnoise3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    float n = i.x + i.y * 57.0 + i.z * 113.0;
    return mix(mix(mix(hash11(n), hash11(n + 1.0), f.x), mix(hash11(n + 57.0), hash11(n + 58.0), f.x), f.y),
               mix(mix(hash11(n + 113.0), hash11(n + 114.0), f.x), mix(hash11(n + 170.0), hash11(n + 171.0), f.x), f.y), f.z); }
float ring(float r, float R, float w) { float x = (r - R) / w; return exp(-x * x); }

void main() {
    vec2 uv = vUV;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);
    vec3 c1 = vColor.rgb, c2 = uColor2;
    vec3 col = vec3(0.0); float a = 0.0;
    float fl = uFlash;

    if (uStyle == 0) {                                   
        if (r > 1.25) discard;
        float n = vnoise(vec2(ang * 3.0 + uTime * 1.5, r * 6.0 - uTime * 0.8));
        float n2 = vnoise(vec2(ang * 7.0 - uTime * 2.3, r * 12.0));
        float R = 0.82 + (n - 0.5) * 0.06;
        float core = ring(r, R, 0.03 + 0.02 * n2);
        float glow = ring(r, R, 0.20) * (0.5 + 0.5 * n);
        float arcs = pow(0.5 + 0.5 * sin(ang * 3.0 - uTime * 2.5), 12.0) * ring(r, R, 0.08);
        float inner = smoothstep(R, 0.0, r) * 0.08 * (0.6 + 0.4 * n2);
        col = mix(c1, vec3(1.0), 0.5) * core * 1.4 + c1 * glow * 0.6 + c2 * arcs * 1.2 + c2 * inner;
        col *= smoothstep(1.25, 1.05, r);
    } else if (uStyle == 1) {                            
        if (r > 1.3) discard;
        float rot = uTime * 1.2;
        float aw = fract((ang + rot) / TAU);
        float outer = ring(r, 0.95, 0.018) * step(0.08, fract(aw * 4.0)) * step(fract(aw * 4.0), 0.42);
        float aw2 = fract((ang - rot * 1.6) / TAU);
        float inner = ring(r, 0.68, 0.012) * step(0.5, fract(aw2 * 24.0));
        vec2 q = abs(uv);
        float br = 0.0;                                   
        float bx = smoothstep(0.02, 0.0, abs(q.x - 0.55)) * step(0.35, q.y) * step(q.y, 0.55);
        float by = smoothstep(0.02, 0.0, abs(q.y - 0.55)) * step(0.35, q.x) * step(q.x, 0.55);
        br = max(bx, by);
        float ticks = smoothstep(0.015, 0.0, min(q.x, q.y)) * step(0.82, max(q.x, q.y)) * step(max(q.x, q.y), 1.08);
        float dot_ = exp(-r * r / 0.002);
        float pulse = ring(r, fract(uTime * 0.8) * 1.2, 0.03) * (1.0 - fract(uTime * 0.8));
        float f = outer * 1.3 + inner + br * 1.1 + ticks * 0.9 + dot_ + pulse * 0.6;
        col = mix(c1, vec3(1.0), 0.25) * f + c2 * ring(r, 0.95, 0.12) * 0.25;
        col *= smoothstep(1.3, 1.15, r);
    } else if (uStyle == 2) {                            
        float y = uv.y;
        float s1 = fract(uv.x * 3.0 + y * 1.4 - uTime * 0.7);
        float s2 = fract(uv.x * 5.0 - y * 0.9 - uTime * 1.1 + 0.37);
        float band = smoothstep(0.0, 0.12, s1) * smoothstep(0.35, 0.12, s1);
        float band2 = smoothstep(0.0, 0.08, s2) * smoothstep(0.2, 0.08, s2);
        float n = vnoise(vec2(uv.x * 20.0, y * 6.0 - uTime * 2.0));
        float fy = smoothstep(0.0, 0.15, y) * smoothstep(1.0, 0.6, y);
        col = (c1 * band * (0.7 + 0.6 * n) * 1.3 + c2 * band2 * 0.9) * fy;
        a = 0.0;
    } else if (uStyle == 3) {                            
        if (r > 1.0) discard;
        float nz = sqrt(1.0 - r * r);
        vec3 n = normalize(uv.x * uRight + uv.y * uUp - nz * uFwd);   
        float fres = pow(1.0 - nz, 2.2);
        
        vec3 an = abs(n);
        vec2 hp = (an.y > max(an.x, an.z) ? n.xz : (an.x > an.z ? n.yz : n.xy)) * 7.0;
        vec2 hs = vec2(1.0, 1.7320508);
        vec2 ga = mod(hp, hs) - hs * 0.5, gb = mod(hp - hs * 0.5, hs) - hs * 0.5;
        vec2 gv = dot(ga, ga) < dot(gb, gb) ? ga : gb;
        vec2 ab = abs(gv);
        float hexD = 0.5 - max(dot(ab, normalize(vec2(1.0, 1.7320508))), ab.x);
        float seam = smoothstep(0.05, 0.0, hexD);
        float flow = vnoise3(n * 3.0 + vec3(0.0, uTime * 0.8, 0.0));
        float sweep = ring(n.y, sin(uTime * 1.3) * 0.9, 0.08);
        col = c1 * (fres * 1.2 + seam * (0.12 + 0.6 * fres) * (0.6 + 0.6 * flow) + sweep * 0.35) + c2 * fres * fres * 0.6;
        col *= smoothstep(1.0, 0.97, r);
        a = fres * 0.25;
    } else if (uStyle == 5 || uStyle == 6) {             
        if (r > 1.3) discard;
        float R = 0.82;
        float halo = ring(r, R, 0.012) * 0.35 + ring(r, R, 0.12) * 0.12;
        col = c1 * halo;
        int n = uStyle == 5 ? 3 : 2;
        for (int i = 0; i < 3; i++) {
            if (i >= n) break;
            float a0 = uTime * 2.2 + float(i) * TAU / float(n);
            vec2 cpos = vec2(cos(a0), sin(a0)) * R;
            vec2 tdir = vec2(-sin(a0), cos(a0));
            vec2 q = uv - cpos;
            vec2 lq = vec2(dot(q, tdir), dot(q, cpos / R));          
            float d;
            if (uStyle == 5) {
                vec2 p0 = vec2(0.17, 0.0), p1 = vec2(-0.09, 0.11), p2 = vec2(-0.09, -0.11);
                vec2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
                vec2 v0 = lq - p0, v1 = lq - p1, v2 = lq - p2;
                vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
                vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
                vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
                float sg = sign(e0.x * e2.y - e0.y * e2.x);
                vec2 dd = min(min(vec2(dot(pq0, pq0), sg * (v0.x * e0.y - v0.y * e0.x)),
                                  vec2(dot(pq1, pq1), sg * (v1.x * e1.y - v1.y * e1.x))),
                                  vec2(dot(pq2, pq2), sg * (v2.x * e2.y - v2.y * e2.x)));
                d = -sqrt(dd.x) * sign(dd.y);
            } else {                                                  
                float c0 = length(lq - vec2(0.0, 0.0)) - 0.16;
                float c1_ = length(lq - vec2(-0.05, -0.07)) - 0.15;
                d = max(c0, -c1_);
            }
            float fill = aa(d);
            float edge = exp(-pow(d / 0.012, 2.0));
            float glow = exp(-max(d, 0.0) * 25.0) * (1.0 - fill);
            
            float ang = atan(uv.y, uv.x);
            float dAng = mod(a0 - ang + PI, TAU) - PI;               
            float trail = dAng > 0.0 ? exp(-dAng * 3.0) * ring(r, R, 0.025) : 0.0;
            col += mix(c1, c2, 0.3) * fill * 0.7 + mix(c1, vec3(1.0), 0.6) * edge * 1.3 + c1 * glow * 0.6 + c2 * trail * 0.9;
            a += fill * 0.4;
        }
        col *= smoothstep(1.3, 1.15, r); a *= smoothstep(1.3, 1.15, r);
    } else if (uStyle == 7) {                            
        if (r > 1.3) discard;
        col = c1 * (ring(r, 0.92, 0.01) * 0.5 + ring(r, 0.92, 0.1) * 0.12);
        for (int i = 0; i < 4; i++) {
            float a0 = -uTime * 1.4 + float(i) * TAU / 4.0;
            vec2 cpos = vec2(cos(a0), sin(a0)) * 0.92;
            vec2 q = uv - cpos;
            float cr = cos(uTime * 3.0 + float(i)), sr = sin(uTime * 3.0 + float(i));
            q = mat2(cr, -sr, sr, cr) * q;
            float d = abs(q.x) * 1.0 + abs(q.y) * 0.55 - 0.1;     
            float fill = aa(d);
            float edge = exp(-pow(d / 0.01, 2.0));
            float glint = pow(max(0.0, sin((q.x + q.y) * 30.0 + uTime * 6.0)), 6.0) * fill;
            col += mix(c2, c1, 0.4) * fill * 0.6 + mix(c1, vec3(1.0), 0.6) * edge * 1.2 + vec3(1.0) * glint * 0.5
                 + c1 * exp(-max(d, 0.0) * 22.0) * (1.0 - fill) * 0.5;
            a += fill * 0.35;
        }
        col *= smoothstep(1.3, 1.15, r); a *= smoothstep(1.3, 1.15, r);
    } else {                                             
        if (r > 1.3) discard;
        vec2 p = uv + vec2(0.0, 0.1 * sin(uTime * 3.0));
        float d = max(abs(p.x) * 1.15 + p.y * 0.62 - 0.55, -(abs(p.x) * 1.15 + p.y * 0.62 - 0.22));
        d = max(d, -p.y - 0.75);
        float m = aa(d);
        float edge = exp(-pow(d / 0.03, 2.0));
        float glow = exp(-max(d, 0.0) * 9.0) * (1.0 - m);
        float shine = pow(max(0.0, sin((p.x + p.y) * 4.0 - uTime * 4.0)), 10.0);
        col = (mix(c1, c2, smoothstep(0.5, -0.6, p.y)) * (0.75 + shine * 0.6)) * m + vec3(1.0) * edge * 0.5 + c1 * glow * 0.6;
        a = m * 0.8;
        col *= smoothstep(1.3, 1.1, r); a *= smoothstep(1.3, 1.1, r);
    }
    
    col *= 1.0 + fl * 0.8;
    float k = smoothstep(0.0, 1.0, uIn);
    fragColor = vec4(col, a) * vColor.a * k;
}
