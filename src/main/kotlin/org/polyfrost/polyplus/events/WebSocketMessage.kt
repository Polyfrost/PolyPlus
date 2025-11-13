package org.polyfrost.polyplus.events

import org.polyfrost.oneconfig.api.event.v1.events.Event
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket

class WebSocketMessage(val packet: ClientboundPacket) : Event