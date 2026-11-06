#version 330

//? if >= 1.21.4 {
#moj_import <minecraft:fog.glsl>
//?} else {
/*#moj_import <fog.glsl>
*///?}
//? if >= 1.21.8 {
#moj_import <minecraft:dynamictransforms.glsl>
//?} else {
/*uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
*///?}

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    //? if >= 1.21.8 {
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    //?} else {
    /*fragColor = linear_fog(color, sphericalVertexDistance, FogStart, FogEnd, FogColor);
    *///?}
}
