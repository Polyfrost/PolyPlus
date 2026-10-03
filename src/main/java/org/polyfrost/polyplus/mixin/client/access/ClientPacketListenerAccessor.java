package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Mutable
    @Accessor("registryAccess")
    void polyplus$setRegistryAccess(RegistryAccess.Frozen registryAccess);

    @Mutable
    @Accessor("scoreboard")
    void polyplus$setScoreboard(Scoreboard scoreboard);
}
//?} else {
/*import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPacketListenerAccessor {
}
*///?}
