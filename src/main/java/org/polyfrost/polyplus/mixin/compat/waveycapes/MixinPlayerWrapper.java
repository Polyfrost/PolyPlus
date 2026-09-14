//~ identifier
package org.polyfrost.polyplus.mixin.compat.waveycapes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

//? if >= 1.21.11 {
import net.minecraft.resources.Identifier;
//?}

//? if >= 1.21.10 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.spongepowered.asm.mixin.injection.At;
//?}

//? if = 1.21.10 {
/*import net.minecraft.resources.Identifier;
*///?}

@Pseudo
@Mixin(targets = "dev.tr7zw.transition.mc.entitywrapper.PlayerWrapper", remap = false)
public class MixinPlayerWrapper {
    //? if >= 1.21.10 {
    @ModifyReturnValue(method = "getCapeTexture", at = @At("RETURN"), remap = false, require = 0, expect = 0)
    private Identifier polyplus$previewCapeTexture(Identifier original) {
        if (!PlayerPreviewRenderer.isRenderingPreview()) {
            return original;
        }
        return (Identifier) PlayerPreviewRenderer.previewCapeOverride();
    }
    //?}
}
