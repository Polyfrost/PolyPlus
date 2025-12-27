package org.polyfrost.polyplus.client.events

import dev.deftu.omnicore.api.events.CancellableEvent
import net.minecraft.network.Packet

data class ReceivePacketEvent(val packet: Packet<*>) : CancellableEvent {
    override var isCancelled: Boolean = false
}
