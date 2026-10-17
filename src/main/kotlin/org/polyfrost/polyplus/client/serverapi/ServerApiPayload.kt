package org.polyfrost.polyplus.client.serverapi

//? if > 1.8.9 {
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class ServerApiPayload(val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ServerApiPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<ServerApiPayload>(
            Identifier.fromNamespaceAndPath(ServerApiProtocol.NAMESPACE, ServerApiProtocol.PATH)
        )

        @JvmField
        val CODEC: StreamCodec<FriendlyByteBuf, ServerApiPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBytes(payload.data) },
            { buf -> ServerApiPayload(ByteArray(buf.readableBytes()).also { buf.readBytes(it) }) },
        )
    }
}
//?}
