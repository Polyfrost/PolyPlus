package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.polyfrost.polyplus.client.gui.vanillamenu.ElementTransforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The menu editor's scale: a widget draws scaled from its top left corner and takes clicks over its scaled size. */
@Mixin(AbstractWidget.class)
public abstract class MixinAbstractWidgetScale {
    @Shadow public boolean visible;
    @Shadow protected int width;
    @Shadow protected int height;

    @Shadow public abstract int getX();

    @Shadow public abstract int getY();

    @Unique private boolean polyplus$scaled;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void polyplus$pushScale(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        float scale = ElementTransforms.scale((AbstractWidget) (Object) this);
        polyplus$scaled = scale != 1f && visible;
        if (!polyplus$scaled) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(getX(), getY());
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-getX(), -getY());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void polyplus$popScale(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!polyplus$scaled) return;
        polyplus$scaled = false;
        graphics.pose().popMatrix();
    }

    @Inject(method = "areCoordinatesInRectangle", at = @At("HEAD"), cancellable = true)
    private void polyplus$scaledHit(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        float scale = ElementTransforms.scale((AbstractWidget) (Object) this);
        if (scale == 1f) return;
        cir.setReturnValue(x >= getX() && y >= getY() && x < getX() + width * scale && y < getY() + height * scale);
    }
}
//?}
