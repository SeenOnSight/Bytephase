#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D Mask;
uniform vec3 uSkip;            
uniform vec2  srcTexel;
uniform float spread;

vec4 tap(vec2 p) {
    vec4 c = texture(Mask, p);
    float m = c.a * step(0.002, max(max(c.r, c.g), c.b)) * step(0.03, distance(c.rgb, uSkip));
    return vec4(c.rgb * m, m);
}

void main() {
    vec2 o = vec2(spread) * srcTexel;
    outColor = 0.25 * (tap(uv + vec2(-o.x, -o.y)) + tap(uv + vec2(o.x, -o.y))
                     + tap(uv + vec2(-o.x,  o.y)) + tap(uv + vec2(o.x,  o.y)));
}
