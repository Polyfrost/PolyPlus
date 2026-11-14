package org.polyfrost.polyplus.mixin.compat.oneconfig;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.lobby.StartupSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9
import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.sound.ExternalSounds", remap = false)
public class MixinExternalSounds {
    //? if > 1.8.9 {
    @WrapOperation(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", remap = true),
            remap = false,
            require = 0,
            expect = 0
    )
    private static CompletableFuture<Void> polyplus$reloadOnlySounds(Operation<CompletableFuture<Void>> original) {
        return StartupSounds.reload(original);
    }
    //?} else {
    /*@WrapOperation(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;reloadResources()V", remap = true),
            remap = false,
            require = 0,
            expect = 0
    )
    private static void polyplus$reloadOnlySounds(Operation<Void> original) {
        StartupSounds.reload(original);
    }
    *///?}
}
