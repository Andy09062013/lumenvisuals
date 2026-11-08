#version 330

// Default: unchanged water. The "Lumen: sky colors on water" pack replaces this file.
vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos) {
    return color.rgb;
}

// Default: unchanged transparency.
float lumen_water_alpha(vec4 color, vec4 vertColor, vec3 viewPos) {
    return color.a;
}
