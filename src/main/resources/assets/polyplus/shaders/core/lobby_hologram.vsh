#version 330

//? if >= 26.3 {
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out vec3 viewPosition;
layout(location = 1) out vec3 viewNormal;
layout(location = 2) out vec2 texCoord0;
layout(location = 3) out vec3 accent;
layout(location = 4) out vec4 overlayColor;
layout(location = 5) out vec4 ambientColor;
layout(location = 6) out float height;
layout(location = 7) out float progress;
layout(location = 8) out float seed;
layout(location = 9) out float prepass;
//?} else {
/*//? if >= 1.21.8 {
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
//?}

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

//? if < 1.21.8 {
/^uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
^///?}

out vec3 viewPosition;
out vec3 viewNormal;
out vec2 texCoord0;
out vec3 accent;
out vec4 overlayColor;
out vec4 ambientColor;
out float height;
out float progress;
out float seed;
out float prepass;
*///?}

void main() {
    vec4 eye = ModelViewMat * vec4(Position + Normal * 0.006, 1.0);
    gl_Position = ProjMat * eye;
    viewPosition = eye.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    texCoord0 = UV0;
    accent = vec3((UV2.y >> 10) & 31, (UV2.y >> 5) & 31, UV2.y & 31) / 31.0;
    overlayColor = texelFetch(Sampler1, UV1, 0);
    ambientColor = texelFetch(Sampler2, ivec2(15, 15), 0);
    height = Position.y;
    progress = float(UV2.x & 1023) / 1023.0;
    seed = float((UV2.x >> 10) & 15) / 15.0;
    prepass = float((UV2.x >> 14) & 1);
}
