#version 120

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform float Seconds;

varying vec4 vertexColor;
varying vec2 texCoord0;
varying vec2 lightCoord;
varying float waveFront;
varying float fogDistance;

const float RING_GAIN = 0.6;
const float RING_WIDTH = 7.0;
const float RING_RAMP = 28.0;
const float WAVE_SPEED = 28.0;

void main() {
    if (waveFront <= 0.0) {
        discard;
    }
    vec4 color = texture2D(Sampler0, texCoord0) * vertexColor * texture2D(Sampler2, lightCoord);
    if (waveFront < RING_WIDTH) {
        float ring = 1.0 - clamp(waveFront / RING_WIDTH, 0.0, 1.0);
        ring *= smoothstep(RING_WIDTH, RING_WIDTH + RING_RAMP, Seconds * WAVE_SPEED);
        vec3 tint = gl_Fog.color.rgb / max(max(gl_Fog.color.r, max(gl_Fog.color.g, gl_Fog.color.b)), 0.05);
        color.rgb += mix(tint, vec3(1.0), ring) * RING_GAIN * ring * ring;
    }
    float fogValue = clamp((fogDistance - gl_Fog.start) * gl_Fog.scale, 0.0, 1.0);
    gl_FragColor = vec4(mix(color.rgb, gl_Fog.color.rgb, fogValue), color.a);
}
