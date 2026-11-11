//? if = 1.8.9 {
/*#version 120
#define texture texture2D
#define fragColor gl_FragColor
*///?} else {
#version 330
//?}
//? if >= 26.3
#extension GL_ARB_separate_shader_objects : require
//? if < 1.21.4 {
/*#define InSampler DiffuseSampler
*///?}

uniform sampler2D InSampler;

//? if >= 1.21.8 {
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LobbyBlur {
    vec2 Dir;
    float Threshold;
};
//?} else {
/*uniform vec2 OutSize;
uniform vec2 InSize;
uniform vec2 Dir;
uniform float Threshold;
*///?}

//? if >= 26.3 {
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
//?} elif > 1.8.9 {
/*in vec2 texCoord;
out vec4 fragColor;
*///?} else {
/*varying vec2 texCoord;
*///?}

vec3 fetch(vec2 uv) {
    vec3 c = texture(InSampler, uv).rgb;
    if (!all(lessThan(abs(c), vec3(1000000.0)))) {
        return vec3(0.0);
    }
    if (Threshold <= 0.0) {
        return c;
    }
    float peak = max(c.r, max(c.g, c.b));
    float excess = clamp((peak - Threshold) / (1.0 - Threshold), 0.0, 1.0);
    float chroma = (peak - min(c.r, min(c.g, c.b))) / max(peak, 0.001);
    return c * excess * excess * mix(0.35, 1.0, chroma);
}

void main() {
    vec2 offset = Dir / OutSize;
    vec3 sum = fetch(texCoord) * 0.2;
    sum += (fetch(texCoord + offset * 1.4) + fetch(texCoord - offset * 1.4)) * 0.18;
    sum += (fetch(texCoord + offset * 3.3) + fetch(texCoord - offset * 3.3)) * 0.14;
    sum += (fetch(texCoord + offset * 5.2) + fetch(texCoord - offset * 5.2)) * 0.08;
    fragColor = vec4(sum, 1.0);
}
