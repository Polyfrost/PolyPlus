#version 330

//? if >= 26.3 {
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec3 viewPosition;
layout(location = 1) in vec3 viewNormal;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec3 accent;
layout(location = 4) in vec4 overlayColor;
layout(location = 5) in vec4 ambientColor;
layout(location = 6) in float height;
layout(location = 7) in float progress;
layout(location = 8) in float seed;
layout(location = 9) in float prepass;

layout(location = 0) out vec4 fragColor;
//?} else {
/*//? if >= 1.21.8 {
#moj_import <minecraft:globals.glsl>
//?} else {
/^uniform float GameTime;
^///?}

uniform sampler2D Sampler0;

in vec3 viewPosition;
in vec3 viewNormal;
in vec2 texCoord0;
in vec3 accent;
in vec4 overlayColor;
in vec4 ambientColor;
in float height;
in float progress;
in float seed;
in float prepass;

out vec4 fragColor;
*///?}

const float TAU = 6.2831853;
const float FIGURE_HEIGHT = 2.0;
const float DAY_SECONDS = 1200.0;
const float SKIN_TEXELS = 64.0;
const float LINES_PER_BLOCK = 15.0;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.1) {
        discard;
    }
    if (prepass > 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    float time = GameTime * DAY_SECONDS;
    vec3 texel = mix(overlayColor.rgb, skin.rgb, overlayColor.a);
    vec3 lit = mix(vec3(1.0), ambientColor.rgb, 0.35);
    float luma = dot(texel, vec3(0.299, 0.587, 0.114));
    vec3 body = mix(accent * (0.2 + 0.85 * luma), texel, 0.45) * lit;

    float spread = fwidth(height) * LINES_PER_BLOCK;
    float line = smoothstep(0.2, 0.8, 0.5 + 0.5 * sin(height * LINES_PER_BLOCK * TAU - time * 1.7));
    float scan = mix(mix(0.6, 1.0, line), 0.84, clamp(spread * 1.6, 0.0, 1.0));
    float sweep = pow(0.5 + 0.5 * sin(height * 1.9 - time * 0.85 + seed * TAU), 18.0);
    float rim = pow(1.0 - abs(dot(normalize(viewNormal), normalize(-viewPosition))), 2.2);
    float flutter = 0.95 + 0.05 * sin(time * 9.0 + seed * TAU) * sin(time * 3.7 + seed * 11.0);
    float dip = smoothstep(0.985, 1.0, sin(time * 1.3 + seed * TAU)) * 0.22;

    float daylight = clamp(dot(ambientColor.rgb, vec3(0.333)), 0.0, 1.0);
    float restAlpha = clamp((0.6 * scan + 0.3 * rim + 0.24 * sweep) * mix(0.9, 1.3, daylight) * (flutter - dip), 0.0, 1.0);
    vec3 rest = (body + accent * (0.42 * rim + 0.5 * sweep)) * mix(0.8, 1.08, scan);

    float grain = (hash(floor(texCoord0 * SKIN_TEXELS) + seed * 7.0) - 0.5) * 0.2;
    float rise = clamp(height / FIGURE_HEIGHT, 0.0, 1.0) + grain;
    vec3 charged = mix(texel * lit, accent, 0.62) + accent * 0.35;
    float front;
    float cover;
    float alpha;
    if (progress < 0.5) {
        front = progress * 2.0 * 1.3 - 0.15;
        cover = step(rise, front);
        alpha = cover;
    } else {
        front = (progress - 0.5) * 2.0 * 1.3 - 0.15;
        cover = step(front, rise);
        alpha = mix(restAlpha, 1.0, cover);
    }
    float moving = step(0.001, progress) * (1.0 - step(0.999, progress));
    float seam = smoothstep(0.09, 0.0, abs(rise - front)) * moving;
    vec3 color = mix(rest, charged, cover) + accent * seam * 0.9;
    fragColor = vec4(color, max(alpha, seam * 0.9));
}
