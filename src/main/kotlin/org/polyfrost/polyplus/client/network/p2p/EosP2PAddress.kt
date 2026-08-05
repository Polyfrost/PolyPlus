package org.polyfrost.polyplus.client.network.p2p

import java.net.SocketAddress
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId

data class EosP2PAddress(val user: EosProductUserId, val socket: EosP2PSocketId) : SocketAddress() {
    override fun toString(): String = "eos-p2p://${user.raw}/${socket.name}"
}
