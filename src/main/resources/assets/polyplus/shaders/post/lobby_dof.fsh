//? if = 1.8.9 {
/*#version 120
#define textureLod(sampler, uv, lod) texture2D(sampler, uv)
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
uniform sampler2D SoftSampler;
uniform sampler2D DepthSampler;

//? if >= 1.21.8 {
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 SoftSize;
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
//?} elif > 1.8.9 {
/*in vec2 texCoord;
out vec4 fragColor;
*///?} else {
/*varying vec2 texCoord;
*///?}

const int GATHER_TAPS = 24;
const int PROBE_TAPS = 8;
const float GOLDEN_ANGLE = 2.39996;
const float DOF_RADIUS = 11.0;
const float SKY_RADIUS = 18.0;
const float REFERENCE_HEIGHT = 1080.0;
const float COC_DEADZONE = 0.14;
const float COC_FULL = 0.85;
const float FAR_DEPTH = 1000000.0;
const float SOFT_FROM = 5.0;
const float SOFT_TO = 12.0;
const float SHARP_REACH = 0.4;
const float PULL_START = 0.15;
const float PULL_END = 0.7;
const float PULL_FAR = 2.0;
const float PULL_APERTURE = 0.3;

bool finiteColor(vec3 color) {
    return all(lessThan(abs(color), vec3(1000000.0)));
}

bool validDepth(float depth) {
    return depth >= 0.0 && depth <= 1.0;
}

float noise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

int flagMode() {
    return int(Flags.w + 0.5);
}

