package org.polyfrost.polyplus.mixin.compat.skyboxify;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

//? if skyboxify {
import btw.lowercase.skyboxify.skybox.impl.Skybox;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
//?}

@Pseudo
@Mixin(targets = "btw.lowercase.skyboxify.skybox.SkyboxManager", remap = false)
public class MixinSkyboxManager {
    @Unique
    private final Matrix4f polyplus$modelView = new Matrix4f();

    @WrapOperation(
            method = "extractSkyboxes",
            at = @At(value = "NEW", target = "()Lorg/joml/Matrix4f;"),
            require = 0
    )
    private Matrix4f polyplus$reuseModelView(Operation<Matrix4f> original) {
        return this.polyplus$modelView.identity();
    }

    //? if skyboxify {
    @Shadow
    @Final
    private List<Skybox> activeSkies;

    @Inject(method = "containsEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private void polyplus$containsEnabled(ResourceKey<Level> resourceKey, CallbackInfoReturnable<Boolean> cir) {
        for (Skybox skybox : this.activeSkies) {
            if (resourceKey.equals(skybox.dimension())) {
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }
    //?}
}
