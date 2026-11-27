package org.polyfrost.polyplus.client.cosmetics.moments

//? if > 1.8.9 {
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class MomentPayload(val data: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MomentPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<MomentPayload>(
            Identifier.fromNamespaceAndPath(MomentChannel.NAMESPACE, MomentChannel.PATH)
        )

        @JvmField
        val CODEC: StreamCodec<FriendlyByteBuf, MomentPayload> = StreamCodec.of(
            { buf, payload -> buf.writeBytes(payload.data) },
            { buf -> MomentPayload(ByteArray(minOf(buf.readableBytes(), MomentChannel.MAX_BYTES)).also { buf.readBytes(it); buf.skipBytes(buf.readableBytes()) }) },
        )
    }
}
//?}
