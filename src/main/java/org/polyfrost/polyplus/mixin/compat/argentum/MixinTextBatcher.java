package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
        if (!cacheable) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= PolyPlusBadge.LEGACY_CHAR && (c == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex(c) >= 0)) return false;
        }
        return true;
    }
}
*///?}
