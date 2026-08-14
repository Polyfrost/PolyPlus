package org.polyfrost.polyplus.client.network.p2p

import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent
import org.apache.logging.log4j.LogManager

class P2PVoicechatPlugin : VoicechatPlugin {
    private val LOGGER = LogManager.getLogger()

    override fun getPluginId(): String = "polyplus"

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(ClientVoicechatInitializationEvent::class.java, ::onClientInit)
        registration.registerEvent(VoicechatServerStartingEvent::class.java, ::onServerStarting)
    }

    private fun onClientInit(event: ClientVoicechatInitializationEvent) {
        val host = P2PChannelRegistry.connectedPeers().firstOrNull() ?: return
        val bridge = EosVoicechatBridge.currentBridge()
        if (bridge == null) {
            LOGGER.warn("Joined a world over PolyPlus P2P, but the EOS bridge isn't installed. We can't route voice chat over it, falling back to default UDP (probably won't reach the host).")
            return
        }

        LOGGER.info("Routing Simple Voice Chat through P2P (host: {}).", host)
        event.socketImplementation = EosVoicechatClientSocket(bridge, host)
    }

    private fun onServerStarting(event: VoicechatServerStartingEvent) {
        if (P2PSessionManager.currentSessionId == null) {
            return
        }
        val bridge = EosVoicechatBridge.currentBridge()
        if (bridge == null) {
            LOGGER.warn("Hosting a world over PolyPlus P2P, but the EOS bridge isn't installed. We can't route voice chat over it, falling back to default UDP (probably won't reach the peers).")
            return
        }

        LOGGER.info("Routing Simple Voice Chat through P2P.")
        event.socketImplementation = EosVoicechatServerSocket(bridge)
    }
}
