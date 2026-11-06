#version 120

uniform sampler2D Sampler0;
uniform float Seconds;
uniform vec3 Accent;
uniform float Progress;
uniform float Seed;
uniform float Prepass;
uniform float Daylight;

varying vec3 viewPosition;
varying vec3 viewNormal;
varying vec2 texCoord0;
varying float height;

const float TAU = 6.2831853;
const float FIGURE_HEIGHT = 2.0;
const float SKIN_TEXELS = 64.0;
const float LINES_PER_BLOCK = 15.0;

float hash(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 skin = texture2D(Sampler0, texCoord0);
    if (skin.a < 0.1) {
        discard;
    }
    if (Prepass > 0.5) {
        gl_FragColor = vec4(0.0);
        return;
    }

    float time = Seconds;
    vec3 texel = skin.rgb;
    float luma = dot(texel, vec3(0.299, 0.587, 0.114));
    vec3 body = mix(Accent * (0.2 + 0.85 * luma), texel, 0.45) * mix(0.75, 1.0, Daylight);

    float line = smoothstep(0.2, 0.8, 0.5 + 0.5 * sin(height * LINES_PER_BLOCK * TAU - time * 1.7));
    float scan = mix(0.6, 1.0, line);
    float sweep = pow(0.5 + 0.5 * sin(height * 1.9 - time * 0.85 + Seed * TAU), 18.0);
    float rim = pow(1.0 - abs(dot(normalize(viewNormal), normalize(-viewPosition))), 2.2);
    float flutter = 0.95 + 0.05 * sin(time * 9.0 + Seed * TAU) * sin(time * 3.7 + Seed * 11.0);
    float dip = smoothstep(0.985, 1.0, sin(time * 1.3 + Seed * TAU)) * 0.22;

    float restAlpha = clamp((0.6 * scan + 0.3 * rim + 0.24 * sweep) * mix(0.9, 1.3, Daylight) * (flutter - dip), 0.0, 1.0);
    vec3 rest = (body + Accent * (0.42 * rim + 0.5 * sweep)) * mix(0.8, 1.08, scan);

    float grain = (hash(floor(texCoord0 * SKIN_TEXELS) + Seed * 7.0) - 0.5) * 0.2;
    float rise = clamp(height / FIGURE_HEIGHT, 0.0, 1.0) + grain;
    vec3 charged = mix(texel, Accent, 0.62) + Accent * 0.35;
    float front;
    float cover;
    float alpha;
    if (Progress < 0.5) {
        front = Progress * 2.0 * 1.3 - 0.15;
        cover = step(rise, front);
        alpha = cover;
    } else {
        front = (Progress - 0.5) * 2.0 * 1.3 - 0.15;
        cover = step(front, rise);
        alpha = mix(restAlpha, 1.0, cover);
    }
    float moving = step(0.001, Progress) * (1.0 - step(0.999, Progress));
    float seam = smoothstep(0.09, 0.0, abs(rise - front)) * moving;
    vec3 color = mix(rest, charged, cover) + Accent * seam * 0.9;
    gl_FragColor = vec4(color, max(alpha, seam * 0.9));
}
