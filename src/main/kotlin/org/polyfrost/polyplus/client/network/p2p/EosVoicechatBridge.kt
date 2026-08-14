package org.polyfrost.polyplus.client.network.p2p

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge

internal object EosVoicechatBridge {
    private val LOGGER = LogManager.getLogger()

    val SOCKET_ID = EosP2PSocketId("polyplus-voicechat")

    @Volatile private var bridge: EosSdkBridge? = null

    @Volatile private var activeClientSocket: EosVoicechatClientSocket? = null
    @Volatile private var activeServerSocket: EosVoicechatServerSocket? = null

    fun currentBridge(): EosSdkBridge? = bridge

    fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        bridge.addConnectionRequestHandler(SOCKET_ID) { remote ->
            LOGGER.info("{} wants to open a voice-chat-bridging connection with us.", remote)
            bridge.acceptConnection(SOCKET_ID, remote)
        }
    }

    internal fun registerClientSocket(socket: EosVoicechatClientSocket) {
        activeClientSocket = socket
    }

    internal fun unregisterClientSocket(socket: EosVoicechatClientSocket) {
        activeClientSocket = activeClientSocket.takeUnless { it === socket }
    }

    internal fun registerServerSocket(socket: EosVoicechatServerSocket) {
        activeServerSocket = socket
    }

    internal fun unregisterServerSocket(socket: EosVoicechatServerSocket) {
        activeServerSocket = activeServerSocket.takeUnless { it === socket }
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET_ID) return false

        val bytes = ByteArray(received.data.remaining())
        received.data.get(bytes)

        val delivered = activeClientSocket?.offer(bytes) ?: false
        val deliveredToServer = activeServerSocket?.offer(received.remote, bytes) ?: false
        if (!delivered && !deliveredToServer) {
            LOGGER.warn("Got a voice-chat-bridge packet from {} but no client or server voice socket is currently open to receive it, dropping it.", received.remote)
        }
        return true
    }
}




