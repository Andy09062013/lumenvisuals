#version 330

// Depth-aware blur for the lighting mask: smooths shadows/AO without bleeding across block edges.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LumenBlur {
    vec2 Dir;
};

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

float linearDepth(float d, float far) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NEAR * far) / (far + NEAR - z * (far - NEAR));
}

void main() {
    float far = max(texelFetch(DataSampler, ivec2(2, 0), 0).a * 4096.0, 64.0);
    vec2 stepUV = Dir / InSize;
    float z0 = linearDepth(texture(DepthSampler, texCoord).r, far);
    vec4 c0 = texture(InSampler, texCoord);
    vec4 sum = vec4(0.0);
    float wsum = 0.0;
    const float W[5] = float[](0.227, 0.194, 0.122, 0.054, 0.016);
    for (int i = -4; i <= 4; i++) {
        vec2 tc = texCoord + stepUV * float(i) * 1.5;
        vec4 s = texture(InSampler, tc);
        float z = linearDepth(texture(DepthSampler, tc).r, far);
        float w = W[abs(i)] * exp(-abs(z - z0) / (0.04 * z0 + 0.08)) * s.a;
        sum += s * w;
        wsum += w;
    }
    fragColor = wsum > 0.0001 ? vec4(sum.rgb / wsum, c0.a) : c0;
}
