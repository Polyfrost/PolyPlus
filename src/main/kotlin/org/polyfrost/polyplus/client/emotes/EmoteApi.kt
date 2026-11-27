package org.polyfrost.polyplus.client.emotes

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
*///?}
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.gui.EmoteWheelOverlay
import org.polyfrost.polyplus.client.lobby.LobbyAvatar
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object EmoteApi {
    private val LOGGER = LogManager.getLogger("polyplus/emotes")

    private val playRequests = AtomicLong()

    private val plays = EmotePlays(
        stop = { emoteId, startTime ->
            PolyConnection.sendPacket(ServerboundPacket.StopEmote(emoteId, startTime))
                .onFailure { LOGGER.warn("Failed to cancel emote {}", emoteId, it) }
                .isSuccess
        },
        drop = CosmeticSync::dropLocalEmote,
    )

    private val gate = EmotePlayGate()

    class PlayCancelled : IllegalStateException("The emote was cancelled before it was sent")

    fun stop(player: AbstractClientPlayer) {
        (player as PlayerEmotesAccess).`polyplus$emoteController`().stop()
    }

    internal fun stopNow(player: AbstractClientPlayer) {
        val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`()
        controller.stop()
        controller.takeEnd()?.let { onEmoteEnded(player, it) }
    }

    internal fun playsSolo(player: AbstractClientPlayer): Boolean =
        (player as PlayerEmotesAccess).`polyplus$emoteController`().playsSolo

    internal fun playRequestCount(): Long = playRequests.get()

    fun playTicket(): Long = gate.ticket()

    internal fun playable(ticket: Long?): Boolean = gate.open(ticket)

    internal fun dropQueuedPlays() {
        gate.cancel { }
    }

    internal fun requestPlay(emoteId: Int, anchor: EmoteAnchor?, ticket: Long? = null): Result<Unit> =
        gate.send(ticket) {
            val requestId = CosmeticSync.nextRequestId()
            plays.send(requestId, emoteId) { countPlay(ServerboundPacket.PlayEmote(emoteId, anchor, requestId)) }
        } ?: Result.failure(PlayCancelled())

    internal fun cancelSentPlay(): Boolean = gate.cancel { plays.cancel() }

    internal fun onPlayFailed(requestId: Long?) {
        if (requestId != null) plays.failed(requestId)
    }

    internal fun forgetUnanswered() = plays.clear()

    internal fun onLocalApplied(emoteId: Int, startTime: Long) = plays.applied(emoteId, startTime)

    internal fun skipsEcho(player: String, emoteId: Int, startTime: Long?, requestId: Long?): Boolean {
        val uuid = runCatching { UUID.fromString(player) }.getOrNull() ?: return false
        if (uuid != ClientPlatform.localPlayerUuid()) return false
        return plays.skips(emoteId, startTime, requestId)
    }

    internal fun requestSharedStart(requestId: String, anchor: EmoteAnchor): Result<Unit> =
        countPlay(ServerboundPacket.SharedEmoteStart(requestId, anchor))

    private fun countPlay(packet: ServerboundPacket): Result<Unit> =
        PolyConnection.sendPacket(packet).onSuccess { playRequests.incrementAndGet() }

    internal fun onEmoteEnded(player: AbstractClientPlayer, ended: EndedEmote) {
        if (!isLocal(player)) return
        if (ended.dev) {
            CosmeticSync.dropLocalDevEmote(ended.emoteId)
            return
        }
        if (ended.startTime == null && ended.automatic && playRequests.get() != ended.playRequests) return
        PolyConnection.sendPacket(ServerboundPacket.StopEmote(ended.emoteId, ended.startTime)).onFailure {
            LOGGER.warn("Failed to tell the server the emote ended", it)
        }
    }

    private fun isLocal(player: AbstractClientPlayer): Boolean =
        player === Minecraft.getInstance().player || (player is LobbyAvatar && player.isLocal)

    fun playOwnedEmote(player: AbstractClientPlayer, emoteId: Int): Boolean {
        if (emoteId !in CosmeticCatalog.ownedEmoteIds()) return false
        if (player !== Minecraft.getInstance().player) return playOwnedEmote(emoteId)
        EmoteWheelOverlay.pick(emoteId, true)
        return true
    }

    fun playOwnedEmote(emoteId: Int): Boolean {
        if (emoteId !in CosmeticCatalog.ownedEmoteIds()) {
            return false
        }
        return CosmeticService.playEmote(emoteId).isSuccess
    }
}

internal class EndedEmote(
    val emoteId: Int,
    val startTime: Long?,
    val playRequests: Long,
    val automatic: Boolean,
    val dev: Boolean = false,
)
