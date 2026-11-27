package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 26.2 {
/*import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialUniforms;
import org.polyfrost.polyplus.client.nameplate.render.material.RenderTypeNameAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} elif >= 1.21.11 && < 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialUniforms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
*///?}

//? if >= 1.21.11 && < 26.3 {
/*@Mixin(RenderType.class)
*///?}
public class MixinRenderType {
    //? if >= 1.21.11 && < 26.3 {
    /*@Shadow
    @Final
    protected String name;
    *///?}

    //? if = 26.2 {
    /*@Inject(method = "prepare", at = @At("RETURN"))
    private void polyplus$nameNameplateDraw(CallbackInfoReturnable<PreparedRenderType> cir) {
        if (NameplateMaterialUniforms.owns(name)) {
            ((RenderTypeNameAccess) (Object) cir.getReturnValue()).polyplus$setRenderTypeName(name);
        }
    }
    *///?} elif >= 1.21.11 && < 26.2 {
    /*@WrapOperation(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V"
        )
    )
    private void polyplus$bindNameplateMaterial(RenderPass pass, Operation<Void> original) {
        original.call(pass);
        NameplateMaterialUniforms.bind(name, pass);
    }
    *///?}
}
