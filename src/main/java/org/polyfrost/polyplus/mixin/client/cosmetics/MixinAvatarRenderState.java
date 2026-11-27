package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?}

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess;
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord;
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalsRenderAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderState.class)
//?} elif >= 1.21.4 {
/*@Mixin(PlayerRenderState.class)
*///?}
public class MixinAvatarRenderState
    //? if >= 1.21.4
    implements AvatarEmoteRenderAccess, PlayerSignalsRenderAccess
{
    //? if >= 1.21.4 {
    @Unique
    private EmoteController polyplus$boundEmoteController;

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

    @Unique
    private PlayerSignalRecord polyplus$signals;

    @Override
    public PlayerSignalRecord polyplus$signals() {
        return polyplus$signals;
    }

    @Override
    public void polyplus$setSignals(PlayerSignalRecord record) {
        polyplus$signals = record;
    }
    //?}
}
