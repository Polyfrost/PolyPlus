package org.polyfrost.polyplus.mixin.compat.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if dh {
import org.polyfrost.polyplus.compat.distanthorizons.DhLobbyGuard;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

@Pseudo
@Mixin(targets = {
        "com.seibel.distanthorizons.core.world.DhClientWorld",
        "com.seibel.distanthorizons.core.world.DhClientServerWorld"
}, remap = false)
public abstract class MixinDhClientWorlds {
    //? if dh {
    @Inject(
            method = {
                    "getOrLoadLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientLevel;",
                    "getLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientLevel;",
                    "getOrLoadLevel(Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/ILevelWrapper;)Lcom/seibel/distanthorizons/core/level/DhClientServerLevel;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0,
            expect = 0
    )
    private void polyplus$noLobbyLevel(@Coerce Object level, CallbackInfoReturnable<Object> cir) {
        if (DhLobbyGuard.isLobby(level)) {
            cir.setReturnValue(null);
        }
    }
    //?}
}
