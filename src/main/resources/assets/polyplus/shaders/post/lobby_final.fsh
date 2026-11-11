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
uniform sampler2D BloomSampler;
uniform sampler2D DofSampler;
uniform sampler2D FrostSampler;
uniform sampler2D DepthSampler;

//? if >= 1.21.8 {
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 BloomSize;
    vec2 DofSize;
    vec2 FrostSize;
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

layout(std140) uniform LobbyGlass {
    vec4 Glass;
    vec4 Panels[12];
    vec4 Radii[3];
};

vec4 panelAt(int index) {
    return Panels[index];
}

float radiusAt(int index) {
    return Radii[index / 4][index % 4];
}
//?} else {
/*uniform vec2 OutSize;
uniform vec2 InSize;
uniform vec2 BloomSize;
uniform vec2 DofSize;
uniform vec2 FrostSize;
uniform vec2 DepthSize;
uniform vec4 Forward;
uniform vec4 Right;
uniform vec4 Up;
uniform vec4 Params;
uniform vec4 Mood;
uniform vec4 Lens;
uniform vec4 Wave;
uniform vec4 Flags;
uniform vec4 Glass;
uniform vec4 Panel0;
uniform vec4 Panel1;
uniform vec4 Panel2;
uniform vec4 Panel3;
uniform vec4 Panel4;
uniform vec4 Panel5;
uniform vec4 Panel6;
uniform vec4 Panel7;
uniform vec4 Panel8;
uniform vec4 Panel9;
uniform vec4 Panel10;
uniform vec4 Panel11;
uniform vec4 Radius0;
uniform vec4 Radius1;
uniform vec4 Radius2;

vec4 panelAt(int index) {
    vec4 panels[12] = vec4[12](Panel0, Panel1, Panel2, Panel3, Panel4, Panel5, Panel6, Panel7, Panel8, Panel9, Panel10, Panel11);
    return panels[index];
}

float radiusAt(int index) {
    vec4 radii[3] = vec4[3](Radius0, Radius1, Radius2);
    return radii[index / 4][index - index / 4 * 4];
}
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

const float DOF_RADIUS = 11.0;
const float REFERENCE_HEIGHT = 1080.0;
const float COC_DEADZONE = 0.14;
const float COC_FULL = 0.85;
const float FAR_DEPTH = 1000000.0;
const float DEPTH_SOFTNESS = 0.03;
const float EDGE_FLOOR = 0.3;
const float OWN_FROM = 0.5;
const float OWN_TO = 2.0;
const float DITHER = 1.3 / 255.0;

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
    return max(OutSize.y, 1.0) / REFERENCE_HEIGHT;
}

float dofRadius() {
    return DOF_RADIUS * pixelScale();
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

float panelCoverage(int index, vec2 pixel) {
    vec4 panel = panelAt(index);
    float radius = radiusAt(index) * OutSize.y;
    vec2 centre = vec2(panel.x, 1.0 - panel.y) * OutSize;
    vec2 half_size = panel.zw * OutSize;
    vec2 outside = abs(pixel - centre) - half_size;
    if (max(outside.x, outside.y) > 1.0) {
        return 0.0;
    }
    vec2 corner = outside + radius;
    float distance = length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
    return clamp(0.5 - distance, 0.0, 1.0);
}

float frostAmount() {
    if (Glass.y == 0.0) {
        return 0.0;
    }
    float amount = Glass.x;
    vec2 pixel = texCoord * OutSize;
    int count = int(Glass.z);
    for (int index = 0; index < count; index++) {
        if (amount >= 1.0) {
            break;
        }
        amount = max(amount, panelCoverage(index, pixel));
    }
    return amount * Glass.y;
}

const float REVEAL_FADE = 0.22;
const float REVEAL_FLOOR = 0.35;
const float PULL_START = 0.15;
const float PULL_END = 0.7;
const float PULL_FAR = 2.0;
const float PULL_APERTURE = 0.3;
const float LIGHT_START = 0.04;
const float LIGHT_END = 0.85;
const float LIGHT_REACH = 220.0;
const float LIGHT_SOFTNESS = 14.0;
const float LIGHT_EDGE = 6.0;
const float LIGHT_FLARE = 0.8;
const vec3 LIGHT_DARK = vec3(0.22, 0.26, 0.4);
const vec3 LIGHT_WARMTH = vec3(1.0, 0.8, 0.55);

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

float sceneDepth(vec2 uv) {
    float depth = textureLod(DepthSampler, uv, 0.0).r;
    if (!validDepth(depth)) {
        return FAR_DEPTH;
    }
    float z = linearDepth(depth);
    if (skyAt(uv, depth, z)) {
        return FAR_DEPTH;
    }
    return z;
}

vec2 dofSize() {
    return max(DofSize, vec2(1.0));
}

float depthWeight(float a, float b) {
    float difference = abs(a - b) / (max(min(a, b), 0.001) * DEPTH_SOFTNESS);
    return 1.0 / (1.0 + difference * difference);
}

vec4 jointUpsample(float z, float relax) {
    vec2 grid = texCoord * dofSize() - 0.5;
    vec2 base = floor(grid);
    vec2 f = grid - base;
    vec2 uv00 = (base + 0.5) / dofSize();
    vec2 uv10 = (base + vec2(1.5, 0.5)) / dofSize();
    vec2 uv01 = (base + vec2(0.5, 1.5)) / dofSize();
    vec2 uv11 = (base + 1.5) / dofSize();
    vec4 dof00 = textureLod(DofSampler, uv00, 0.0);
    vec4 dof10 = textureLod(DofSampler, uv10, 0.0);
    vec4 dof01 = textureLod(DofSampler, uv01, 0.0);
    vec4 dof11 = textureLod(DofSampler, uv11, 0.0);
    if (dof00 + dof10 + dof01 + dof11 == vec4(0.0)) {
        return vec4(0.0);
    }
    float w00 = (1.0 - f.x) * (1.0 - f.y) * mix(depthWeight(z, sceneDepth(uv00)), 1.0, relax);
    float w10 = f.x * (1.0 - f.y) * mix(depthWeight(z, sceneDepth(uv10)), 1.0, relax);
    float w01 = (1.0 - f.x) * f.y * mix(depthWeight(z, sceneDepth(uv01)), 1.0, relax);
    float w11 = f.x * f.y * mix(depthWeight(z, sceneDepth(uv11)), 1.0, relax);
    vec4 sum = dof00 * w00 + dof10 * w10 + dof01 * w01 + dof11 * w11;
    return sum / max(w00 + w10 + w01 + w11, EDGE_FLOOR);
}

vec4 tentUpsample(float z) {
    //? if = 1.8.9 {
    /*return jointUpsample(z, 1.0);
    *///?} else {
    vec2 half_texel = 0.5 / dofSize();
    vec4 sum = textureLod(DofSampler, texCoord + vec2(-half_texel.x, -half_texel.y), 0.0);
    sum += textureLod(DofSampler, texCoord + vec2(half_texel.x, -half_texel.y), 0.0);
    sum += textureLod(DofSampler, texCoord + vec2(-half_texel.x, half_texel.y), 0.0);
    sum += textureLod(DofSampler, texCoord + vec2(half_texel.x, half_texel.y), 0.0);
    return sum * 0.25;
    //?}
}

