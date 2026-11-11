package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.polyfrost.polyplus.client.emoji.EmojiFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Argentum tints every glyph with the text color; draw the badge and emoji untinted and without a shadow,
// matching the vanilla TextRenderer path
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class MixinTextBatcher {
    @Unique private boolean polyplus$colorGlyph;
    @Unique private boolean polyplus$untint;

    @ModifyArg(
        method = "layout",
        at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/gui/font/FontSet;glyph(IZ)Ldev/rdh/argentum/impl/render/gui/font/Glyph;"),
        index = 0,
        require = 0
    )
    private int polyplus$trackGlyph(int codepoint) {
        polyplus$colorGlyph = codepoint <= Character.MAX_VALUE
            && (codepoint == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex((char) codepoint) >= 0);
        polyplus$untint = false;
        return codepoint;
    }

    @ModifyExpressionValue(
        method = "layout",
        at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/gui/font/Glyph;visible()Z"),
        require = 0
    )
    private boolean polyplus$skipShadow(boolean visible, @Local(argsOnly = true, ordinal = 0) boolean shadow) {
        if (!polyplus$colorGlyph) return visible;
        polyplus$untint = !shadow;
        return visible && !shadow;
    }

    @ModifyArg(method = "layout", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/gui/TextBatcher;decoration"), index = 0, require = 0)
    private float polyplus$tintDecoration(float x) {
        polyplus$untint = false;
        return x;
    }

    @ModifyArg(method = "vertex", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;", remap = true), index = 0, require = 0)
    private float polyplus$red(float red) {
        return polyplus$untint ? 1.0F : red;
    }

    @ModifyArg(method = "vertex", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;", remap = true), index = 1, require = 0)
    private float polyplus$green(float green) {
        return polyplus$untint ? 1.0F : green;
    }

    @ModifyArg(method = "vertex", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;", remap = true), index = 2, require = 0)
    private float polyplus$blue(float blue) {
        return polyplus$untint ? 1.0F : blue;
    }
}
*///?}
