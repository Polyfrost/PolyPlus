package org.polyfrost.polyplus.client.cosmetics.moments

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
//?} else {
/*import net.minecraft.network.PacketByteBuf
*///?}

//? if >= 26.1 {
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents
//?} else if > 1.8.9 {
/*import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents
*///?}

object MomentChannel {
    const val NAMESPACE = "polyplus"
    const val PATH = "moment"
    const val CHANNEL = "$NAMESPACE:$PATH"
    const val PAYLOAD_BYTES = 32
    const val MAX_BYTES = 64

    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME}/Moments")

    @Volatile
    var announced = false
        private set

    @Volatile
    private var warned = false

    fun initialize() {
        //? if >= 26.1 {
        PayloadTypeRegistry.clientboundPlay().register(MomentPayload.TYPE, MomentPayload.CODEC)
        ServerboundPlayChannelEvents.REGISTER.register { _, _, _, channels ->
            if (MomentPayload.TYPE.id() in channels) announce()
        }
        ServerboundPlayChannelEvents.UNREGISTER.register { _, _, _, channels ->
            if (MomentPayload.TYPE.id() in channels) reset()
        }
        //?} else if > 1.8.9 {
        /*PayloadTypeRegistry.playS2C().register(MomentPayload.TYPE, MomentPayload.CODEC)
        C2SPlayChannelEvents.REGISTER.register { _, _, _, channels ->
            if (MomentPayload.TYPE.id() in channels) announce()
        }
        C2SPlayChannelEvents.UNREGISTER.register { _, _, _, channels ->
            if (MomentPayload.TYPE.id() in channels) reset()
        }
        *///?}

        //? if > 1.8.9 {
        ClientPlayNetworking.registerGlobalReceiver(MomentPayload.TYPE) { payload, _ -> handle(payload.data) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            if (ClientPlayNetworking.canSend(MomentPayload.TYPE)) announce()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        //?}
    }

    //? if = 1.8.9 {
    /*@JvmStatic
    fun onLegacyPayload(channel: String, data: PacketByteBuf) {
        when (channel) {
            "REGISTER" -> if (CHANNEL in channels(data)) announce()
            "UNREGISTER" -> if (CHANNEL in channels(data)) reset()
            CHANNEL -> {
                val bytes = ByteArray(minOf(data.readableBytes(), MAX_BYTES))
                data.getBytes(data.readerIndex(), bytes)
                handle(bytes)
            }
        }
    }

    private fun channels(data: PacketByteBuf): List<String> {
        val bytes = ByteArray(data.readableBytes())
        data.getBytes(data.readerIndex(), bytes)
        return String(bytes, Charsets.UTF_8).split('\u0000')
    }
    *///?}

    @JvmStatic
    fun reset() {
        announced = false
    }

    fun decode(data: ByteArray): Pair<UUID, UUID>? {
        if (data.size != PAYLOAD_BYTES) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val killer = UUID(buffer.getLong(), buffer.getLong())
        val victim = UUID(buffer.getLong(), buffer.getLong())
        return killer to victim
    }

    private fun announce() {
        if (!announced) LOGGER.info("Server announced {}, kill effects are on", CHANNEL)
        announced = true
    }

    private fun handle(data: ByteArray) {
        if (!announced) return
        val pair = decode(data)
        if (pair == null) {
            if (!warned) {
                warned = true
                LOGGER.warn("Ignoring a {} message of {} bytes, expected {}", CHANNEL, data.size, PAYLOAD_BYTES)
            }
            return
        }
        ClientPlatform.runOnMain { Moments.onKill(pair.first, pair.second) }
    }
}
