package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.wwaypoints.client.ToggleSneakService", remap = false)
public class MixinToggleSneakService {
    // 0.8.0's forceDisable runs every tick while sneak modifications are disallowed, and releasing the vanilla sneak key
    // each tick would cut hold-to-sneak short; its toggle never holds that key down, so there is nothing for it to release
    @IfModLoaded(value = "wwaypoints", maxVersion = "0.8.0")
    @WrapOperation(
            method = "forceDisable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setDown(Z)V", remap = true),
            remap = false
    )
    private static void keepVanillaSneakKey(KeyMapping key, boolean down, Operation<Void> original) {
    }
}
