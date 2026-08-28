package org.polyfrost.polyplus.client.cosmetics.access;

import java.util.Collections;
import java.util.Map;

import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;

public interface AvatarEmoteRenderAccess {

    EmoteController polyplus$boundEmoteController();

    void polyplus$bindEmoteController(EmoteController controller);

    Map<String, BoneTransform> polyplus$lastEmoteSample();

    void polyplus$setLastEmoteSample(Map<String, BoneTransform> sample);
}
