#version 330 core


in vec2 uv;
out vec4 outColor;
uniform sampler2D Mask;
uniform sampler2D Scene;
uniform vec4 uRect;      
uniform vec3 uTag;
void main() {
    vec2 p = mix(uRect.xy, uRect.zw, uv);
    vec4 m = texture(Mask, p);
    float cov = m.a * step(distance(m.rgb, uTag), 0.035);
    vec3 s = texture(Scene, p).rgb;
    outColor = vec4(s * cov, cov);
}
