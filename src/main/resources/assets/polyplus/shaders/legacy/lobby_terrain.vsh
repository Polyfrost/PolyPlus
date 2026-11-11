#version 120

uniform float Seconds;
uniform float AimDistance;
uniform vec3 Right;
uniform vec3 Up;
uniform vec3 Forward;

varying vec4 vertexColor;
varying vec2 texCoord0;
varying vec2 lightCoord;
varying float waveFront;
varying float fogDistance;

const float WAVE_SPEED = 28.0;
const float WAVE_WIDTH = 10.0;
const float WAVE_DEPTH = 4.0;
const float WAVE_CREST = 0.5;

void main() {
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    vec3 pos = Right * eye.x + Up * eye.y - Forward * eye.z;
    vec3 aim = Forward * AimDistance;
    float level = length(Forward.xz) + 1e-4;
    vec2 across = Forward.xz / level;
    float aimAlong = level * AimDistance;
    float dip = atan(-Forward.y, level) + atan(1.0 / gl_ProjectionMatrix[1][1]);
    float start = dip <= 0.0 ? aimAlong : (dip >= 1.5707 ? 0.0 : clamp(-aim.y / tan(dip), 0.0, aimAlong));
    float fromAim = max(dot(pos.xz, across) - start, 0.0);
    float behindFront = Seconds * WAVE_SPEED - fromAim;
    float risen = clamp(behindFront / WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * WAVE_CREST;
    float lift = (eased - 1.0) * WAVE_DEPTH + crest;
    waveFront = behindFront;
    eye.xyz += vec3(Right.y, Up.y, -Forward.y) * lift;
    gl_Position = gl_ProjectionMatrix * eye;
    fogDistance = length(pos.xz);
    vertexColor = gl_Color;
    texCoord0 = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    lightCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
