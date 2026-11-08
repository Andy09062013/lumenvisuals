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
