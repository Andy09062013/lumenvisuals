#version 330

// Lumen: water surfaces mirror the sky/fog colour at grazing angles (so sunsets show on lakes) and are a bit clearer.
// Only the flat top of the water is touched: the sides of water blocks (against ice, glass, etc.) stay vanilla.
// Uses FogColor from fog.glsl.
bool lumen_isWaterSurface(vec4 color, vec4 vertColor, vec3 viewPos) {
    if (color.a > 0.97 || vertColor.b < vertColor.r * 1.1) return false;
    vec3 n = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    return abs(n.y) > 0.9;
}

vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos) {
#ifdef ALPHA_CUTOUT
    return color.rgb;
#else
    if (!lumen_isWaterSurface(color, vertColor, viewPos)) return color.rgb;
    vec3 v = normalize(viewPos);
    float fresnel = pow(1.0 - abs(v.y), 4.0);
    return mix(color.rgb, FogColor.rgb, clamp(fresnel * 0.5, 0.0, 0.5));
#endif
}

float lumen_water_alpha(vec4 color, vec4 vertColor, vec3 viewPos) {
#ifdef ALPHA_CUTOUT
    return color.a;
#else
    return lumen_isWaterSurface(color, vertColor, viewPos) ? color.a * 0.78 : color.a;
#endif
}
