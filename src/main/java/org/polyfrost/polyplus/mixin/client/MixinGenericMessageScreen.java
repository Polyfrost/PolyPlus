package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.MainMenuReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

@Mixin(GenericMessageScreen.class)
public class MixinGenericMessageScreen {
    @Inject(
        //? if >= 26.1 {
        method = "extractBackground",
        //?} else {
        /*method = "renderBackground",
        *///?}
        at = @At("HEAD"),
        cancellable = true
    )
    private void polyplus$darkJoinLeaveBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int mouseX, int mouseY, float tickDelta, CallbackInfo ci
    ) {
        if (!MainMenuReplacement.enabled() || Minecraft.getInstance().level != null) return;
        Screen self = (Screen) (Object) this;
        graphics.fill(0, 0, self.width, self.height, 0xFF0B1013);
        ci.cancel();
    }
}
//?}
