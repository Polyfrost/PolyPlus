const float POLYPLUS_RING_GAIN = 0.6;
const float POLYPLUS_RING_WIDTH = 7.0;
const float POLYPLUS_RING_RAMP = 28.0;
const float POLYPLUS_WAVE_SPEED = 28.0;

vec4 polyplus_lobbyReveal(vec4 color) {
    if (polyplus_waveFront <= 0.0) {
        discard;
    }
    float ring = 1.0 - clamp(polyplus_waveFront / POLYPLUS_RING_WIDTH, 0.0, 1.0);
    ring *= smoothstep(POLYPLUS_RING_WIDTH, POLYPLUS_RING_WIDTH + POLYPLUS_RING_RAMP, u_PolyLobbyWave.x * POLYPLUS_WAVE_SPEED);
    vec3 tint = u_FogColor.rgb / max(max(u_FogColor.r, max(u_FogColor.g, u_FogColor.b)), 0.05);
    color.rgb += mix(tint, vec3(1.0), ring) * POLYPLUS_RING_GAIN * ring * ring;
    return color;
}
