package org.polyfrost.polyplus.mixin.compat.euphoria;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.integration.SodiumConsole", remap = false)
public class MixinSodiumConsole {
    @WrapMethod(
            method = "logMessage(IILjava/lang/String;)V",
            remap = false,
            require = 0,
            expect = 0
    )
    private static void polyplus$hidePopupMessages(int level, int messageFadeTimer, String message, Operation<Void> original) {
    }
}
