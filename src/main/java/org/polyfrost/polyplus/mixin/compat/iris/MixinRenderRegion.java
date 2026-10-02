package org.polyfrost.polyplus.mixin.compat.iris;

//? if = 26.2 {
/*import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion", priority = 1500, remap = false)
public class MixinRenderRegion {
    @Inject(method = "iris$forceClearAllBatches", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;cachedBatches:Ljava/util/Map;", opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true, require = 0, expect = 0)
    private void polyplus$skipDuplicateClear(CallbackInfo ci) {
        ci.cancel();
    }
}
*///?}
