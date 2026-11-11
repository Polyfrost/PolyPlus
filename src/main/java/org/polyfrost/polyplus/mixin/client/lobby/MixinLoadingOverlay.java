package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.polyfrost.polyplus.client.lobby.StartupOverlay;
import org.polyfrost.polyplus.compat.RrlsOverlay;
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

//? if < 1.21.10 {
/*import java.util.Optional;
import java.util.function.Consumer;
*///?}

@Mixin(LoadingOverlay.class)
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

    //? if < 1.21.10 {
    /*@Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;
    *///?}

    @Unique
    private boolean polyplus$branded;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$claimInitialLoad(CallbackInfo ci) {
        polyplus$branded = StartupOverlay.begin(fadeIn, minecraft.isGameLoadFinished());
    }

    //? if >= 26.1 {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawStartup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawStartup(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
    *///?}
        if (!polyplus$branded) return;
        if (RrlsOverlay.hides(this)) {
            polyplus$branded = false;
            StartupOverlay.abandon(reload.done());
            return;
        }
        ci.cancel();
        //? if < 1.21.10 {
        /*if (fadeOutStart == -1L && reload.isDone()) {
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable error) {
                onFinish.accept(Optional.of(error));
            }
            fadeOutStart = System.currentTimeMillis();
            if (minecraft.screen != null) minecraft.screen.init(minecraft, graphics.guiWidth(), graphics.guiHeight());
        }
        *///?}
        StartupOverlay.frame(minecraft, graphics, partial, reload.getActualProgress(), fadeOutStart > -1L);
    }
}
//?}
