#version 330

//? if >= 1.21.8 {
layout(std140) uniform LightmapInfo {
    float AmbientLightFactor;
    float SkyFactor;
    float BlockFactor;
    int UseBrightLightmap;
    float NightVisionFactor;
    float DarknessScale;
    float DarkenWorldFactor;
    float BrightnessFactor;
    vec3 SkyLightColor;
} lightmapInfo;

#define SKY_FACTOR lightmapInfo.SkyFactor
#define BLOCK_FACTOR lightmapInfo.BlockFactor
#define SKY_LIGHT_COLOR lightmapInfo.SkyLightColor
#define DARKEN_WORLD lightmapInfo.DarkenWorldFactor
#define DARKNESS lightmapInfo.DarknessScale
#define BRIGHTNESS lightmapInfo.BrightnessFactor
//?} else {
/*uniform float SkyFactor;
uniform float BlockFactor;
uniform vec3 SkyLightColor;
uniform float DarknessScale;
uniform float DarkenWorldFactor;
uniform float BrightnessFactor;
uniform vec4 LobbyNight;

#define SKY_FACTOR max(SkyFactor, LobbyNight.w)
#define BLOCK_FACTOR BlockFactor
#define SKY_LIGHT_COLOR mix(SkyLightColor, LobbyNight.rgb, LobbyNight.w)
#define DARKEN_WORLD DarkenWorldFactor
#define DARKNESS DarknessScale
#define BRIGHTNESS BrightnessFactor
*///?}

in vec2 texCoord;

out vec4 fragColor;

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    float maxInverted = 1.0f - maxComponent;
    float maxScaled = 1.0f - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

void main() {
    float block_brightness = get_brightness(floor(texCoord.x * 16) / 15) * BLOCK_FACTOR;
    float sky_brightness = get_brightness(floor(texCoord.y * 16) / 15) * SKY_FACTOR;

    vec3 color = vec3(
        block_brightness,
        block_brightness * ((block_brightness * 0.6 + 0.4) * 0.6 + 0.4),
        block_brightness * (block_brightness * block_brightness * 0.6 + 0.4)
    );

    color += SKY_LIGHT_COLOR * sky_brightness;
    color = mix(color, vec3(0.75), 0.04);
    color = mix(color, color * vec3(0.7, 0.6, 0.6), DARKEN_WORLD);
    color = clamp(color - vec3(DARKNESS), 0.0, 1.0);
    color = mix(color, notGamma(color), BRIGHTNESS);
    color = mix(color, vec3(0.75), 0.04);

    fragColor = vec4(color, 1.0);
}
