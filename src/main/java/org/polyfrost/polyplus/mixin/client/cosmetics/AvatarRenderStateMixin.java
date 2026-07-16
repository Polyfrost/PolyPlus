//? if >= 1.21.4 {
package org.polyfrost.polyplus.mixin.client.cosmetics;

import java.util.Collections;
import java.util.Map;

import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderState.class)
//?} else {
/*@Mixin(PlayerRenderState.class)
*///?}
public class AvatarRenderStateMixin implements AvatarEmoteRenderAccess {
    @Unique
    private EmoteController polyplus$boundEmoteController;

    @Unique
    private Map<String, BoneTransform> polyplus$lastEmoteSample;

    @Override
    public EmoteController polyplus$boundEmoteController() {
        EmoteController controller = polyplus$boundEmoteController;
        if (controller == null) {
            controller = new EmoteController();
            polyplus$boundEmoteController = controller;
        }
        return controller;
    }

    @Override
    public void polyplus$bindEmoteController(EmoteController controller) {
        polyplus$boundEmoteController = controller;
    }

    @Override
    public Map<String, BoneTransform> polyplus$lastEmoteSample() {
        Map<String, BoneTransform> sample = polyplus$lastEmoteSample;
        return sample == null ? Collections.emptyMap() : sample;
    }

    @Override
    public void polyplus$setLastEmoteSample(Map<String, BoneTransform> sample) {
        polyplus$lastEmoteSample = sample == null ? Collections.emptyMap() : sample;
    }
}
//?}
