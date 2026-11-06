package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
//? if >= 26.1 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
//?}
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
//? if < 26.3 {
/*import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.polyfrost.polyplus.mixin.client.access.GameRendererAccessor;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    //? if >= 26.1 {
    @ModifyExpressionValue(method = "extract", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private ClientLevel polyplus$embeddedLobbyOwnsLevel(ClientLevel original) {
        return LobbyScene.ownsFrame() ? null : original;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void polyplus$extractLobby(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (advanceGameTime) LobbyScene.onExtractEnd((GameRenderer) (Object) this, deltaTracker);
    }
    //?}

    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$beginLobbyRender(CallbackInfo ci) {
        LobbyScene.onRenderHead();
    }

    //? if >= 1.21.8 {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    //?} else {
    /*@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    *///?}
    private void polyplus$endLobbyRender(CallbackInfo ci) {
        LobbyScene.onLevelRendered();
    }

    //? if < 26.1 {
    /*@Inject(method = "render", at = @At("RETURN"))
    private void polyplus$captureLobbyFrame(CallbackInfo ci) {
        LobbyScene.onLevelRendered();
    }
    *///?}

    //? if >= 26.3 {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V"))
    private void polyplus$drawLobbyHolograms(CallbackInfo ci) {
        LobbyScene.drawHolograms((GameRenderer) (Object) this);
    }
    //?}

    //? if >= 26.2 && < 26.3 {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render", shift = At.Shift.AFTER))
    *///?} elif < 26.2 {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel", shift = At.Shift.AFTER))
    *///?}
    //? if < 26.3 {
    /*private void polyplus$processLobbyPost(CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (!LobbyScene.terrainActive()) return;
        LobbyScene.drawHolograms(renderer);
        //? if < 26.1
        /^LobbyScene.onLevelDrawn(renderer);^/
        //? if >= 1.21.4 {
        /^LobbySky.INSTANCE.process(renderer, ((GameRendererAccessor) renderer).polyplus$resourcePool());
        ^///?} else {
        /^LobbySky.INSTANCE.process(renderer);
        ^///?}
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyHand(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }

    //? if < 1.21.4 {
    /^@Inject(method = "reloadShaders", at = @At("HEAD"))
    private void polyplus$reloadLobbyChain(CallbackInfo ci) {
        LobbySky.release();
    }
    ^///?}
    *///?}
}
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import org.joml.Vector4f;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.polyfrost.polyplus.client.lobby.LobbyLegacyRender;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow
    private float fogRed;

    @Shadow
    private float fogGreen;

    @Shadow
    private float fogBlue;

    @Shadow
    private boolean lightMapDirty;

    @Shadow
    private float lightMapFlicker;

    @Shadow
    @Final
    private int[] lightMapPixels;

    @Shadow
    @Final
    private DynamicTexture lightmapTexture;

    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void polyplus$beginLobbyRender(float tickDelta, long startTime, CallbackInfo ci) {
        LobbyScene.onRenderHead();
    }

    @Inject(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJ)V", shift = At.Shift.AFTER))
    private void polyplus$processLobbyPost(float tickDelta, long startTime, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        if (!LobbyScene.terrainActive()) return;
        LobbyScene.onLevelDrawn(renderer);
        LobbySky.INSTANCE.process(renderer);
    }

    @Inject(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
    private void polyplus$endLobbyRender(float tickDelta, long startTime, CallbackInfo ci) {
        if (LobbyScene.terrainActive()) LobbyLegacyRender.restoreGuiState((GameRenderer) (Object) this);
        LobbyScene.onLevelRendered();
    }

    @Inject(method = "render(FJ)V", at = @At("RETURN"))
    private void polyplus$finishLobbyFrame(float tickDelta, long startTime, CallbackInfo ci) {
        LobbyScene.onLevelRendered();
    }

    @Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V", ordinal = 1))
    private void polyplus$captureLobbyDepth(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo ci) {
        if (!LobbyScene.terrainActive()) return;
        LobbyScene.drawHolograms(tickDelta);
        LobbySky.captureDepth();
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void polyplus$reloadLobbyChain(CallbackInfo ci) {
        LobbySky.release();
    }

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float polyplus$lobbyFov(float original) {
        return PauseStage.fov(LobbyScene.cameraFov(original));
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyHand(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }

    @Inject(method = "renderAxisIndicators", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyAxes(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }

    @Inject(method = "applyViewBobbing", at = @At("HEAD"), cancellable = true)
    private void polyplus$stillLobbyCamera(CallbackInfo ci) {
        if (LobbyScene.terrainActive()) ci.cancel();
    }

    @Inject(method = "updateLightMap", at = @At("HEAD"), cancellable = true)
    private void polyplus$lobbyLightmap(float tickDelta, CallbackInfo ci) {
        if (!LobbyScene.terrainActive()) return;
        ci.cancel();
        if (!lightMapDirty) return;
        lightMapDirty = false;
        LobbySky.INSTANCE.legacyLightmap(Minecraft.getInstance().world, lightMapPixels, lightMapFlicker);
        lightmapTexture.upload();
    }

    @Inject(method = "setupClearColor", at = @At("RETURN"))
    private void polyplus$lobbyFogColor(float tickDelta, CallbackInfo ci) {
        if (!LobbyScene.terrainActive()) return;
        Vector4f tinted = LobbySky.fogColor(new Vector4f(fogRed, fogGreen, fogBlue, 1f));
        fogRed = tinted.x;
        fogGreen = tinted.y;
        fogBlue = tinted.z;
        GlStateManager.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void polyplus$shapeLobbyFog(int mode, float tickDelta, CallbackInfo ci) {
        if (!LobbyScene.terrainActive() || mode == -1) return;
        GlStateManager.fogStart(LobbyScene.terrainFogStart());
        GlStateManager.fogEnd(LobbyScene.terrainFogEnd());
    }
}
*///?}
