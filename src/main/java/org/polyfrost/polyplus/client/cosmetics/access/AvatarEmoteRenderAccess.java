package org.polyfrost.polyplus.client.cosmetics.access;

//? if > 1.8.9 {
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;

public interface AvatarEmoteRenderAccess {

    EmoteController polyplus$boundEmoteController();

    void polyplus$bindEmoteController(EmoteController controller);
}
//?}
