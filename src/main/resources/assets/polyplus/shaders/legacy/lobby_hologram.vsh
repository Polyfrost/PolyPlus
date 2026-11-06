#version 120

uniform vec3 FeetEye;
uniform vec3 UpEye;

varying vec3 viewPosition;
varying vec3 viewNormal;
varying vec2 texCoord0;
varying float height;

void main() {
    vec4 eye = gl_ModelViewMatrix * vec4(gl_Vertex.xyz + gl_Normal * 0.006, gl_Vertex.w);
    gl_Position = gl_ProjectionMatrix * eye;
    viewPosition = eye.xyz;
    viewNormal = gl_NormalMatrix * gl_Normal;
    texCoord0 = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    height = dot(eye.xyz - FeetEye, UpEye);
}
