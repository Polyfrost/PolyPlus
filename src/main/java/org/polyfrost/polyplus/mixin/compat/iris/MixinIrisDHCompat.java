package org.polyfrost.polyplus.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.compat.iris.IrisLobbyDh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompat", remap = false)
public abstract class MixinIrisDHCompat {
    @ModifyExpressionValue(
            method = "<init>(Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;Z)V",
            at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/compat/dh/DHCompat;dhPresent:Z", opcode = Opcodes.GETSTATIC),
            remap = false,
            require = 0,
            expect = 0
    )
    private boolean polyplus$inertForLobby(boolean present, @Local(argsOnly = true) IrisRenderingPipeline pipeline) {
        return present && !IrisLobbyDh.isBuilding() && !IrisLobbyDh.isLobbyPipeline(pipeline);
    }

    @Inject(method = "checkFrame()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$noLobbyFrameCheck(CallbackInfoReturnable<Boolean> cir) {
        Boolean held = IrisLobbyDh.frameCheck();
        if (held != null) {
            cir.setReturnValue(held);
        }
    }

    @Inject(method = "hasRenderingEnabled()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 0)
    private static void polyplus$noLobbyRendering(CallbackInfoReturnable<Boolean> cir) {
        if (IrisLobbyDh.engaged()) {
            cir.setReturnValue(false);
        }
    }
}
