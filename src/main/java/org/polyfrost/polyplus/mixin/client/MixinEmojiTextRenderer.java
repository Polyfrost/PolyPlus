package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.render.TextRenderer;
import org.polyfrost.polyplus.client.emoji.EmojiFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public abstract class MixinEmojiTextRenderer {
    @Shadow private float x;
    @Shadow private float y;
    @Shadow private float a;

    @Unique private boolean polyplus$shadowLayer;

    @Inject(method = "drawLayer(Ljava/lang/String;Z)V", at = @At("HEAD"))
    private void polyplus$trackShadow(String text, boolean shadow, CallbackInfo ci) {
        polyplus$shadowLayer = shadow;
    }

    @Inject(method = "drawGlyph", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawEmoji(char chr, boolean italic, CallbackInfoReturnable<Float> cir) {
        int index = EmojiFont.legacyIndex(chr);
        if (index < 0) return;
        if (!polyplus$shadowLayer) EmojiFont.drawLegacy(index, x, y, a);
        cir.setReturnValue(EmojiFont.LEGACY_ADVANCE);
    }

    @Inject(method = "getWidth(C)I", at = @At("HEAD"), cancellable = true)
    private void polyplus$emojiWidth(char chr, CallbackInfoReturnable<Integer> cir) {
        if (EmojiFont.legacyIndex(chr) >= 0) cir.setReturnValue((int) EmojiFont.LEGACY_ADVANCE);
    }
}
*///?}
