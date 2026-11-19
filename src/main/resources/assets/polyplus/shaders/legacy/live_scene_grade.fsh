#version 120

uniform sampler2D InSampler;
uniform vec4 ColorModulator;

varying vec2 texCoord;

void main() {
    vec4 source = texture2D(InSampler, texCoord);
    vec3 color = source.rgb;
    float night = ColorModulator.x;
    float exposure = ColorModulator.y;
    float golden = ColorModulator.z;
    float bright = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float warmth = clamp((color.r - color.b) * 3.5 + bright * bright * 0.5, 0.0, 1.0);
    vec3 graded = mix(color * vec3(0.74, 0.86, 1.14), color * vec3(1.12, 0.97, 0.76), warmth);
    vec3 warm = color * mix(vec3(1.0), vec3(1.1, 0.98, 0.82), golden);
    color = mix(warm, graded, night);
    color = 1.0 - exp(-color * exposure);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.16);
    color = mix(color, color * color * (3.0 - 2.0 * color), 0.3);
    color += vec3(0.012, 0.0, 0.036) * (1.0 - clamp(luma * 2.2, 0.0, 1.0));
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), source.a * ColorModulator.w);
}
