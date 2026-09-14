package org.polyfrost.polyplus.mixin.compat.animatium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "org.visuals.legacy.animatium.util.ToastUtil", remap = false)
public class MixinToastUtil {
    @WrapMethod(
            method = {
                    "send(Lnet/minecraft/network/chat/Component;)V",
                    "send(Lnet/minecraft/class_2561;)V"
            },
            remap = false,
            require = 0,
            expect = 0
    )
    private void polyplus$hideToasts(Component message, Operation<Void> original) {
    }
}
