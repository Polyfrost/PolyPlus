package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosLogThrottle
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object Parties {
    private val LOGGER = LogManager.getLogger()

    private val tickNames = arrayOf(
        "voice",
        "scene transfer",
        "scene",
        "leader follow",
        "host join requests",
        "play together",
        "presence",
        "join",
    )

    private val tickSteps: Array<() -> Unit> = arrayOf(
        PartyVoice::tick,
        SceneTransfer::tick,
        PartyScene::tick,
        LeaderFollow::tick,
        HostJoinRequests::tick,
        PlayTogether::tick,
        PartyPresence::tick,
        PartyJoin::tick,
    )

    private val tickThrottle = EosLogThrottle()
    private val tracedSteps = HashSet<String>()

    fun initialize() {
        PartyRepository.earlyInitialize()
        PlayTogether.earlyInitialize()
        LeaderFollow.earlyInitialize()
        HostJoinRequests.earlyInitialize()
        HudManager.register(PartyVoiceHud(), PolyPlusConstants.ID, "assets/polyplus/brand/oneclient.svg")
        PartyRepository.refreshed.onEach { PartyPresence.forceSend() }.launchIn(PolyPlusClient.SCOPE)
        PolyConnection.onClosed { ClientPlatform.runOnMain(::resetSession) }
        eventHandler<TickEvent.End> {
            for (i in tickSteps.indices) {
                try {
                    tickSteps[i]()
                } catch (error: Throwable) {
                    reportTickFailure(tickNames[i], error)
                }
            }
        }
    }

    private fun reportTickFailure(name: String, error: Throwable) {
        val suppressed = tickThrottle.onMessage("$name ${error.javaClass.name}: ${error.message}", System.currentTimeMillis()) ?: return
        if (tracedSteps.add(name)) {
            LOGGER.error("Party tick step '{}' failed", name, error)
        } else {
            LOGGER.warn("Party tick step '{}' failed again ({} repeats hidden): {}", name, suppressed, error.toString())
        }
    }

    private fun resetSession() {
        PartyVoice.leave()
        PartyPresence.reset()
        PartyRepository.reset()
    }
}
