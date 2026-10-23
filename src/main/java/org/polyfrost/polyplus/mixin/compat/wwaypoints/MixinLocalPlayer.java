package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

//? if wwaypoints {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wwaypoints.WaypointsClient;
//?}

@IfModLoaded(value = "wwaypoints", maxVersion = "0.8.1")
@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    //? if wwaypoints {
    // wWaypoints forces this on as soon as its toggle flips at the end of the client tick, but vanilla crouches off the
    // previous tick's input, so the client would start crouching a tick before the server is told about the shift
    @WrapMethod(method = "isShiftKeyDown")
    private boolean polyplus$followSentInput(Operation<Boolean> original) {
        boolean shift = original.call();
        if (!shift || !WaypointsClient.isToggleSneakEnabled()) return shift;
        LocalPlayer self = (LocalPlayer) (Object) this;
        //? if >= 1.21.2 {
        return self.input.keyPresses.shift();
        //?} else
        //return self.input != null && self.input.shiftKeyDown;
    }
    //?}
}
