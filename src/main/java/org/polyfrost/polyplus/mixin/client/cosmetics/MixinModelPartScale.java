package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public class MixinModelPartScale implements ModelPartScaleAccess {
    @Unique
    private float polyplus$scaleX = 1f;

    @Unique
    private float polyplus$scaleY = 1f;

    @Unique
    private float polyplus$scaleZ = 1f;

    @Override
    public float polyplus$scaleX() {
        return polyplus$scaleX;
    }

    @Override
    public float polyplus$scaleY() {
        return polyplus$scaleY;
    }

    @Override
    public float polyplus$scaleZ() {
        return polyplus$scaleZ;
    }

    @Override
    public void polyplus$setScale(float x, float y, float z) {
        polyplus$scaleX = x;
        polyplus$scaleY = y;
        polyplus$scaleZ = z;
    }

    @Unique
    private boolean polyplus$scaled() {
        return polyplus$scaleX != 1f || polyplus$scaleY != 1f || polyplus$scaleZ != 1f;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V"))
    private void polyplus$scaleBoxes(int list, Operation<Void> original) {
        if (!polyplus$scaled()) {
            original.call(list);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scalef(polyplus$scaleX, polyplus$scaleY, polyplus$scaleZ);
        original.call(list);
        GlStateManager.popMatrix();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/model/ModelPart;render(F)V"))
    private void polyplus$scaleChildren(ModelPart child, float scale, Operation<Void> original) {
        if (!polyplus$scaled()) {
            original.call(child, scale);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scalef(polyplus$scaleX, polyplus$scaleY, polyplus$scaleZ);
        original.call(child, scale);
        GlStateManager.popMatrix();
    }

    @Inject(method = "transform", at = @At("TAIL"))
    private void polyplus$scaleTransform(float scale, CallbackInfo ci) {
        if (polyplus$scaled()) GlStateManager.scalef(polyplus$scaleX, polyplus$scaleY, polyplus$scaleZ);
    }
}
*///?}
