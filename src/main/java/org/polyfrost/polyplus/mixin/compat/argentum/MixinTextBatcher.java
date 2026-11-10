package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.polyfrost.polyplus.client.PolyPlusBadge;
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
    @ModifyReturnValue(method = "cacheable(Ljava/lang/String;)Z", at = @At("RETURN"), remap = false, require = 0)
    private static boolean polyplus$skipEmoji(boolean cacheable, String text) {
        return cacheable && !polyplus$hasEmoji(text);
    }

    // Argentum 1.0.3+ draws non-cacheable text with its own glyphs too, so the badge/emoji never reach
    // MixinEmojiTextRenderer. NaN makes Argentum's TextRenderer hook fall back to vanilla for this string
    @Inject(method = "begin", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$vanillaForEmoji(CallbackInfoReturnable<Float> cir, @Local(argsOnly = true) String text) {
        if (polyplus$hasEmoji(text)) cir.setReturnValue(Float.NaN);
    }

    // Argentum computes widths itself instead of going through TextRenderer.getWidth(C)
    @Inject(method = "charWidth(CZ[B)F", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$emojiWidth(char chr, boolean unicode, byte[] glyphSizes, CallbackInfoReturnable<Float> cir) {
        if (chr == PolyPlusBadge.LEGACY_CHAR) cir.setReturnValue((float) PolyPlusBadge.BADGE_ADVANCE);
        else if (EmojiFont.legacyIndex(chr) >= 0) cir.setReturnValue(EmojiFont.LEGACY_ADVANCE);
    }

    // Argentum 1.0.3+ signature
    @Inject(method = "charWidth(IZ)F", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polyplus$emojiWidthCodePoint(int codePoint, boolean unicode, CallbackInfoReturnable<Float> cir) {
        if (codePoint == PolyPlusBadge.LEGACY_CHAR) cir.setReturnValue((float) PolyPlusBadge.BADGE_ADVANCE);
        else if (codePoint >= PolyPlusBadge.LEGACY_CHAR && codePoint <= Character.MAX_VALUE
                && EmojiFont.legacyIndex((char) codePoint) >= 0) cir.setReturnValue(EmojiFont.LEGACY_ADVANCE);
    }

    // runs for every string Argentum draws, so skip the legacyIndex call for anything below the private use area
    private static boolean polyplus$hasEmoji(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= PolyPlusBadge.LEGACY_CHAR && (c == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex(c) >= 0)) {
                return true;
            }
        }
        return false;
    }
}
*///?}