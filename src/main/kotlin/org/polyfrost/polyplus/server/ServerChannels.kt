package org.polyfrost.polyplus.server

import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
//?} else {
/*import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket
*///?}

//? if >= 26.1 {
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents
//?} else if > 1.8.9 {
/*import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents
*///?}

//? if > 1.8.9 {
class RawPayload(private val type: CustomPacketPayload.Type<RawPayload>, val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RawPayload> = type
}
//?}

object ServerChannels {
    private val LOGGER = LogManager.getLogger("PolyPlus/ServerChannels")
    private val handlers = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    private val registerListeners = CopyOnWriteArrayList<(Collection<String>) -> Unit>()

    //? if > 1.8.9 {
    private val types = ConcurrentHashMap<String, CustomPacketPayload.Type<RawPayload>>()
    private var registerHooked = false
    //?} else {
    /*private val serverChannels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    *///?}

    fun register(channel: String, handler: (ByteArray) -> Unit) {
        require(handlers.putIfAbsent(channel, handler) == null) { "Channel $channel is already registered" }
        //? if > 1.8.9 {
        val type = CustomPacketPayload.Type<RawPayload>(Identifier.parse(channel))
        val codec: StreamCodec<FriendlyByteBuf, RawPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBytes(payload.data) },
            { buf -> RawPayload(type, ByteArray(buf.readableBytes()).also { buf.readBytes(it) }) },
        )
        //? if >= 26.1 {
        PayloadTypeRegistry.serverboundPlay().register(type, codec)
        PayloadTypeRegistry.clientboundPlay().register(type, codec)
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(type, codec)
        PayloadTypeRegistry.playS2C().register(type, codec)
        *///?}
        ClientPlayNetworking.registerGlobalReceiver(type) { payload, _ -> receive(channel, payload.data) }
        types[channel] = type
        hookRegisterEvent()
        //?}
    }

    fun onServerRegister(listener: (Collection<String>) -> Unit) {
        registerListeners += listener
    }

    fun canSend(channel: String): Boolean {
        //? if > 1.8.9 {
        val type = types[channel] ?: return false
        return runCatching { ClientPlayNetworking.canSend(type) }.getOrDefault(false)
        //?} else {
        /*return channel in serverChannels
        *///?}
    }

    fun send(channel: String, data: ByteArray): Boolean = runCatching {
        //? if > 1.8.9 {
        val type = types[channel] ?: return false
        if (Minecraft.getInstance().connection == null) return false
        ClientPlayNetworking.send(RawPayload(type, data))
        //?} else {
        /*val connection = Minecraft.getInstance().networkHandler ?: return false
        connection.sendPacket(CustomPayloadC2SPacket(channel, PacketByteBuf(Unpooled.wrappedBuffer(data))))
        *///?}
        true
    }.onFailure { LOGGER.warn("Could not send a plugin message on {}", channel, it) }.getOrDefault(false)

    private fun receive(channel: String, data: ByteArray) {
        val handler = handlers[channel] ?: return
        runCatching { handler(data) }.onFailure { LOGGER.error("Handler for {} failed", channel, it) }
    }

    private fun notifyRegistered(channels: Collection<String>) {
        for (listener in registerListeners) {
            runCatching { listener(channels) }.onFailure { LOGGER.error("Channel registration listener failed", it) }
        }
    }

    //? if > 1.8.9 {
    private fun hookRegisterEvent() {
        if (registerHooked) return
        registerHooked = true
        //? if >= 26.1 {
        ServerboundPlayChannelEvents.REGISTER.register { _, _, client, channels ->
            val names = channels.map { it.toString() }
            client.execute { notifyRegistered(names) }
        }
        //?} else {
        /*C2SPlayChannelEvents.REGISTER.register { _, _, client, channels ->
            val names = channels.map { it.toString() }
            client.execute { notifyRegistered(names) }
        }
        *///?}
    }
    //?}

    //? if = 1.8.9 {
    /*@JvmStatic
    fun onLegacyPayload(channel: String, data: PacketByteBuf) {
        val bytes = ByteArray(data.readableBytes())
        data.getBytes(data.readerIndex(), bytes)
        if (channel == "REGISTER") {
            val channels = String(bytes, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
            serverChannels += channels
            notifyRegistered(channels)
        } else {
            receive(channel, bytes)
        }
    }

    internal fun onLegacyJoin() {
        if (handlers.isEmpty()) return
        send("REGISTER", handlers.keys.joinToString("\u0000").toByteArray(Charsets.UTF_8))
    }

    internal fun onLegacyDisconnect() {
        serverChannels.clear()
    }
    *///?}
}
