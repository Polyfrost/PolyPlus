package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ClientLevel.ClientLevelData.class, priority = 1500)
public class MixinClientLevelData {
    @TargetHandler(mixin = "net.irisshaders.iris.mixin.sky.MixinClientLevelData_DisableVoidPlane", name = "iris$getHorizonHeight")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"), require = 0, expect = 0)
    private FogType polyplus$extractedFogType(Camera camera, Operation<FogType> original) {
        return Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogType;
    }
}
//?}