int depthMode() {
    return flagMode() - flagMode() / 4 * 4;
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

bool depthPresent() {
    return !isSkyDepth(textureLod(DepthSampler, vec2(0.5, 0.08), 0.0).r)
        || !isSkyDepth(textureLod(DepthSampler, vec2(0.2, 0.08), 0.0).r)
        || !isSkyDepth(textureLod(DepthSampler, vec2(0.8, 0.08), 0.0).r);
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

float behindFront(vec2 uv, float z) {
    vec3 pos = viewRay(uv) * z;
    return Wave.w - max(dot(pos.xz, Wave.xy) - Wave.z, 0.0);
}

bool masked(vec2 uv, float depth, float z) {
    return ringMode() == 2 && waveLive() && !isSkyDepth(depth) && behindFront(uv, z) <= 0.0;
}

bool skyAt(vec2 uv, float depth, float z) {
    return isSkyDepth(depth) || masked(uv, depth, z);
}

float pixelScale() {
    return max(InSize.y, 1.0) / REFERENCE_HEIGHT;
}

float dofRadius() {
    return DOF_RADIUS * pixelScale();
}

float skyRadius() {
    return SKY_RADIUS * pixelScale();
}

float focusDistance() {
    return max(Lens.x, 0.001);
}

float lensCoc(float z) {
    float focus = focusDistance();
    if (z < focus) {
        return smoothstep(0.45, 1.5, (focus - z) / focus) * Lens.y;
    }
    return smoothstep(COC_DEADZONE, COC_FULL, (z - focus) / max(z, 0.001)) * Lens.y;
}

int revealMode() {
    return int(Lens.z + 0.5);
}

float focusPullBlur(float z) {
    float focus = focusDistance() * mix(PULL_FAR, 1.0, smoothstep(PULL_START, PULL_END, Lens.w));
    float aperture = mix(PULL_APERTURE, 1.0, smoothstep(PULL_END, 1.0, Lens.w));
    return smoothstep(focus * 0.45 * aperture, focus * 1.5 * aperture, abs(z - focus)) * Lens.y;
}

float blurAmount(float z) {
    return clamp(revealMode() == 1 ? focusPullBlur(z) : lensCoc(z), 0.0, 1.0);
}

vec2 tapLens(vec2 uv) {
    float depth = textureLod(DepthSampler, uv, 0.0).r;
    if (!validDepth(depth)) {
        return vec2(0.0, FAR_DEPTH);
    }
    float z = linearDepth(depth);
    if (skyAt(uv, depth, z)) {
        return vec2(skyRadius(), FAR_DEPTH);
    }
    return vec2(blurAmount(z) * dofRadius(), z);
}

float softShare(float soften) {
    if (soften <= 0.01) {
        return 0.0;
    }
    if (soften >= 0.99) {
        return 1.0;
    }
    return soften;
}

vec3 tapColor(vec2 uv, float soften) {
    if (soften <= 0.01) {
        return textureLod(InSampler, uv, 0.0).rgb;
    }
    vec3 soft = textureLod(SoftSampler, uv, 0.0).rgb;
    if (soften >= 0.99) {
        return soft;
    }
    return mix(textureLod(InSampler, uv, 0.0).rgb, soft, soften);
}

vec3 centerColor(vec3 center, float soften) {
    if (soften <= 0.01) {
        return center;
    }
    vec3 soft = textureLod(SoftSampler, texCoord, 0.0).rgb;
    if (soften >= 0.99) {
        return soft;
    }
    return mix(center, soft, soften);
}

bool frontNearby(float centerDepth, float search) {
    float ahead = max(0.15, centerDepth * 0.03);
    for (int i = 0; i < PROBE_TAPS; i++) {
        float angle = float(i) * GOLDEN_ANGLE;
        float reach = sqrt((float(i) + 0.5) / float(PROBE_TAPS)) * search;
        vec2 lens = tapLens(texCoord + vec2(cos(angle), sin(angle)) * reach / InSize);
        if (centerDepth - lens.y > ahead * 0.5 && lens.x > 0.25 * search && lens.x + 1.0 > reach) {
            return true;
        }
    }
    return false;
}

vec4 gather(vec3 center, float centerReach, float centerDepth, float search, float seed, float behindMix) {
    float soften = smoothstep(SOFT_FROM * pixelScale(), SOFT_TO * pixelScale(), centerReach);
    vec3 back = mix(center, centerColor(center, soften), soften);
    float backWeight = 1.0;
    vec3 front = vec3(0.0);
    float frontWeight = 0.0;
    float area = search * search / float(GATHER_TAPS);
    float ahead = max(0.15, centerDepth * 0.03);
    float spin = seed * 6.28318 * (1.0 - soften);
    float jitter = mix(0.5, noise(gl_FragCoord.xy + vec2(37.0, 89.0)), 1.0 - soften);
    for (int i = 0; i < GATHER_TAPS; i++) {
        float angle = float(i) * GOLDEN_ANGLE + spin;
        float reach = sqrt((float(i) + jitter) / float(GATHER_TAPS)) * search;
        vec2 uv = texCoord + vec2(cos(angle), sin(angle)) * reach / InSize;
        vec2 lens = tapLens(uv);
        float frontness = smoothstep(ahead * 0.5, ahead * 1.5, centerDepth - lens.y);
        float share = 0.0;
        if (frontness > 0.0) {
            float covers = clamp(lens.x - reach + 1.0, 0.0, 1.0) * smoothstep(0.25 * search, 0.5 * search, lens.x);
            share = min(area / max(lens.x * lens.x, 1.0), 0.25) * covers * frontness;
        }
        float weight = clamp(min(lens.x, centerReach) - reach + 1.0, 0.0, 1.0) * (1.0 - frontness);
        if (share <= 0.0 && weight <= 0.0) {
            continue;
        }
        vec3 taken = tapColor(uv, soften);
        if (!finiteColor(taken)) {
            continue;
        }
        if (share > 0.0) {
            front += taken * share;
            frontWeight += share;
        }
        back += taken * weight;
        backWeight += weight;
    }
    vec3 result = mix(center, back / backWeight, behindMix);
    float centerShare = 1.0 - behindMix + behindMix * (1.0 - soften * softShare(soften)) / backWeight;
    if (frontWeight > 0.0) {
        float cover = clamp(frontWeight, 0.0, 1.0);
        result = mix(result, front / frontWeight, cover);
        centerShare *= 1.0 - cover;
    }
    return vec4(clamp(result - center * centerShare, 0.0, 1.0), clamp(1.0 - centerShare, 0.0, 1.0));
}

void main() {
    vec4 result = vec4(0.0);
    float depth = textureLod(DepthSampler, texCoord, 0.0).r;
    if (depthKnown() && depthPresent() && validDepth(depth)) {
        vec3 center = textureLod(InSampler, texCoord, 0.0).rgb;
        float seed = noise(gl_FragCoord.xy);
        float z = linearDepth(depth);
        if (skyAt(texCoord, depth, z)) {
            float day = 1.0 - Mood.y;
            float search = mix(dofRadius() * Lens.y, skyRadius(), day);
            if (search > 0.5 && (day > 0.01 || frontNearby(FAR_DEPTH, search))) {
                result = gather(center, search, FAR_DEPTH, search, seed, day);
            }
        } else {
            float radius = blurAmount(z) * dofRadius();
            float search = max(radius, dofRadius() * Lens.y);
            if (search > 0.5 && (radius > SHARP_REACH || frontNearby(z, search))) {
                result = gather(center, radius, z, search, seed, 1.0);
            }
        }
    }
    fragColor = result;
}