vec4 depthOfField(float z, float own) {
    if (own >= 0.99) {
        return tentUpsample(z);
    }
    vec4 sharp = jointUpsample(z, own);
    if (own <= 0.01) {
        return sharp;
    }
    return mix(sharp, tentUpsample(z), own);
}

vec3 firstLightGain(float depth) {
    float progress = clamp((Lens.w - LIGHT_START) / (LIGHT_END - LIGHT_START), 0.0, 1.0);
    float radius = LIGHT_REACH * progress * progress;
    float z = linearDepth(depth);
    if (!depthKnown() || !depthPresent() || !validDepth(depth) || skyAt(texCoord, depth, z)) {
        return mix(LIGHT_DARK, vec3(1.0), smoothstep(70.0, 125.0, radius));
    }
    vec3 ray = viewRay(texCoord);
    float reach = length(ray * z - Forward.xyz * Lens.x);
    float lit = 1.0 - smoothstep(radius - LIGHT_SOFTNESS, radius, reach);
    float front = (reach - radius + LIGHT_SOFTNESS * 0.5) / LIGHT_EDGE;
    float flare = exp(-front * front) * (1.0 - progress);
    return mix(LIGHT_DARK, vec3(1.0), lit) * (1.0 + LIGHT_WARMTH * (LIGHT_FLARE * flare));
}

