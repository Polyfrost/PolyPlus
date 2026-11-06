#version 330
//? if >= 26.3
#extension GL_ARB_separate_shader_objects : require
//? if < 1.21.4 {
/*#define InSampler DiffuseSampler
*///?}

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

//? if >= 1.21.8 {
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

layout(std140) uniform LobbySky {
    vec4 Forward;
    vec4 Right;
    vec4 Up;
    vec4 Params;
    vec4 Mood;
    vec4 Lens;
    vec4 Wave;
    vec4 Flags;
};
//?} else {
/*uniform vec2 OutSize;
uniform vec2 InSize;
uniform vec2 DepthSize;
uniform vec4 Forward;
uniform vec4 Right;
uniform vec4 Up;
uniform vec4 Params;
uniform vec4 Mood;
uniform vec4 Lens;
uniform vec4 Wave;
uniform vec4 Flags;
*///?}

//? if >= 26.3 {
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
//?} else {
/*in vec2 texCoord;
out vec4 fragColor;
*///?}

const float WASH_STRENGTH = 0.55;
const float AURORA_STRENGTH = 0.72;
const float AURORA_SATURATION = 0.72;
const float RING_GAIN = 0.6;
const float RING_WIDTH = 7.0;
const float RING_RAMP = 28.0;

int flagMode() {
    return int(Flags.w + 0.5);
}

int depthMode() {
    return flagMode() % 4;
}

int ringMode() {
    return flagMode() / 4;
}

bool depthKnown() {
    return depthMode() != 3;
}

bool isSkyDepth(float depth) {
    if (depthMode() == 1) {
        return depth >= 0.999999;
    }
    if (depthMode() == 2) {
        return depth <= 0.000001;
    }
    if (depthMode() == 3) {
        return false;
    }
    //? if >= 26.2 {
    return depth <= 0.000001;
    //?} else {
    /*return depth >= 0.999999;
    *///?}
}

float linearDepth(float depth) {
    float near = Params.z;
    float far = Params.w;
    //? if >= 26.2 {
    return near * far / (depth * (far - near) + near);
    //?} else {
    /*return near * far / (far - depth * (far - near));
    *///?}
}

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    return Forward.xyz + Right.xyz * (ndc.x * Forward.w * Right.w) + Up.xyz * (ndc.y * Forward.w);
}

bool waveLive() {
    return ringMode() > 0 && dot(Wave.xy, Wave.xy) > 0.5;
}

float behindFront(vec2 uv, float depth) {
    vec3 pos = viewRay(uv) * linearDepth(depth);
    return Wave.w - max(dot(pos.xz, Wave.xy) - Wave.z, 0.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float ribbon(vec2 p, float t) {
    vec2 warp = vec2(noise(p * 0.45 + vec2(t * 0.07, 3.1)), noise(p * 0.45 - vec2(1.7, t * 0.06)));
    vec2 q = p + warp * 3.4;
    float wave = noise(q * vec2(0.55, 0.22) + vec2(0.0, t * 0.09));
    float ridge = 1.0 - abs(2.0 * wave - 1.0);
    float fine = noise(q * vec2(5.5, 0.6) + vec2(t * 0.35, 0.0));
    return (pow(ridge, 9.0) * 1.7 + pow(ridge, 3.0) * 0.3) * (0.55 + 0.75 * fine);
}

vec3 aurora(vec3 dir, float t, float jitter) {
    vec3 total = vec3(0.0);
    const int STEPS = 26;
    for (int i = 0; i < STEPS; i++) {
        float f = (float(i) + jitter) / float(STEPS);
        float height = 0.9 + f * f * 2.1;
        vec2 p = dir.xz / (dir.y + 0.16) * height * 0.95;
        float band = ribbon(p, t);
        vec3 tint = mix(vec3(0.08, 1.0, 0.52), vec3(0.16, 0.5, 1.0), smoothstep(0.1, 0.55, f));
        tint = mix(tint, vec3(0.92, 0.16, 0.86), smoothstep(0.5, 1.0, f));
        total += band * tint * pow(1.0 - f, 1.6);
    }
    return total * 0.3 * smoothstep(0.01, 0.12, dir.y);
}

vec3 auroraWash(vec3 dir, float t) {
    vec3 total = vec3(0.0);
    const int STEPS = 6;
    for (int i = 0; i < STEPS; i++) {
        float f = (float(i) + 0.5) / float(STEPS);
        vec2 p = dir.xz / (dir.y + 0.3) * (1.1 + f * 1.5) * 0.95;
        vec3 tint = mix(vec3(0.08, 1.0, 0.52), vec3(0.16, 0.5, 1.0), smoothstep(0.1, 0.55, f));
        total += ribbon(p, t) * tint * (1.0 - f);
    }
    return total * 0.6;
}

vec3 ringGlow(float front) {
    float ring = 1.0 - clamp(front / RING_WIDTH, 0.0, 1.0);
    ring *= smoothstep(RING_WIDTH, RING_WIDTH + RING_RAMP, Wave.w);
    vec3 tint = Flags.rgb / max(max(Flags.r, max(Flags.g, Flags.b)), 0.05);
    return mix(tint, vec3(1.0), ring) * RING_GAIN * ring * ring;
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 dir = normalize(Forward.xyz + Right.xyz * ndc.x * Forward.w * Right.w + Up.xyz * ndc.y * Forward.w);
    if (!depthKnown() || !(depth >= 0.0 && depth <= 1.0)) {
        fragColor = vec4(color, 1.0);
        return;
    }
    bool sky = isSkyDepth(depth);
    if (!sky && waveLive()) {
        float front = behindFront(texCoord, depth);
        if (ringMode() == 2 && front <= 0.0) {
            color = Flags.rgb;
            sky = true;
        } else if (front > 0.0) {
            color += ringGlow(front);
        }
    }
    if (sky) {
        if (dir.y > 0.0 && Mood.x > 0.01) {
            vec3 glow = aurora(dir, Up.w, hash(texCoord + fract(Up.w)));
            float glowLuma = dot(glow, vec3(0.2126, 0.7152, 0.0722));
            color += mix(vec3(glowLuma), glow, AURORA_SATURATION) * AURORA_STRENGTH * Mood.x;
        }
    } else if (Mood.x > 0.01) {
        vec3 mirrored = normalize(vec3(dir.x, abs(dir.y) * 0.9 + 0.08, dir.z));
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        vec3 wash = auroraWash(mirrored, Up.w);
        float washLuma = dot(wash, vec3(0.2126, 0.7152, 0.0722));
        color += mix(vec3(washLuma), wash, AURORA_SATURATION) * luma * luma * WASH_STRENGTH * Mood.x;
    }
    fragColor = vec4(color, 1.0);
}
