const float POLYPLUS_WAVE_WIDTH = 10.0;
const float POLYPLUS_WAVE_DEPTH = 4.0;
const float POLYPLUS_WAVE_CREST = 0.5;

float polyplus_front(vec3 polyplus_position) {
    if (polyplus_LobbyWave == vec4(0.0)) {
        return 1.0;
    }
    float fromAim = max(dot(polyplus_position.xz, polyplus_LobbyWave.xy) - polyplus_LobbyWave.z, 0.0);
    return polyplus_LobbyWave.w - fromAim;
}

float polyplus_lift(vec3 polyplus_position) {
    if (polyplus_LobbyWave == vec4(0.0)) {
        return 0.0;
    }
    float risen = clamp(polyplus_front(polyplus_position) / POLYPLUS_WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * POLYPLUS_WAVE_CREST;
    return (eased - 1.0) * POLYPLUS_WAVE_DEPTH + crest;
}
