package org.polyfrost.polyplus.client.network.eos

import java.nio.ByteBuffer

interface EosSdkBridge {
    val isLoggedIn: Boolean

    val localUser: EosProductUserId?

    suspend fun connectLogin(openIdAccessToken: String): Result<EosProductUserId>

    fun setRelayControl(allowRelays: Boolean)

    suspend fun queryNatType(): Result<NatType>

    fun setPacketQueueSize(inboundBytes: Long, outboundBytes: Long)

    fun outboundQueueBytes(socket: EosP2PSocketId): Long

    fun sendPacket(socket: EosP2PSocketId, remote: EosProductUserId, data: ByteBuffer)

    fun setInboundPacketHandler(handler: (Received) -> Unit)

    fun addConnectionRequestHandler(socket: EosP2PSocketId, handler: (remote: EosProductUserId) -> Unit): EosNotificationHandle

    fun acceptConnection(socket: EosP2PSocketId, remote: EosProductUserId)

    fun closeConnection(socket: EosP2PSocketId, remote: EosProductUserId?)

    fun addConnectionStateHandler(socket: EosP2PSocketId, handler: (event: ConnectionStateEvent) -> Unit): EosNotificationHandle

    fun removeNotificationHandler(handle: EosNotificationHandle)

    data class Received(val socket: EosP2PSocketId, val remote: EosProductUserId, val data: ByteBuffer)

    sealed interface ConnectionStateEvent {
        val remote: EosProductUserId

        data class Established(override val remote: EosProductUserId, val direct: Boolean) : ConnectionStateEvent
        data class Interrupted(override val remote: EosProductUserId) : ConnectionStateEvent
        data class Closed(override val remote: EosProductUserId, val reason: String) : ConnectionStateEvent
    }

    enum class NatType { Open, Moderate, Strict, Unknown }
}
