#version 330

// Default: unchanged water. The "Lumen: sky colors on water" pack replaces this file.
vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos) {
    return color.rgb;
}

// Default: unchanged transparency.
float lumen_water_alpha(vec4 color, vec4 vertColor, vec3 viewPos) {
    return color.a;
}

// Default: no moving water. The "Lumen: water" pack replaces this file.
vec3 lumen_waterflow(vec3 rgb, vec4 color, vec4 vertColor, vec3 viewPos, vec3 worldPos, float time, vec3 sky) {
    return rgb;
}
