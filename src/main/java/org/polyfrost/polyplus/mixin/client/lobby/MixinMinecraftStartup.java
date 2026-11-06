package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureManager;
import org.polyfrost.polyplus.client.lobby.StartupOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftStartup {
    @Inject(method = "renderLoadingScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"))
    private void polyplus$brandSplash(TextureManager textureManager, CallbackInfo ci) {
        StartupOverlay.legacySplash();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$initDone(CallbackInfo ci) {
        StartupOverlay.legacyInitDone();
    }
}
*///?}
