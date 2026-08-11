package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.launcher.SessionRefreshPrompt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class Mixin_RefreshSessionButton extends Screen {
    protected Mixin_RefreshSessionButton(Component title) {
        super(title);
    }

    @Shadow
    @Final
    private LinearLayout layout;

    @Unique
    private boolean polyplus$promptResolved;

    @Unique
    private SessionRefreshPrompt polyplus$prompt;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;allowsMultiplayer()Z"))
    private void polyplus$addRefreshSessionButton(CallbackInfo ci) {
        if (!this.polyplus$promptResolved) {
            this.polyplus$promptResolved = true;
            this.polyplus$prompt = SessionRefresh.createPrompt();
        }
        SessionRefreshPrompt prompt = this.polyplus$prompt;
        if (prompt == null) {
            return;
        }
        Button button = Button.builder(prompt.label(), pressed -> prompt.onPress()).width(200).build();
        this.layout.addChild(button);
        prompt.attach(button, this);
    }
}