vec3 scene() {
    vec3 color = textureLod(InSampler, texCoord, 0.0).rgb;
    float depth = textureLod(DepthSampler, texCoord, 0.0).r;
    float blur = 0.0;
    if (depthKnown() && depthPresent() && validDepth(depth)) {
        float z = linearDepth(depth);
        bool sky = skyAt(texCoord, depth, z);
        if (!sky) {
            blur = blurAmount(z);
        }
        float texel = max(OutSize.y, 1.0) / dofSize().y;
        float own = sky ? 1.0 - Mood.y : smoothstep(OWN_FROM * texel, OWN_TO * texel, blur * dofRadius());
        vec4 dof = depthOfField(sky ? FAR_DEPTH : z, own);
        color = color * (1.0 - dof.a) + dof.rgb;
    }
    if (revealMode() == 2) {
        color *= firstLightGain(depth);
    }
    return color + textureLod(BloomSampler, texCoord, 0.0).rgb * mix(0.08, 0.6, Mood.y) * (1.0 - 0.5 * blur);
}

void main() {
    float frost = frostAmount();
    vec3 color = frost >= 1.0 ? textureLod(FrostSampler, texCoord, 0.0).rgb : scene();
    if (frost > 0.0 && frost < 1.0) {
        color = mix(color, textureLod(FrostSampler, texCoord, 0.0).rgb, frost);
    }
    if (!finiteColor(color)) {
        color = textureLod(InSampler, texCoord, 0.0).rgb;
    }
    float bright = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float warmth = clamp((color.r - color.b) * 3.5 + bright * bright * 0.5, 0.0, 1.0);
    vec3 graded = mix(color * vec3(0.74, 0.86, 1.14), color * vec3(1.12, 0.97, 0.76), warmth);
    vec3 golden = color * mix(vec3(1.0), vec3(1.1, 0.98, 0.82), Mood.w);
    color = mix(golden, graded, Mood.y);
    color = 1.0 - exp(-color * Mood.z);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.16);
    color = mix(color, color * color * (3.0 - 2.0 * color), 0.3);
    color += vec3(0.012, 0.0, 0.036) * (1.0 - clamp(luma * 2.2, 0.0, 1.0));
    vec2 centred = texCoord - 0.5;
    float vignette = 1.0 - smoothstep(0.32, 0.92, length(centred * vec2(1.15, 1.0)));
    color *= mix(mix(0.72, 0.42, Mood.y), 1.0, vignette);
    if (revealMode() == 1) {
        color *= mix(REVEAL_FLOOR, 1.0, smoothstep(0.0, REVEAL_FADE, Lens.w));
    }
    vec2 pixel = gl_FragCoord.xy;
    color += (vec3(noise(pixel), noise(pixel + vec2(19.0, 47.0)), noise(pixel + vec2(71.0, 13.0))) + vec3(noise(pixel + vec2(37.0, 89.0))) - 1.0) * DITHER;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
