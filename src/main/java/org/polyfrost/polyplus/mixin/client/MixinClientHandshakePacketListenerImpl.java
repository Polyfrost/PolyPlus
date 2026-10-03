package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.network.http.MinecraftLoginGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class MixinClientHandshakePacketListenerImpl {
    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private Connection connection;

    @WrapMethod(method = "authenticateServer")
    private Component polyplus$refreshExpiredSession(String digest, Operation<Component> original) {
        SessionRefresh.beforeAuthenticate();
        boolean held = MinecraftLoginGate.begin(this.connection);
        try {
            Component error = original.call(digest);
            boolean disconnects = this.serverData == null || !this.serverData.isLan();
            if (error == null || !disconnects || !SessionRefresh.isInvalidSession(error)) {
                return error;
            }
            while (SessionRefresh.refreshAfterRejection()) {
                error = original.call(digest);
                if (error == null || !SessionRefresh.isInvalidSession(error)) {
                    return error;
                }
            }
            SessionRefresh.onInvalidSession();
            return error;
        } finally {
            MinecraftLoginGate.end(this.connection, held);
        }
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.s2c.login.HelloS2CPacket;
import org.polyfrost.polyplus.client.launcher.SessionRefresh;
import org.polyfrost.polyplus.client.network.http.MinecraftLoginGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLoginNetworkHandler.class)
public class MixinClientHandshakePacketListenerImpl {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Connection connection;

    @WrapMethod(method = "handleHello")
    private void polyplus$holdLoginGate(HelloS2CPacket packet, Operation<Void> original) {
        SessionRefresh.beforeAuthenticate();
        boolean held = MinecraftLoginGate.begin(this.connection);
        try {
            original.call(packet);
        } finally {
            MinecraftLoginGate.end(this.connection, held);
        }
    }

    @WrapOperation(
        method = "handleHello",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Lcom/mojang/authlib/GameProfile;Ljava/lang/String;Ljava/lang/String;)V",
            remap = false
        )
    )
    private void polyplus$refreshExpiredSession(
        MinecraftSessionService service,
        GameProfile profile,
        String accessToken,
        String serverId,
        Operation<Void> original
    ) {
        ServerListEntry server = this.minecraft.getCurrentServerEntry();
        if (server != null && server.isLocal()) {
            original.call(service, profile, accessToken, serverId);
            return;
        }
        try {
            original.call(service, profile, accessToken, serverId);
        } catch (Exception rejected) {
            if (!(rejected instanceof InvalidCredentialsException)) {
                throw rejected;
            }
            while (SessionRefresh.refreshAfterRejection()) {
                Session session = this.minecraft.getSession();
                try {
                    original.call(service, session.getProfile(), session.getAccessToken(), serverId);
                    return;
                } catch (Exception again) {
                    if (!(again instanceof InvalidCredentialsException)) {
                        throw again;
                    }
                }
            }
            SessionRefresh.onInvalidSession();
            throw rejected;
        }
    }

    @Inject(method = "handleLoginSuccess", at = @At("TAIL"))
    private void polyplus$loginSucceeded(CallbackInfo ci) {
        MinecraftLoginGate.onLoginSettled(this.connection);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void polyplus$loginDisconnected(CallbackInfo ci) {
        MinecraftLoginGate.onLoginSettled(this.connection);
    }
}
*///?}
