package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.polyfrost.polyplus.client.cosmetics.moments.Moments;
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumps;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @ModifyVariable(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String polyplus$emojiChat(String message) {
        return EmojiRegistry.toShortcodes(message);
    }

    @ModifyVariable(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String polyplus$emojiCommand(String command) {
        return EmojiRegistry.commandToShortcodes(command);
    }

    @Inject(
        method = "handleRespawn",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread", shift = At.Shift.AFTER)
    )
    private void polyplus$respawnMoment(ClientboundRespawnPacket packet, CallbackInfo ci) {
        Moments.onRespawnPacket();
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void polyplus$respawnBreaksRings(ClientboundRespawnPacket packet, CallbackInfo ci) {
        WearerJumps.localRespawned();
    }

    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread", shift = At.Shift.AFTER)
    )
    private void polyplus$positionSyncStarts(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        WearerJumps.localSyncStarted();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void polyplus$positionSyncBreaksRings(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        WearerJumps.localSyncEnded();
    }

    @Inject(
        method = "handleTeleportEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread", shift = At.Shift.AFTER)
    )
    private void polyplus$teleportBreaksRings(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        WearerJumps.entityTeleported(packet);
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void polyplus$appearBreaksRings(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        WearerJumps.entityAppeared(packet.getId());
    }
}
//?} else {
/*import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import org.polyfrost.polyplus.client.cosmetics.moments.MomentChannel;
import org.polyfrost.polyplus.client.cosmetics.moments.Moments;
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumps;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager;
import org.polyfrost.polyplus.client.serverapi.OneClientServerApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPacketListener {
    @ModifyVariable(method = "sendPacket", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Packet polyplus$emojiChat(Packet packet) {
        if (packet instanceof ChatMessageC2SPacket chat) {
            String message = chat.getMessage();
            String converted = message.startsWith("/") ? EmojiRegistry.commandToShortcodes(message) : EmojiRegistry.toShortcodes(message);
            if (!converted.equals(message)) {
                return new ChatMessageC2SPacket(converted);
            }
        }
        return packet;
    }

    @Inject(method = "closeWorld", at = @At("HEAD"))
    private void polyplus$onDisconnect(CallbackInfo ci) {
        P2PSessionManager.onClientDisconnected();
        OneClientServerApi.reset();
        MomentChannel.reset();
    }

    @Inject(
        method = "handleCustomPayload",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$oneClientServerApi(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        OneClientServerApi.onLegacyPayload(packet.getChannel(), packet.getData());
        MomentChannel.onLegacyPayload(packet.getChannel(), packet.getData());
    }

    @Inject(
        method = "handlePlayerRespawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$respawnMoment(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        Moments.onRespawnPacket();
    }

    @Inject(method = "handlePlayerRespawn", at = @At("TAIL"))
    private void polyplus$respawnBreaksRings(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        WearerJumps.localRespawned();
    }

    @Inject(
        method = "handlePlayerMove",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$positionSyncStarts(PlayerMoveS2CPacket packet, CallbackInfo ci) {
        WearerJumps.localSyncStarted();
    }

    @Inject(method = "handlePlayerMove", at = @At("TAIL"))
    private void polyplus$positionSyncBreaksRings(PlayerMoveS2CPacket packet, CallbackInfo ci) {
        WearerJumps.localSyncEnded();
    }

    @Inject(
        method = "handleEntityTeleport",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
            shift = At.Shift.AFTER
        )
    )
    private void polyplus$teleportBreaksRings(EntityTeleportS2CPacket packet, CallbackInfo ci) {
        WearerJumps.entityTeleported(packet);
    }

    @Inject(method = "handleAddPlayer", at = @At("TAIL"))
    private void polyplus$appearBreaksRings(AddPlayerS2CPacket packet, CallbackInfo ci) {
        WearerJumps.entityAppeared(packet.getId());
    }
}
*///?}
