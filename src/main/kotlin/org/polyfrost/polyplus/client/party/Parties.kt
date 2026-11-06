package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.ShutdownEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object Parties {
    private val LOGGER = LogManager.getLogger()

    private val ticks: List<() -> Unit> = listOf(
        PartyVoice::tick,
        SceneTransfer::tick,
        PartyScene::tick,
        LeaderFollow::tick,
        PlayTogether::tick,
        PartyPresence::tick,
        PartyJoin::tick,
    )

    fun initialize() {
        PartyRepository.earlyInitialize()
        PlayTogether.earlyInitialize()
        HudManager.register(PartyVoiceHud(), PolyPlusConstants.ID, "assets/polyplus/brand/oneclient.svg")
        PartyRepository.refreshed.onEach { PartyPresence.forceSend() }.launchIn(PolyPlusClient.SCOPE)
        eventHandler<ShutdownEvent> { PartyVoice.onShutdown() }
        eventHandler<TickEvent.End> {
            ticks.forEach { step -> runCatching(step).onFailure { LOGGER.error("Party tick failed", it) } }
        }
    }
}
