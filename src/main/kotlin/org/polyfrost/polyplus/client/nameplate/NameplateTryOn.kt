package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object NameplateTryOn {
    class Trial internal constructor(
        val title: String,
        val choices: List<NameplateChoice>,
        val index: Int,
        val expiresAt: Long,
    ) {
        val choice: NameplateChoice
            get() = choices[index]

        fun remainingMillis(now: Long = System.currentTimeMillis()): Long = (expiresAt - now).coerceAtLeast(0L)
    }

    private const val DURATION_MS = 120_000L
    private const val MAX_PENDING_SHOTS = 8

    @Volatile
    private var trial: Trial? = null

    @Volatile
    var reactions: Int = 0
        private set

    private val shots = ConcurrentLinkedQueue<String>()

    private val pulseEnds = LongArray(Reaction.entries.size)

    @Volatile
    private var pulsing = 0L

    fun start(title: String, choices: List<NameplateChoice>, index: Int): Trial? {
        if (choices.isEmpty()) return null
        val started = Trial(title, choices, index.coerceIn(choices.indices), System.currentTimeMillis() + DURATION_MS)
        resetMotion()
        trial = started
        return started
    }

    fun select(index: Int) {
        val current = active() ?: return
        if (index in current.choices.indices) {
            trial = Trial(current.title, current.choices, index, current.expiresAt)
            shots.clear()
        }
    }

    fun stop() {
        trial = null
        resetMotion()
    }

    fun toggleReaction(bit: Int) {
        if (active() != null) reactions = reactions xor bit
    }

    fun pulseReaction(reaction: Reaction, millis: Long) {
        if (active() == null) return
        val end = System.currentTimeMillis() + millis
        pulseEnds[reaction.ordinal] = end
        pulsing = maxOf(pulsing, end)
    }

    fun fire(oneShot: String) {
        if (active() == null || shots.size >= MAX_PENDING_SHOTS) return
        shots += oneShot
    }

    fun reactionMask(player: UUID): Int {
        val mask = reactions or pulsedMask()
        return if (mask != 0 && active() != null && player == ClientPlatform.localPlayerUuid()) mask else 0
    }

    private fun pulsedMask(): Int {
        val now = System.currentTimeMillis()
        if (now >= pulsing) return 0
        var mask = 0
        for (reaction in Reaction.entries) {
            if (pulseEnds[reaction.ordinal] > now) mask = mask or reaction.bit
        }
        return mask
    }

    fun drainShots(player: UUID): List<String> {
        if (shots.isEmpty() || player != ClientPlatform.localPlayerUuid()) return emptyList()
        if (active() == null) {
            shots.clear()
            return emptyList()
        }
        val drained = ArrayList<String>(shots.size)
        var shot = shots.poll()
        while (shot != null) {
            drained += shot
            shot = shots.poll()
        }
        return drained
    }

    private fun resetMotion() {
        reactions = 0
        pulseEnds.fill(0L)
        pulsing = 0L
        shots.clear()
    }

    fun active(): Trial? {
        val current = trial ?: return null
        if (current.remainingMillis() > 0L) return current
        trial = null
        resetMotion()
        return null
    }

    fun shownOnOwnTag(): Boolean =
        NameplateBinding.settings.enabled &&
            PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, ClientPlatform.localPlayerUuid())

    fun boundFor(player: UUID): BoundNameplate? {
        val current = active() ?: return null
        if (player != ClientPlatform.localPlayerUuid() || !PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, player)) return null
        return NameplateRegistry.bind(player, current.choice.bundle, current.choice.variant)
    }
}
