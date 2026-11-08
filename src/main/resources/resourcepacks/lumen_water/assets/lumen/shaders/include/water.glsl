#version 330

// Lumen: water (translucent, blue-tinted surfaces) mirrors the sky/fog color at grazing angles,
// so sunsets and sunrises show up on lakes and the ocean. Uses FogColor from fog.glsl.
vec3 lumen_water(vec4 color, vec4 vertColor, vec3 viewPos) {
#ifdef ALPHA_CUTOUT
    return color.rgb;
#else
    if (color.a > 0.97 || vertColor.b < vertColor.r * 1.1) return color.rgb;
    vec3 v = normalize(viewPos);
    float fresnel = pow(1.0 - abs(v.y), 4.0);
    return mix(color.rgb, FogColor.rgb, clamp(fresnel * 0.55, 0.0, 0.55));
#endif
}

// Lumen: clearer water, you can see further into lakes and the sea.
float lumen_water_alpha(vec4 color, vec4 vertColor) {
#ifdef ALPHA_CUTOUT
    return color.a;
#else
    if (color.a > 0.97 || vertColor.b < vertColor.r * 1.1) return color.a;
    return color.a * 0.6;
#endif
}
