#version 330 core


in vec2 uv;
out vec4 outColor;

uniform sampler2D Source;
uniform vec2  direction;   
uniform float sigma;       

void main() {
    vec4 acc = textureLod(Source, uv, 0.0);
    if (sigma < 0.3) { outColor = acc; return; }
    float tw = 1.0;
    float k  = -0.5 / (sigma * sigma);
    int   n  = min(int(ceil(sigma * 3.0)), 48);
    for (int i = 1; i <= n; i += 2) {
        float a  = float(i);
        float b  = a + 1.0;
        float wa = exp(a * a * k);
        float wb = (i + 1 <= n) ? exp(b * b * k) : 0.0;
        float w  = wa + wb;
        float o  = (a * wa + b * wb) / w;
        acc += (textureLod(Source, uv + direction * o, 0.0) + textureLod(Source, uv - direction * o, 0.0)) * w;
        tw  += 2.0 * w;
    }
    outColor = acc / tw;
}
