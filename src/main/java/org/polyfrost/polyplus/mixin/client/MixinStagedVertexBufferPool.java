package org.polyfrost.polyplus.mixin.client;

//? if >= 26.2 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
*///?}
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.StagedVertexBuffer$GpuBufferPool")
public class MixinStagedVertexBufferPool {
    @Unique
    private static final int POLYPLUS_MAX_IDLE_FRAMES = 60;

    @Shadow
    @Final
    private List<GpuBuffer> available;

    @Unique
    private final Reference2IntOpenHashMap<GpuBuffer> polyplus$idleFrames = new Reference2IntOpenHashMap<>();
    @Unique
    private final List<GpuBuffer> polyplus$kept = new ArrayList<>();

    @Inject(method = "acquire", at = @At("RETURN"))
    private void polyplus$resetIdle(GpuDevice device, int minSize, CallbackInfoReturnable<GpuBuffer> cir) {
        polyplus$idleFrames.removeInt(cir.getReturnValue());
    }

    @Inject(method = "endFrame", at = @At("HEAD"))
    private void polyplus$holdIdleBuffers(GpuDevice device, CallbackInfo ci) {
        for (GpuBuffer buffer : available) {
            int idle = polyplus$idleFrames.getInt(buffer) + 1;
            if (idle > POLYPLUS_MAX_IDLE_FRAMES) {
                polyplus$idleFrames.removeInt(buffer);
                buffer.close();
            } else {
                polyplus$idleFrames.put(buffer, idle);
                polyplus$kept.add(buffer);
            }
        }
        available.clear();
    }

    @Inject(method = "endFrame", at = @At("RETURN"))
    private void polyplus$restoreIdleBuffers(GpuDevice device, CallbackInfo ci) {
        available.addAll(polyplus$kept);
        polyplus$kept.clear();
    }
}
//?}
