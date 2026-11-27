package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.10 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.polyfrost.polyplus.client.cosmetics.render.DeferredPoses;
import org.polyfrost.polyplus.client.cosmetics.render.PooledGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 26.2 {
import net.minecraft.client.renderer.SubmitNodeCollection;
//?} else {
/*import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
*///?}

//? if >= 26.2 {
@Mixin(SubmitNodeCollection.class)
//?} else {
/*@Mixin(CustomFeatureRenderer.Storage.class)
*///?}
public class MixinCustomGeometryPose {
    @WrapOperation(
        //? if >= 26.2 {
        method = "submitCustomGeometry",
        //?} else {
        /*method = "add",
        *///?}
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;copy()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;")
    )
    private PoseStack.Pose polyplus$poolPose(PoseStack.Pose pose, Operation<PoseStack.Pose> original, @Local(argsOnly = true) SubmitNodeCollector.CustomGeometryRenderer renderer) {
        if (renderer instanceof PooledGeometry) {
            return DeferredPoses.copy(pose);
        }
        return original.call(pose);
    }
}
//?}
