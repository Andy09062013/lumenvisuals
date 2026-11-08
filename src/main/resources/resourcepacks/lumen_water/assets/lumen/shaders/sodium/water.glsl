// Lumen (Sodium): water mirrors the sky/fog colour at grazing angles and is clearer.
bool lumen_isWater(vec4 color, vec4 vertColor) {
    return color.a < 0.97 && vertColor.b > vertColor.r * 1.1;
}

vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos, vec4 fogColor) {
    if (!lumen_isWater(color, vertColor)) return color.rgb;
    vec3 v = normalize(viewPos);
    float fresnel = pow(1.0 - abs(v.y), 4.0);
    return mix(color.rgb, fogColor.rgb, clamp(fresnel * 0.55, 0.0, 0.55));
}

float lumen_water_alpha(vec4 color, vec4 vertColor) {
    return lumen_isWater(color, vertColor) ? color.a * 0.6 : color.a;
}
