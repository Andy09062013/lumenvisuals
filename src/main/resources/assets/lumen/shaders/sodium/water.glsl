// Default (Sodium): unchanged water. The "Lumen: sky colors on water" pack replaces this file.
vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos, vec4 fogColor) {
    return color.rgb;
}

float lumen_water_alpha(vec4 color, vec4 vertColor) {
    return color.a;
}
