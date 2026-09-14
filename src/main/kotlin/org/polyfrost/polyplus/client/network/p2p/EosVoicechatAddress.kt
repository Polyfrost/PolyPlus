package org.polyfrost.polyplus.client.network.p2p

import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import java.net.SocketAddress

internal data class EosVoicechatAddress(val remote: EosProductUserId) : SocketAddress()
