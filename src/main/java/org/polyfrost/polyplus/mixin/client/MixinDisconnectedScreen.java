package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.launcher.SessionRefreshPrompt;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen extends Screen {
    private MixinDisconnectedScreen(Component title) {
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

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;allowsMultiplayer()Z"))
    private void polyplus$addRejoinButton(CallbackInfo ci) {
        if (!P2PSessionManager.canRejoin()) {
            return;
        }
        this.layout.addChild(Button.builder(Component.literal("Rejoin world"), pressed -> {
            pressed.active = false;
            P2PSessionManager.rejoin();
        }).width(200).build());
    }
}
//?} else {
/*import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.launcher.SessionRefreshPrompt;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen extends Screen {
    @Unique
    private static final int POLYPLUS_REFRESH_BUTTON_ID = 0x504C5553;

    @Unique
    private static final int POLYPLUS_REJOIN_BUTTON_ID = 0x504C5554;

    @Shadow
    private int textHeight;

    @Unique
    private boolean polyplus$promptResolved;

    @Unique
    private SessionRefreshPrompt polyplus$prompt;

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$addRefreshSessionButton(CallbackInfo ci) {
        if (!this.polyplus$promptResolved) {
            this.polyplus$promptResolved = true;
            this.polyplus$prompt = SessionRefresh.createPrompt();
        }
        SessionRefreshPrompt prompt = this.polyplus$prompt;
        if (prompt == null) {
            return;
        }
        int y = this.height / 2 + this.textHeight / 2 + this.textRenderer.fontHeight + 24;
        ButtonWidget button = new ButtonWidget(POLYPLUS_REFRESH_BUTTON_ID, this.width / 2 - 100, y, prompt.label().getString());
        this.buttons.add(button);
        prompt.attach(button, this);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$addRejoinButton(CallbackInfo ci) {
        if (!P2PSessionManager.canRejoin()) {
            return;
        }
        int rows = this.polyplus$prompt == null ? 1 : 2;
        int y = this.height / 2 + this.textHeight / 2 + this.textRenderer.fontHeight + 24 * rows;
        this.buttons.add(new ButtonWidget(POLYPLUS_REJOIN_BUTTON_ID, this.width / 2 - 100, y, "Rejoin world"));
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$pressRejoinButton(ButtonWidget button, CallbackInfo ci) {
        if (button.id == POLYPLUS_REJOIN_BUTTON_ID) {
            button.active = false;
            P2PSessionManager.rejoin();
            ci.cancel();
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$pressRefreshSessionButton(ButtonWidget button, CallbackInfo ci) {
        if (button.id == POLYPLUS_REFRESH_BUTTON_ID && this.polyplus$prompt != null) {
            this.polyplus$prompt.onPress();
            ci.cancel();
        }
    }
}
*///?}
