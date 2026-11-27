package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 26.2 {
/*import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterialBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class MixinNameplatePreparedType {
    @Inject(method = "prepare", at = @At("RETURN"))
    private void polyplus$trackNameplateMaterial(CallbackInfoReturnable<PreparedRenderType> cir) {
        NameplateMaterialBackend.prepared(this, cir.getReturnValue());
    }
}
*///?}
