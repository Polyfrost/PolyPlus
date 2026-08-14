package org.polyfrost.polyplus.client.network.p2p

sealed class EosStatus {
    data object Connecting : EosStatus()
    data object Ready : EosStatus()
    data class Failed(val reason: String) : EosStatus()
}