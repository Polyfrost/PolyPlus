package org.polyfrost.polyplus.mixin.client.animatium;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.visuals.legacy.animatium.util.ToastUtil", remap = false)
public class Mixin_HideAnimatiumToasts {
    @Inject(
            method = {
                    "send(Lnet/minecraft/network/chat/Component;)V",
                    "send(Lnet/minecraft/class_2561;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0,
            expect = 0
    )
    private void polyplus$hideToasts(Component message, CallbackInfo ci) {
        ci.cancel();
    }
}
