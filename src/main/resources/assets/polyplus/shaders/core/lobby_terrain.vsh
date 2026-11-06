#version 330
//? if < 1.21.4 {
/*#define ModelOffset ChunkOffset
*///?}
//? if >= 26.3 {
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out float waveFront;
//?} elif >= 1.21.8 {
/*#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>
*///?} elif >= 1.21.4 {
/*#moj_import <minecraft:fog.glsl>
*///?} else {
/*#moj_import <fog.glsl>
*///?}
//? if < 1.21.8 {
/*
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ModelOffset;
uniform float GameTime;
uniform float LobbyAim;
*///?}
//? if >= 1.21.11 && < 26.3 {
/*#moj_import <minecraft:chunksection.glsl>
*///?} elif < 1.21.11 && >= 1.21.8 {
/*#moj_import <minecraft:dynamictransforms.glsl>
*///?}
//? if >= 26.1 && < 26.3 {
/*#moj_import <minecraft:sample_lightmap.glsl>
*///?}
//? if < 26.3 {
/*
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float chunkVisibility;
out float waveFront;
*///?}
//? if >= 1.21.8 {
#define AIM_DISTANCE FogEnvironmentalEnd
//?} else {
/*#define AIM_DISTANCE LobbyAim
*///?}
//? if < 26.1 {
/*
vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}
*///?}

const float SECONDS_PER_DAY = 1200.0;
const float WAVE_SPEED = 28.0;
const float WAVE_WIDTH = 10.0;
const float WAVE_DEPTH = 4.0;
const float WAVE_CREST = 0.5;

void main() {
    //? if >= 1.21.11 {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    //?} else {
    /*vec3 pos = Position + ModelOffset;
    *///?}

    float seconds = GameTime * SECONDS_PER_DAY;
    float distanceToAim = AIM_DISTANCE;
    vec3 forward = -vec3(ModelViewMat[0][2], ModelViewMat[1][2], ModelViewMat[2][2]);
    vec3 aim = forward * distanceToAim;
    float level = length(forward.xz) + 1e-4;
    vec2 across = forward.xz / level;
    float aimAlong = level * distanceToAim;
    float dip = atan(-forward.y, level) + atan(1.0 / ProjMat[1][1]);
    float start = dip <= 0.0 ? aimAlong : (dip >= 1.5707 ? 0.0 : clamp(-aim.y / tan(dip), 0.0, aimAlong));
    float fromAim = max(dot(pos.xz, across) - start, 0.0);
    float behindFront = seconds * WAVE_SPEED - fromAim;
    float risen = clamp(behindFront / WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * WAVE_CREST;
    pos.y += (eased - 1.0) * WAVE_DEPTH + crest;
    waveFront = behindFront;

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    //? if >= 1.21.8 {
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    //?} else {
    /*sphericalVertexDistance = fog_distance(pos, 0);
    cylindricalVertexDistance = fog_distance(pos, 1);
    *///?}
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    //? if >= 1.21.11 {
    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
    //?} else {
    /*chunkVisibility = 1.0;
    *///?}
}
