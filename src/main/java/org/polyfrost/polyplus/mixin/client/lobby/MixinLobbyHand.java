package org.polyfrost.polyplus.mixin.client.lobby;

import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.3 {
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.ItemInHandRenderer;
*///?} else {
/*import net.minecraft.client.render.ItemInHandRenderer;
*///?}

//? if >= 26.3 {
@Mixin(FirstPersonHandsAndItemsRenderer.class)
//?} else {
/*@Mixin(ItemInHandRenderer.class)
*///?}
public class MixinLobbyHand {
    //? if >= 26.2 {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    //?} elif > 1.8.9 {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    *///?} else {
    /*@Inject(method = "renderInFirstPerson", at = @At("HEAD"), cancellable = true)
    *///?}
    private void polyplus$hideLobbyHand(CallbackInfo ci) {
        if (LobbyScene.terrainActive() || LobbyScene.ownsFrame()) ci.cancel();
    }
}
