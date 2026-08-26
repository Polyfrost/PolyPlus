package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.network.http.MinecraftLoginGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class Mixin_AutoRefreshSession {
    @Shadow
    @Final
    private ServerData serverData;

    @WrapMethod(method = "authenticateServer")
    private Component polyplus$refreshExpiredSession(String digest, Operation<Component> original) {
        SessionRefresh.beforeAuthenticate();
        Component error = polyplus$authenticate(digest, original);
        boolean disconnects = this.serverData == null || !this.serverData.isLan();
        if (error == null || !disconnects || !SessionRefresh.isInvalidSession(error)) {
            return error;
        }
        if (SessionRefresh.refreshAfterRejection()) {
            error = polyplus$authenticate(digest, original);
            if (error == null || !SessionRefresh.isInvalidSession(error)) {
                return error;
            }
        }
        SessionRefresh.onInvalidSession();
        return error;
    }

    private Component polyplus$authenticate(String digest, Operation<Component> original) {
        boolean held = MinecraftLoginGate.begin();
        try {
            return original.call(digest);
        } finally {
            MinecraftLoginGate.end(held);
        }
    }
}
