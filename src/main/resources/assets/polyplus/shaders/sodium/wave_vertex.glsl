const float POLYPLUS_WAVE_SPEED = 28.0;
const float POLYPLUS_WAVE_WIDTH = 10.0;
const float POLYPLUS_WAVE_DEPTH = 4.0;
const float POLYPLUS_WAVE_CREST = 0.5;

vec3 polyplus_lobbyWave(vec3 pos) {
    float seconds = u_PolyLobbyWave.x;
    float distanceToAim = u_PolyLobbyWave.y;
    vec3 forward = -vec3(u_ModelViewMatrix[0][2], u_ModelViewMatrix[1][2], u_ModelViewMatrix[2][2]);
    vec3 aim = forward * distanceToAim;
    float level = length(forward.xz) + 1e-4;
    vec2 across = forward.xz / level;
    float aimAlong = level * distanceToAim;
    float dip = atan(-forward.y, level) + atan(1.0 / u_ProjectionMatrix[1][1]);
    float start = dip <= 0.0 ? aimAlong : (dip >= 1.5707 ? 0.0 : clamp(-aim.y / tan(dip), 0.0, aimAlong));
    float fromAim = max(dot(pos.xz, across) - start, 0.0);
    float behindFront = seconds * POLYPLUS_WAVE_SPEED - fromAim;
    float risen = clamp(behindFront / POLYPLUS_WAVE_WIDTH, 0.0, 1.0);
    float eased = risen * risen * (3.0 - 2.0 * risen);
    float crest = sin(risen * 3.14159) * POLYPLUS_WAVE_CREST;
    pos.y += (eased - 1.0) * POLYPLUS_WAVE_DEPTH + crest;
    polyplus_waveFront = behindFront;
    return pos;
}
