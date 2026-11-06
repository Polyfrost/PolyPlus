#version 330
//? if >= 26.3
#extension GL_ARB_separate_shader_objects : require
//? if < 1.21.4 {
/*#define InSampler DiffuseSampler
*///?}

uniform sampler2D InSampler;

//? if >= 26.3 {
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
//?} else {
/*in vec2 texCoord;
out vec4 fragColor;
*///?}

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
