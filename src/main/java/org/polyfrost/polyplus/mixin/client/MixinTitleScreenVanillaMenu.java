package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.polyfrost.polyplus.client.gui.vanillamenu.VanillaTitleMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The vanilla title screen's intro slide, menu editor and player preview. */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenVanillaMenu {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void polyplus$frameStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        VanillaTitleMenu.frameStart((Screen) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void polyplus$frameEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        VanillaTitleMenu.frameEnd((Screen) (Object) this, graphics, mouseX, mouseY, a);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$editorClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaTitleMenu.mouseClicked((Screen) (Object) this, event, doubleClick)) cir.setReturnValue(true);
    }
}
//?}
