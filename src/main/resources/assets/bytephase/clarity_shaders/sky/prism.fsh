






vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime * 0.6;
    float y = dir.y;
    vec3 tint = mix(uColor1, uColor2, 0.5);
    vec3 col = mix(tint * 0.010, tint * 0.004, smoothstep(-0.1, 0.7, y));   

    vec3 light = vec3(0.0);
    float r = 0.42 * uScale;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float a = t * (0.05 + 0.017 * fi) + fi * 1.2566;
        float h = 0.18 + 0.32 * (0.5 + 0.5 * sin(t * (0.07 + 0.02 * fi) + fi * 2.3));
        vec3 c = normalize(vec3(sin(a), h, cos(a)));
        float rr = r * (0.85 + 0.25 * sin(t * 0.2 + fi));
        float g = exp(-(1.0 - dot(dir, c)) / (rr * rr * 0.5));
        float pulse = 0.45 + 0.55 * pow(0.5 + 0.5 * sin(t * 1.1 + fi * 1.9), 1.5);
        light += mix(uColor1, uColor2, 0.5 + 0.5 * sin(fi * 1.9 + t * 0.3)) * g * pulse;
    }
    float az = atan(dir.z, dir.x);
    float rays = 0.5 + 0.5 * sin(az * 7.0 + y * 5.0 + t * 0.4);
    light *= 0.6 + 0.3 * rays;
    light *= 0.8 + 0.2 * sin(t * 0.9);                                      

    col += light * 0.32 * uIntensity * smoothstep(-0.15, 0.1, y);
    col += uColor1 * 0.025 * exp(-abs(y) * 12.0) * (0.7 + 0.3 * sin(t * 0.8)) * uIntensity;  
    return col;
}
