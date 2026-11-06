#version 330

//? if >= 1.21.4 {
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
//?} else {
/*#moj_import <light.glsl>
#moj_import <fog.glsl>
*///?}
//? if >= 1.21.8 {
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
//?}

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

//? if < 1.21.8 {
/*uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
*///?}

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

vec4 mixFaceLight(vec2 light, vec4 color) {
    vec2 lightValue = max(vec2(0.0), light);
    float lightAccum = min(1.0, (lightValue.x + lightValue.y) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    return vec4(color.rgb * lightAccum, color.a);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    //? if >= 1.21.8 {
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    //?} else {
    /*sphericalVertexDistance = fog_distance(Position, FogShape);
    cylindricalVertexDistance = sphericalVertexDistance;
    *///?}

    vec2 light = vec2(dot(Light0_Direction, Normal), dot(Light1_Direction, Normal));
    vertexPerFaceColorBack = mixFaceLight(-light, Color);
    vertexPerFaceColorFront = mixFaceLight(light, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
