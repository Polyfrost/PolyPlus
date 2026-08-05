package org.polyfrost.polyplus.client.network.p2p

import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId

object P2PListenContext {
    @Volatile private var pendingSocket: EosP2PSocketId? = null
    @Volatile private var localUser: EosProductUserId? = null

    fun setPendingListen(socket: EosP2PSocketId, localUser: EosProductUserId) {
        pendingSocket = socket
        this.localUser = localUser
    }

    @JvmStatic
    fun hasPendingListen(): Boolean = pendingSocket != null

    @JvmStatic
    fun consumeSocketOverride(): EosP2PSocketId? {
        val socket = pendingSocket ?: return null
        pendingSocket = null
        return socket
    }

    @JvmStatic
    fun requireLocalUser(): EosProductUserId =
        requireNotNull(localUser) { "P2PListenContext.setPendingListen must be called before a listen redirect fires" }
}
