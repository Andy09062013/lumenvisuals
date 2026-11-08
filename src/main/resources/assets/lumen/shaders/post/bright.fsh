#version 330

// Pulls out only the bright parts of the image (sky, sun, torches, lava) for bloom.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform BrightConfig {
    float Threshold;
    float Knee;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // sky and far-away pixels (where vanilla's square sun lives) don't bloom; Lumen draws its own round sun glow
    if (texture(DepthSampler, texCoord).r > 0.9998) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = max(c.r, max(c.g, c.b));
    float soft = clamp(l - Threshold + Knee, 0.0, 2.0 * Knee);
    soft = soft * soft / (4.0 * Knee + 0.0001);
    float w = max(soft, l - Threshold) / max(l, 0.0001);
    fragColor = vec4(c * w, 1.0);
}
