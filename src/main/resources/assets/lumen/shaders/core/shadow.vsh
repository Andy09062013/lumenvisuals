#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>

// Lumen shadow map: draws chunk geometry from the sun. ModelViewMat holds the sun's projection.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec2 texCoord0;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ModelViewMat * vec4(pos, 1.0);
    texCoord0 = UV0;
}
