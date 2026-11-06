package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.4 && > 1.8.9 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("RETURN"))
    private static void polyplus$shapeLobbyFog(Camera camera, FogRenderer.FogMode mode, float far, boolean thick, float partialTick, CallbackInfo ci) {
        LobbyScene.shapeFog(mode);
    }

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void polyplus$lobbyFogColor(CallbackInfo ci) {
        if (!LobbyScene.terrainActive()) return;
        Vector4f tinted = LobbySky.fogColor(new Vector4f(fogRed, fogGreen, fogBlue, 1f));
        fogRed = tinted.x;
        fogGreen = tinted.y;
        fogBlue = tinted.z;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }
}
*///?} elif < 1.21.8 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private static FogParameters polyplus$shapeLobbyFog(FogParameters fog, Camera camera, FogRenderer.FogMode mode) {
        return LobbyScene.shapeFog(fog, mode);
    }

    @ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
    private static Vector4f polyplus$lobbyFogColor(Vector4f color) {
        return LobbyScene.terrainActive() ? LobbySky.fogColor(color) : color;
    }
}
*///?} elif < 26.1 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void polyplus$shapeLobbyFog(CallbackInfoReturnable<Vector4f> cir, @Local FogData fog) {
        LobbyScene.onFogBuilt(fog);
    }

    //? if < 1.21.11 {
    /^@ModifyExpressionValue(method = "computeFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;getBaseColor(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/Camera;IF)I"))
    private int polyplus$lobbyFogColor(int color) {
        return LobbyScene.terrainActive() ? LobbySky.fogColor(color) : color;
    }
    ^///?}
}
*///?}
