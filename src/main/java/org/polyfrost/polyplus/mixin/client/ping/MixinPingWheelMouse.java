package org.polyfrost.polyplus.mixin.client.ping;

import org.polyfrost.polyplus.client.gui.WheelOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9 {
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
*///?}

//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}

//? if > 1.8.9 {
@Mixin(MouseHandler.class)
//?} else {
/*@Mixin(GameRenderer.class)
*///?}
public class MixinPingWheelMouse {
    //? if > 1.8.9 {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void polyplus$steerWheel(double movementTime, CallbackInfo ci) {
        if (!WheelOverlay.consumeLook(accumulatedDX, accumulatedDY)) return;
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void polyplus$scrollWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (WheelOverlay.consumeScroll(yOffset)) ci.cancel();
    }
    //?}

    //? if >= 1.21.9 {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void polyplus$clickWheel(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (WheelOverlay.consumeButton(button.button(), action == 1)) ci.cancel();
    }
    //?} elif > 1.8.9 {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void polyplus$clickWheel(long window, int button, int action, int mods, CallbackInfo ci) {
        if (WheelOverlay.consumeButton(button, action == 1)) ci.cancel();
    }
    *///?} else {
    /*@WrapWithCondition(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;updateLocalPlayerCamera(FF)V"))
    private boolean polyplus$steerWheel(LocalClientPlayerEntity player, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        return !WheelOverlay.consumeLook(mc.mouse.x, -mc.mouse.y);
    }
    *///?}
}
