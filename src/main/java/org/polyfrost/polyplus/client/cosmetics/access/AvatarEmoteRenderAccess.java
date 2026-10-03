package org.polyfrost.polyplus.client.cosmetics.access;

//? if > 1.8.9 {
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import java.util.Map;

public interface AvatarEmoteRenderAccess {

    EmoteController polyplus$boundEmoteController();

    void polyplus$bindEmoteController(EmoteController controller);

    Map<String, BoneTransform> polyplus$lastEmoteSample();

    void polyplus$setLastEmoteSample(Map<String, BoneTransform> sample);
}
//?}
