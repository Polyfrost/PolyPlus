package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.StartupOverlay;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(value = LoadingOverlay.class, priority = 1500)
public class MixinLoadingOverlay {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private boolean fadeIn;

    @Shadow
    private long fadeOutStart;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Unique
    private boolean polyplus$branded;

    @Unique
    private StartupOverlay.ReloadState polyplus$reloadState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$claimInitialLoad(CallbackInfo ci) {
        LobbyScene.noteReload(reload::isDone);
        polyplus$branded = StartupOverlay.begin(fadeIn, minecraft.isGameLoadFinished());
        if (!polyplus$branded && StartupOverlay.ownsReload(fadeIn)) polyplus$reloadState = new StartupOverlay.ReloadState();
    }

    //? if >= 26.1 {
    @WrapMethod(method = "extractRenderState")
    private void polyplus$drawReload(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, Operation<Void> original) {
    //?} else {
    /*@WrapMethod(method = "render")
    private void polyplus$drawReload(GuiGraphics graphics, int mouseX, int mouseY, float partial, Operation<Void> original) {
    *///?}
        if (polyplus$reloadState == null) {
            original.call(graphics, mouseX, mouseY, partial);
            return;
        }
        polyplus$finishReload(graphics.guiWidth(), graphics.guiHeight());
        if (StartupOverlay.reloadFrame(minecraft, graphics, partial, polyplus$reloadState, reload.getActualProgress(), fadeOutStart > -1L)) {
            polyplus$close();
        }
    }

    //? if >= 26.1 {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawStartup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawStartup(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
    *///?}
        if (!polyplus$branded) return;
        //? if >= 26.1 {
        boolean polyplus$visible = graphics.getClass() == GuiGraphicsExtractor.class;
        //?} else {
        /*boolean polyplus$visible = graphics.getClass() == GuiGraphics.class;
        *///?}
        if (!polyplus$visible) {
            polyplus$branded = false;
            StartupOverlay.abandon(reload.done());
            return;
        }
        ci.cancel();
        //? if < 1.21.10
        /*polyplus$finishReload(graphics.guiWidth(), graphics.guiHeight());*/
        StartupOverlay.frame(minecraft, graphics, partial, reload.getActualProgress(), fadeOutStart > -1L);
    }

    @Unique
    private void polyplus$finishReload(int width, int height) {
        if (fadeOutStart != -1L || !reload.isDone()) return;
        try {
            reload.checkExceptions();
            onFinish.accept(Optional.empty());
        } catch (Throwable error) {
            onFinish.accept(Optional.of(error));
        }
        fadeOutStart = System.currentTimeMillis();
        Screen screen = ClientPlatform.INSTANCE.currentScreen();
        if (screen == null) return;
        //? if >= 1.21.11 {
        screen.init(width, height);
        //?} else {
        /*screen.init(minecraft, width, height);
        *///?}
    }

    @Unique
    private void polyplus$close() {
        //? if >= 26.2 {
        if (minecraft.gui.overlay() == (Object) this) minecraft.gui.setOverlay(null);
        //?} else {
        /*if (minecraft.getOverlay() == (Object) this) minecraft.setOverlay(null);
        *///?}
    }
}
//?}
