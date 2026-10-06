package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.polyfrost.polyplus.client.emoji.EmojiFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// emoji and the badge are drawn immediately, outside Argentum's glyph batch, so text containing them can't be replayed
// from its geometry cache, and its deferred chat background must flush first
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public class MixinTextBatcher {
    @Inject(method = "cacheable(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void polyplus$skipEmoji(String text, CallbackInfoReturnable<Boolean> cir) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex(text.charAt(i)) >= 0) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    // Argentum computes widths itself instead of going through TextRenderer.getWidth(C)
    @Inject(method = "charWidth(CZ[B)F", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$emojiWidth(char chr, boolean unicode, byte[] glyphSizes, CallbackInfoReturnable<Float> cir) {
        if (chr == PolyPlusBadge.LEGACY_CHAR) cir.setReturnValue((float) PolyPlusBadge.BADGE_ADVANCE);
        else if (EmojiFont.legacyIndex(chr) >= 0) cir.setReturnValue(EmojiFont.LEGACY_ADVANCE);
    }
}
*///?}
