package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import org.polyfrost.polyplus.compat.ArgentumNameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// while a badged nametag renders, report no active batch so Argentum draws it immediately (real GL matrices,
// vanilla TextRenderer) instead of capturing it, see MixinEntityRendererArgentum
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.NameTagBatch", remap = false)
public class MixinNameTagBatch {
    @Inject(method = "active", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void polyplus$unbatchBadged(CallbackInfoReturnable<Object> cir) {
        if (ArgentumNameTags.isUnbatched()) cir.setReturnValue(null);
    }
}
*///?}
