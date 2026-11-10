package org.polyfrost.polyplus.mixin.client.ping;

import org.polyfrost.polyplus.client.gui.WheelOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9 {
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerInventory;
import org.spongepowered.asm.mixin.Unique;
*///?}

//? if >= 1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}

//? if > 1.8.9 {
@Mixin(KeyboardHandler.class)
//?} else {
/*@Mixin(Minecraft.class)
*///?}
public class MixinPingWheelKeys {
    //? if >= 1.21.9 {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void polyplus$escapeWheel(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 1 && event.key() == 256 && WheelOverlay.consumeEscape()) ci.cancel();
    }
    //?} elif > 1.8.9 {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void polyplus$escapeWheel(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == 1 && key == 256 && WheelOverlay.consumeEscape()) ci.cancel();
    }
    *///?} else {
    /*@Unique
    private boolean polyplus$swallowClick;

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame()V"))
    private boolean polyplus$escapeWheel(Minecraft minecraft) {
        return !WheelOverlay.consumeEscape();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;set(IZ)V", ordinal = 0))
    private boolean polyplus$clickWheel(int keyCode, boolean pressed) {
        if (!WheelOverlay.consumeButton(keyCode + 100, pressed)) return true;
        polyplus$swallowClick = true;
        return false;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;click(I)V", ordinal = 0))
    private boolean polyplus$swallowWheelClick(int keyCode) {
        if (!polyplus$swallowClick) return true;
        polyplus$swallowClick = false;
        return false;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/living/player/PlayerInventory;scrollInHotbar(I)V"))
    private boolean polyplus$scrollWheel(PlayerInventory inventory, int amount) {
        return !WheelOverlay.consumeScroll(Math.signum(amount));
    }
    *///?}
}
