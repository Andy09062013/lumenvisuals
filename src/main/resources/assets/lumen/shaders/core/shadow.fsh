#version 330

// Lumen shadow map: only depth matters; see-through texture pixels (leaf holes, grass) let light through.

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.5) {
        discard;
    }
    fragColor = vec4(1.0);
}
