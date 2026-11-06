package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.polyfrost.polyplus.mixin.client.access.MinecraftServerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftStartGame {
    @Unique
    private static final Logger polyplus$LOGGER = LogManager.getLogger("PolyPlus");

    @Unique
    private static final long polyplus$STOP_WAIT_MILLIS = 10_000L;

    @Shadow
    private IntegratedServer server;

    @Unique
    private Thread polyplus$stoppingServer;

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
    private void polyplus$rememberStoppingServer(ClientWorld world, String message, CallbackInfo ci) {
        if (world == null && server != null) polyplus$stoppingServer = ((MinecraftServerAccessor) server).polyplus$getThread();
    }

    @Inject(method = "startGame(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V", at = @At("HEAD"))
    private void polyplus$waitForStoppingServer(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        Thread previous = polyplus$stoppingServer;
        polyplus$stoppingServer = null;
        if (previous == null || !previous.isAlive() || previous == Thread.currentThread()) return;
        try {
            previous.join(polyplus$STOP_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (previous.isAlive()) polyplus$LOGGER.debug("Previous integrated server is still stopping after {} ms; starting the new one anyway", polyplus$STOP_WAIT_MILLIS);
    }
}
*///?}
