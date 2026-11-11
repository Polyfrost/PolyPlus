//? if = 1.8.9 {
/*#version 120
*///?} else {
#version 330
//?}
//? if >= 26.3 {
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}
//?} elif >= 1.21.10 {
/*out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vec4 pos = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);

    gl_Position = pos;
    texCoord = uv;
}
*///?} elif >= 1.21.5 {
/*in vec4 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.2, 1.0);
    texCoord = Position.xy;
}
*///?} elif > 1.8.9 {
/*in vec4 Position;

uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    texCoord = Position.xy / OutSize;
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.2, 1.0);
}
*///?} else {
/*uniform vec2 OutSize;

varying vec2 texCoord;

void main() {
    texCoord = gl_Vertex.xy / OutSize;
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.2, 1.0);
}
*///?}
