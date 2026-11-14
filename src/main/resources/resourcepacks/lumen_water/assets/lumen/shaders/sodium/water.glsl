// Lumen (Sodium): water surfaces mirror the sky/fog colour at grazing angles and are a bit clearer.
// Only the flat top of the water is touched: the sides of water blocks (against ice, glass, etc.) stay vanilla.
bool lumen_isWater(vec4 color, vec4 vertColor, vec3 viewPos) {
    if (color.a > 0.97 || vertColor.b < vertColor.r * 1.1) return false;
    vec3 n = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    return abs(n.y) > 0.9;
}

vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos, vec4 fogColor) {
    if (!lumen_isWater(color, vertColor, viewPos)) return color.rgb;
    vec3 v = normalize(viewPos);
    float fresnel = pow(1.0 - abs(v.y), 4.0);
    return mix(color.rgb, fogColor.rgb, clamp(fresnel * 0.5, 0.0, 0.5));
}

float lumen_water_alpha(vec4 color, vec4 vertColor, vec3 viewPos) {
    return lumen_isWater(color, vertColor, viewPos) ? color.a * 0.78 : color.a;
}

// ---- moving water: currents drift across rivers/lakes, flowing water streams downhill ----
float lumen_h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float lumen_vn(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(lumen_h(i), lumen_h(i + vec2(1.0, 0.0)), f.x), mix(lumen_h(i + vec2(0.0, 1.0)), lumen_h(i + vec2(1.0)), f.x), f.y);
}

vec3 lumen_waterflow(vec3 rgb, vec4 color, vec4 vertColor, vec3 viewPos, vec3 worldPos, float time, vec3 sky) {
    if (color.a > 0.97 || vertColor.b < vertColor.r * 1.1) return rgb;
    vec3 n = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    if (abs(n.y) < 0.9) return rgb;
    if (n.y < 0.0) n = -n;
    vec2 wp = mod(worldPos.xz, 4096.0);
    float sl = length(n.xz);
    vec2 dir;
    float speed;
    if (sl > 0.02) {
        dir = n.xz / sl;          // sloped (flowing) water: stream downhill, fast
        speed = 1.9;
    } else {
        float a = lumen_vn(wp * 0.015) * 12.566;   // still water: slow current that curves across the area
        dir = vec2(cos(a), sin(a));
        speed = 0.5;
    }
    vec2 q = floor(wp * 16.0) / 16.0;             // pixel-sized steps so it stays blocky like Minecraft
    vec2 perp = vec2(-dir.y, dir.x);
    float al = dot(q, dir) - time * speed;
    float ac = dot(q, perp);
    float s1 = lumen_vn(vec2(al * 0.9, ac * 3.0));
    float s2 = lumen_vn(vec2(al * 1.7 + 13.1, ac * 5.0 + 7.3));
    float streak = smoothstep(0.66, 0.86, s1) * 0.7 + smoothstep(0.72, 0.9, s2) * 0.5;
    float trough = smoothstep(0.35, 0.15, s1);
    float fade = 1.0 - smoothstep(20.0, 56.0, length(viewPos));
    float amt = (sl > 0.02 ? 1.0 : 0.7) * fade;
    rgb = mix(rgb, rgb * 0.85, trough * 0.4 * amt);
    rgb += (sky * 0.5 + vec3(0.28)) * streak * 0.24 * amt;
    return rgb;
}
