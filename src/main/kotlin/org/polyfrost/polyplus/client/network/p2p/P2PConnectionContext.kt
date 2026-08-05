package org.polyfrost.polyplus.client.network.p2p

object P2PConnectionContext {
    @Volatile private var pendingTarget: P2PSessionManager.JoinTarget? = null

    fun setPendingJoin(target: P2PSessionManager.JoinTarget) {
        pendingTarget = target
    }

    @JvmStatic
    fun hasPendingJoin(): Boolean = pendingTarget != null

    @JvmStatic
    fun consumeAddressOverride(): EosP2PAddress? {
        val target = pendingTarget ?: return null
        pendingTarget = null
        return EosP2PAddress(target.host, target.socket)
    }
}
