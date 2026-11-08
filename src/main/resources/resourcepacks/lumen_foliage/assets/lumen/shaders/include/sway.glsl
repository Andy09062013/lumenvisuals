#version 330

// Lumen: swaying leaves, grass, ferns, crops, dead bushes, dry grass, sugar cane and vines.
// Leaves (vertex alpha 253) and crops/dead bushes/dry grass (alpha 252) are tagged by the mod while chunks are built.
// Other plants: only biome-tinted (green) cross-shaped models move.
// Like real wind, things bend one way (downwind) and spring back, instead of wobbling around the middle.
// Uses GameTime from globals.glsl (imported by terrain.vsh before this file).

const vec3 LUMEN_WIND = vec3(0.86, 0.0, 0.51); // prevailing wind direction

vec3 lumen_sway(vec3 wp, vec4 color, int vertexId) {
    float t = GameTime * 1200.0;
    // gusts roll across the land as a slow wave, so neighbours move together
    float phase = dot(wp.xz, LUMEN_WIND.xz) * 0.12;
    float strength = 0.55 + 0.45 * sin(t * 0.35 - phase * 0.5);

    // leaves: the canopy leans downwind and settles back, with a little flutter
    if (abs(color.a - 253.0 / 255.0) < 0.002) {
        float push = 0.5 + 0.5 * sin(t * 1.6 - phase * 2.0 + wp.y * 0.15);
        float flutter = sin(t * 2.4 + wp.x * 0.7 + wp.z * 0.5 + wp.y * 0.4) * 0.006;
        return LUMEN_WIND * (push * 0.045 * strength) + vec3(flutter, flutter * 0.6, -flutter);
    }

    bool plant = abs(color.a - 252.0 / 255.0) < 0.002;
    if (!plant) {
        vec2 f = fract(wp.xz);
        bool offGrid = (f.x > 0.02 && f.x < 0.98) || (f.y > 0.02 && f.y < 0.98);
        bool green = color.g > color.r * 1.1 && color.g > color.b * 1.1;
        if (!offGrid || !green) return vec3(0.0);
    }
    // block model quads go top-left, bottom-left, bottom-right, top-right: only move the top edge
    int corner = vertexId % 4;
    float top = (corner == 0 || corner == 3) ? 1.0 : 0.0;
    float push = 0.5 + 0.5 * sin(t * 2.0 - phase * 2.5 + fract(wp.x * 7.31) * 0.6);
    return LUMEN_WIND * (push * 0.11 * strength) * top;
}
