package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.polyfrost.polyplus.client.emoji.EmojiFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Argentum tints every glyph with the text color; draw the badge and emoji untinted and without a shadow,
// matching the vanilla TextRenderer path
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class MixinTextBatcher {
    @Shadow private float red;
    @Shadow private float green;
    @Shadow private float blue;

    @Unique private boolean polyplus$colorGlyph;

    @ModifyArg(
        method = "layout",
        at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/gui/font/FontSet;glyph(IZ)Ldev/rdh/argentum/impl/render/gui/font/Glyph;"),
        index = 0,
        require = 0
    )
    private int polyplus$trackGlyph(int codepoint) {
        polyplus$colorGlyph = codepoint <= Character.MAX_VALUE
            && (codepoint == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex((char) codepoint) >= 0);
        return codepoint;
    }

    @WrapOperation(
        method = "layout",
        at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/gui/TextBatcher;glyph"),
        require = 0
    )
    private void polyplus$untintGlyph(@Coerce Object batcher, @Coerce Object glyph, float x, float y, boolean italic, @Coerce Object textureManager,
                                      Operation<Void> original, @Local(argsOnly = true, ordinal = 0) boolean shadow) {
        if (!polyplus$colorGlyph) {
            original.call(batcher, glyph, x, y, italic, textureManager);
            return;
        }
        if (shadow) return;
        float r = red, g = green, b = blue;
        red = green = blue = 1.0F;
        original.call(batcher, glyph, x, y, italic, textureManager);
        red = r;
        green = g;
        blue = b;
    }
}
*///?}
