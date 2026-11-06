#version 330
//? if < 1.21.4 {
/*#define ALPHA_CUTOUT LobbyCutout
*///?}
//? if >= 26.3 {
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in float waveFront;

layout(location = 0) out vec4 fragColor;
//?} elif >= 1.21.8 {
/*#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
*///?} else {
/*uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
*///?}
//? if < 1.21.4 {
/*uniform float LobbyCutout;
*///?}
//? if >= 1.21.11 && < 26.3 {
/*#moj_import <minecraft:chunksection.glsl>
*///?} elif < 1.21.11 && >= 1.21.8 {
/*#moj_import <minecraft:dynamictransforms.glsl>
*///?}
//? if < 26.3 {
/*
uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float chunkVisibility;
in float waveFront;

out vec4 fragColor;
*///?}
//? if >= 1.21.11 && < 26.3 {
/*
vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);
    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);
    float minPixelSize = min(pixelSize.x, pixelSize.y);
    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);
    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);
    float effectiveDerivative = sqrt(minDerivative * maxDerivative);
    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));
    float mipLevelLow = floor(mipLevelExact);
    float mipLevelHigh = mipLevelLow + 1.0;
    float mipBlend = fract(mipLevelExact);
    const vec2 offsets[4] = vec2[](
        vec2(0.125, 0.375),
        vec2(-0.125, -0.375),
        vec2(0.375, -0.125),
        vec2(-0.375, 0.125)
    );
    vec4 rgssColorLow = vec4(0.0);
    vec4 rgssColorHigh = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColorLow += textureLod(source, sampleUV, mipLevelLow);
        rgssColorHigh += textureLod(source, sampleUV, mipLevelHigh);
    }
    rgssColorLow *= 0.25;
    rgssColorHigh *= 0.25;
    vec4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);
    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
    return mix(nearestColor, rgssColor, blendFactor);
}
*///?}

const float RING_GAIN = 0.6;
const float RING_WIDTH = 7.0;
const float RING_RAMP = 28.0;
const float SECONDS_PER_DAY = 1200.0;
const float WAVE_SPEED = 28.0;

void main() {
    if (waveFront <= 0.0) {
        discard;
    }
    //? if >= 1.21.11 {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    //?} else {
    /*vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    *///?}
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    float ring = 1.0 - clamp(waveFront / RING_WIDTH, 0.0, 1.0);
    ring *= smoothstep(RING_WIDTH, RING_WIDTH + RING_RAMP, GameTime * SECONDS_PER_DAY * WAVE_SPEED);
    vec3 tint = FogColor.rgb / max(max(FogColor.r, max(FogColor.g, FogColor.b)), 0.05);
    color.rgb += mix(tint, vec3(1.0), ring) * RING_GAIN * ring * ring;
    //? if >= 1.21.8 {
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    //?} else {
    /*float fogValue = clamp((cylindricalVertexDistance - FogStart) / (FogEnd - FogStart), 0.0, 1.0);
    fragColor = vec4(mix(color.rgb, FogColor.rgb, fogValue * FogColor.a), color.a);
    *///?}
}
