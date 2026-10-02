package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if = 26.2 {
/*import java.util.Collection;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vertices.ImmediateState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

//? if >= 26.3 {
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder", priority = 1500)
//?} else {
/*@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", priority = 1500)
*///?}
public class MixinGlCommandEncoder {
    private static final String IRIS_MIXIN = "net.irisshaders.iris.mixin.MixinGlCommandEncoder";
    //? if >= 26.3 {
    private static final String LAST_PASS = "Lcom/mojang/renderpearl/backend/opengl/GlCommandEncoder;lastPass:Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;";
    private static final String GET_CUSTOM_PASS = "Lcom/mojang/renderpearl/backend/opengl/GlRenderPass;iris$getCustomPass()Lnet/irisshaders/iris/mixinterface/CustomPass;";
    //?} else {
    /*private static final String LAST_PASS = "Lcom/mojang/blaze3d/opengl/GlCommandEncoder;lastPass:Lcom/mojang/blaze3d/opengl/GlRenderPass;";
    private static final String GET_CUSTOM_PASS = "Lcom/mojang/blaze3d/opengl/GlRenderPass;iris$getCustomPass()Lnet/irisshaders/iris/mixinterface/CustomPass;";
    *///?}

    @Unique
    private CustomPass polyplus$customPass;

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$bypassSetup")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), require = 0, expect = 0)
    //? if >= 26.3 {
    private void polyplus$readCustomPass(@Coerce Object pass, CallbackInfo irisCi, CallbackInfo ci) {
    //?} else {
    /*private void polyplus$readCustomPass(@Coerce Object pass, Collection<String> uniforms, CallbackInfoReturnable<Boolean> irisCir, CallbackInfo ci) {
    *///?}
        polyplus$customPass = ((RenderPassInterface) pass).iris$getCustomPass();
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$bypassSetup")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/Iris;isPackInUseQuick()Z"), require = 0, expect = 0)
    private boolean polyplus$customPassFirst(Operation<Boolean> original) {
        return polyplus$customPass != null || original.call();
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$bypassSetup")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = GET_CUSTOM_PASS), require = 0, expect = 0)
    private CustomPass polyplus$cachedCustomPass(@Coerce Object pass, Operation<CustomPass> original) {
        return polyplus$customPass;
    }

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$bypassSetup")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "FIELD", target = LAST_PASS, opcode = Opcodes.PUTSTATIC), require = 0, expect = 0)
    private void polyplus$dropLastPass(@Coerce Object pass, Operation<Void> original) {
    }

    //? if = 26.2 {
    /*// draw buffers already set for the open render pass while no shaderpack was active (only one pass is open at a time)
    @Unique
    private boolean polyplus$drawBuffersSet;

    @TargetHandler(mixin = IRIS_MIXIN, name = "iris$skipShadowDrawBuffers")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawBuffers([I)V"), require = 0, expect = 0)
    private void polyplus$drawBuffersOncePerPass(int[] buffers, Operation<Void> original) {
        if (Iris.isPackInUseQuick() || ImmediateState.safeToMultiply) {
            polyplus$drawBuffersSet = false;
        } else if (polyplus$drawBuffersSet) {
            return;
        } else {
            polyplus$drawBuffersSet = true;
        }

        original.call(buffers);
    }

    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void polyplus$resetDrawBuffers(CallbackInfoReturnable<?> cir) {
        polyplus$drawBuffersSet = false;
    }
    *///?}
}
//?}
