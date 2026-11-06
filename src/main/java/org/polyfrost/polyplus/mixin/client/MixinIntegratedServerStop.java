package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.polyfrost.polyplus.mixin.client.access.MinecraftServerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mixin(IntegratedServer.class)
public class MixinIntegratedServerStop {
    @WrapOperation(
        method = "stop()V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/util/concurrent/Futures;getUnchecked(Ljava/util/concurrent/Future;)Ljava/lang/Object;",
            remap = false
        )
    )
    private Object polyplus$waitWhileServerRuns(Future<?> future, Operation<Object> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        while (true) {
            try {
                return future.get(50L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Thread thread = ((MinecraftServerAccessor) server).polyplus$getThread();
                if (!server.isRunning() || server.hasStopped() || thread == null || !thread.isAlive()) return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                return original.call(future);
            }
        }
    }
}
*///?}
