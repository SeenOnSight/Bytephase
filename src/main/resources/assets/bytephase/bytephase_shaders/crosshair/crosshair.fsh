#version 330 core



in vec2 uv;
out vec4 outColor;

uniform vec2  uRes;          
uniform vec2  uOffset;       
uniform float uK;            
uniform int   uStyle;        
uniform float uLen, uGap, uThick, uDot, uRot, uOutline, uGlow, uTime, uSpread;
uniform int   uColorMode;    
uniform vec3  uCol1, uCol2, uOutCol, uHitCol, uTargetCol;
uniform float uTarget;       
uniform float uHitAge, uKillAge;   
uniform int   uHitmarker, uExpand, uFlash, uShock;
uniform float uCooldown;     

const float PI = 3.14159265;

float sdSeg(vec2 p, vec2 a, vec2 b, float r) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}
mat2 rot(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}
float smoothOut(float x) { x = clamp(x, 0.0, 1.0); return 1.0 - (1.0 - x) * (1.0 - x); }


vec4 over(vec4 top, vec4 under) { return top + under * (1.0 - top.a); }

void main() {
    vec2 p = uv * uRes - uRes * 0.5 - uOffset;
    float k = uK;

    float hit  = uHitAge  < 0.0 ? 0.0 : 1.0 - clamp(uHitAge / 0.38, 0.0, 1.0);   
    float kill = uKillAge < 0.0 ? 0.0 : 1.0 - clamp(uKillAge / 0.70, 0.0, 1.0);
    float eh = hit * hit * (3.0 - 2.0 * hit);
    float ek = kill * kill * (3.0 - 2.0 * kill);

    float gap = (uGap + uSpread + (uExpand == 1 ? 4.5 * eh : 0.0) + 5.0 * ek) * k;
    float len = uLen * k;
    float t = max(0.5, uThick * k * 0.5);
    vec2 q = rot(radians(uRot)) * p;
    vec2 qa = abs(q);

    
    float d = 1e5;
    if (uStyle == 0 || uStyle == 3) {
        d = min(sdSeg(qa, vec2(gap, 0.0), vec2(gap + len, 0.0), t),
                sdSeg(qa, vec2(0.0, gap), vec2(0.0, gap + len), t));
    } else if (uStyle == 2) {
        d = abs(length(p) - (gap + len * 0.5)) - t;
    } else if (uStyle == 4) {                            
        float R = gap + len * 0.5;
        float a = atan(q.y, q.x);
        float m = abs(mod(a, PI * 0.5) - PI * 0.25);      
        float halfArc = PI * 0.25 - 0.32;
        d = max(abs(length(q) - R) - t, (m - halfArc) * length(q));
    } else if (uStyle == 5) {                            
        vec2 c = vec2(abs(q.x), q.y);
        d = sdSeg(c, vec2(0.0, -gap), vec2(len * 0.75, -gap - len * 0.75), t);
    } else if (uStyle == 6) {                            
        float R = gap + len * 0.6;
        d = abs((qa.x + qa.y) - R) * 0.70710678 - t;
    } else if (uStyle == 7) {                            
        float R = gap + len * 0.75, l = len * 0.55;
        d = min(sdSeg(qa, vec2(R, R), vec2(R - l, R), t), sdSeg(qa, vec2(R, R), vec2(R, R - l), t));
    }
    if (uStyle == 1 || uStyle == 3 || uStyle == 5) d = min(d, length(p) - max(0.6, uDot * k * 0.5));

    
    float ang = atan(p.y, p.x);
    vec3 col = uCol1;
    if (uColorMode == 1) col = mix(uCol1, uCol2, 0.5 + 0.5 * sin(ang + uTime * 2.0));
    else if (uColorMode == 2) col = hsv(fract(ang / (2.0 * PI) + uTime * 0.25), 0.75, 1.0);
    col = mix(col, uTargetCol, uTarget);
    if (uFlash == 1) col = mix(col, uHitCol, max(eh * 0.9, ek));

    
    vec4 c = vec4(0.0);
    float glowR = 3.5 * k;
    float g = uGlow * (0.5 + 0.6 * max(eh, ek)) * exp(-max(d, 0.0) / glowR) * 0.6;
    c = over(vec4(col * g, g), c);
    if (uOutline > 0.0) {
        float o = clamp(0.5 - (d - uOutline * k), 0.0, 1.0) * 0.85;
        c = over(vec4(uOutCol * o, o), c);
    }
    float f = clamp(0.5 - d, 0.0, 1.0);
    c = over(vec4(col * f, f), c);

    
    if (uCooldown < 0.999) {
        float R = (uGap + uLen + uSpread + 6.0) * k;
        float rd = abs(length(p) - R) - 0.75 * k;
        float fa = fract(atan(p.x, p.y) / (2.0 * PI));             
        float fill = fa <= uCooldown ? 1.0 : 0.22;
        float a = clamp(0.5 - rd, 0.0, 1.0) * fill * 0.9;
        c = over(vec4(col * a, a), c);
    }

    
    if (uShock == 1 && (hit > 0.0 || kill > 0.0)) {
        float s = max(hit, kill);
        float R = (uGap + uLen) * k + (1.0 - s) * (16.0 + 10.0 * ek) * k;
        float rd = abs(length(p) - R) - (0.6 + 0.8 * s) * k;
        float a = clamp(0.5 - rd, 0.0, 1.0) * s * s;
        float ga = s * s * 0.5 * exp(-max(rd, 0.0) / (3.0 * k));
        vec3 sc = mix(col, uHitCol, 0.7);
        c = over(vec4(sc * a, a), over(vec4(sc * ga, ga), c));
    }

    
    if (uHitmarker == 1 && (hit > 0.0 || kill > 0.0)) {
        float s = max(hit, kill);
        float h0 = (uGap + 2.5 + (1.0 - smoothOut(s)) * 3.5 + 3.0 * ek) * k;
        float hl = (4.5 + 3.5 * ek) * k;
        vec2 dir = vec2(0.70710678);
        float dh = sdSeg(abs(p), dir * h0, dir * (h0 + hl), max(t, 0.8 * k));
        vec3 hc = mix(vec3(1.0), uHitCol, max(ek, 0.35));
        float o = clamp(0.5 - (dh - 1.0 * k), 0.0, 1.0) * s * 0.75;
        float a = clamp(0.5 - dh, 0.0, 1.0) * s;
        float ga = s * 0.45 * exp(-max(dh, 0.0) / (3.0 * k));
        c = over(vec4(hc * a, a), over(vec4(uOutCol * o, o), over(vec4(hc * ga, ga), c)));
    }

    outColor = c;
}
