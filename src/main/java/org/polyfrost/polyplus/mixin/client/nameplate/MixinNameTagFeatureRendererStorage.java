package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.10 && < 26.2 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NameTagFeatureRenderer.Storage.class)
*///?}
public class MixinNameTagFeatureRendererStorage {
    //? if >= 1.21.10 && < 26.2 {
    /*@ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 0)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameplateSubmitAdapter.observe(submit, false);
    }

    @ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 1)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeSeeThroughNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameplateSubmitAdapter.observe(submit, true);
    }

    @ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 2)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeSneakingNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameplateSubmitAdapter.observe(submit, false);
    }
    *///?}
}
