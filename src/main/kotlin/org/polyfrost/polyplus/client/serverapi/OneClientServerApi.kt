package org.polyfrost.polyplus.client.serverapi

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import java.util.concurrent.atomic.AtomicBoolean

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
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

object OneClientServerApi {
    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME}/ServerApi")
    private val helloSent = AtomicBoolean(false)

    @Volatile
    var serverApiVersion: String? = null
        private set

    val isConnected: Boolean
        get() = serverApiVersion != null

    fun initialize() {
        //? if >= 26.1 {
        PayloadTypeRegistry.serverboundPlay().register(ServerApiPayload.TYPE, ServerApiPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(ServerApiPayload.TYPE, ServerApiPayload.CODEC)
        ServerboundPlayChannelEvents.REGISTER.register { _, _, _, channels ->
            if (ServerApiPayload.TYPE.id() in channels) sendHello()
        }
        //?} else if > 1.8.9 {
        /*PayloadTypeRegistry.playC2S().register(ServerApiPayload.TYPE, ServerApiPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ServerApiPayload.TYPE, ServerApiPayload.CODEC)
        C2SPlayChannelEvents.REGISTER.register { _, _, _, channels ->
            if (ServerApiPayload.TYPE.id() in channels) sendHello()
        }
        *///?}

        //? if > 1.8.9 {
        ClientPlayNetworking.registerGlobalReceiver(ServerApiPayload.TYPE) { payload, _ -> handle(payload.data) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            if (ClientPlayNetworking.canSend(ServerApiPayload.TYPE)) sendHello()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        //?}
    }

    //? if = 1.8.9 {
    /*@JvmStatic
    fun onLegacyPayload(channel: String, data: PacketByteBuf) {
        when (channel) {
            "REGISTER" -> {
                val channels = data.toString(Charsets.UTF_8).split('\u0000')
                if (ServerApiProtocol.CHANNEL in channels) sendHello()
            }
            ServerApiProtocol.CHANNEL -> {
                val bytes = ByteArray(data.readableBytes())
                data.getBytes(data.readerIndex(), bytes)
                handle(bytes)
            }
        }
    }
    *///?}

    @JvmStatic
    fun reset() {
        helloSent.set(false)
        serverApiVersion = null
    }

    private fun sendHello() {
        if (!helloSent.compareAndSet(false, true)) return

        val minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")
        val hello = ServerApiProtocol.encodeHello(PolyPlusConstants.VERSION, minecraftVersion)

        //? if > 1.8.9 {
        ClientPlayNetworking.send(ServerApiPayload(hello))
        //?} else {
        /*val connection = Minecraft.getInstance().networkHandler ?: return
        connection.sendPacket(CustomPayloadC2SPacket("REGISTER", PacketByteBuf(Unpooled.wrappedBuffer(ServerApiProtocol.CHANNEL.toByteArray(Charsets.UTF_8)))))
        connection.sendPacket(CustomPayloadC2SPacket(ServerApiProtocol.CHANNEL, PacketByteBuf(Unpooled.wrappedBuffer(hello))))
        *///?}
    }

    private fun handle(data: ByteArray) {
        val packet = runCatching { ServerApiProtocol.decode(data) }
            .onFailure { LOGGER.warn("Ignoring malformed OneClient server API packet: {}", it.message) }
            .getOrNull()

        when (packet) {
            is ServerApiProtocol.Welcome -> {
                serverApiVersion = packet.serverApiVersion
                LOGGER.info("Server supports the OneClient server API {} (protocol {})", packet.serverApiVersion, packet.protocolVersion)
            }
            null -> Unit
        }
    }
}
