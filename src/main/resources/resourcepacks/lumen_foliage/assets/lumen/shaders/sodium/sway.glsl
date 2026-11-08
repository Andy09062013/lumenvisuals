// Lumen (Sodium): grass, ferns, tall grass, sugar cane and vines bend downwind and spring back.
// Only biome-tinted cross-shaped plants move. (Leaf/crop sway needs the vanilla renderer.)
const vec3 LUMEN_WIND = vec3(0.86, 0.0, 0.51);

vec3 lumen_sway(vec3 p, vec4 color, int vertexId, float t) {
    vec2 f = fract(p.xz);
    bool offGrid = (f.x > 0.02 && f.x < 0.98) || (f.y > 0.02 && f.y < 0.98);
    bool green = color.g > color.r * 1.1 && color.g > color.b * 1.1;
    if (!offGrid || !green) return vec3(0.0);
    int corner = vertexId % 4;
    float top = (corner == 0 || corner == 3) ? 1.0 : 0.0;
    float phase = dot(p.xz, LUMEN_WIND.xz) * 0.12;
    float strength = 0.55 + 0.45 * sin(t * 0.35 - phase * 0.5);
    float push = 0.5 + 0.5 * sin(t * 2.0 - phase * 2.5 + fract(p.x * 7.31) * 0.6);
    return LUMEN_WIND * (push * 0.11 * strength) * top;
}
