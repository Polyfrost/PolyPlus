package org.polyfrost.polyplus.client.emotes

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.emotes.playback.EmoteScene

class Emote internal constructor(
    val id: Identifier,
    internal val scene: EmoteScene,
    val allowsVanillaPose: Boolean,
    val lengthSeconds: Float = 0f,
    val roles: List<EmoteMark> = emptyList(),
    val loops: Boolean = false,
    val requirement: EmoteRequirement = EmoteRequirement.FLOOR,
    internal val fallbackClip: Int = NO_FALLBACK,
    internal val propsTexture: Identifier? = null,
    internal val allowsWalking: Boolean = false,
    internal val mainClip: Int = 0,
    internal val allowsSprinting: Boolean = false,
    internal val allowsCrouching: Boolean = false,
    internal val allowsFalling: Boolean = true,
    internal val allowsGliding: Boolean = false,
    internal val allowsSwimming: Boolean = false,
    internal val mainDelay: Float = 0f,
    internal val controlled: Boolean = false,
) {
    val isDuo: Boolean
        get() = roles.size >= 2

    val hasFallback: Boolean
        get() = fallbackClip != NO_FALLBACK

    internal fun roleIndex(seat: Int): Int {
        val found = ROLE_IDS.getOrNull(seat)?.let { id -> roles.indexOfFirst { it.id == id } } ?: -1
        return if (found >= 0) found else seat
    }

    internal fun mark(seat: Int): EmoteMark? = roles.getOrNull(roleIndex(seat))

    internal fun roleSeconds(index: Int): Float =
        roles.getOrNull(index)?.seconds?.takeIf { it > 0f } ?: lengthSeconds

    internal fun roleLoops(index: Int): Boolean =
        roles.getOrNull(index)?.takeIf { it.seconds > 0f }?.loops ?: loops

    internal fun roleDelay(index: Int): Float =
        roles.getOrNull(index)?.takeIf { it.seconds > 0f }?.delay ?: mainDelay

    internal fun restrains(signals: PlayerSignalRecord): Boolean {
        val moving = signals.flag(PlayerSignals.IS_MOVING)
        return !allowsWalking && moving ||
            !allowsSprinting && moving && signals.flag(PlayerSignals.IS_SPRINTING) ||
            !allowsCrouching && signals.flag(PlayerSignals.IS_SNEAKING) ||
            !allowsFalling && signals.flag(PlayerSignals.IS_FALLING) ||
            !allowsGliding && signals.flag(PlayerSignals.IS_GLIDING) ||
            !allowsSwimming && signals.flag(PlayerSignals.IS_SWIMMING) ||
            signals.flag(PlayerSignals.IS_SLEEPING)
    }

    internal fun clip(anchored: Boolean): Int =
        if (!anchored && requirement != EmoteRequirement.FLOOR && hasFallback) fallbackClip else mainClip

    internal companion object {
        const val NO_FALLBACK = -1
        const val INITIATOR = 0
        const val PARTNER = 1
        private val ROLE_IDS = arrayOf("a", "b")

        fun roleDone(seconds: Float, loops: Boolean, elapsedMs: Long, delay: Float = 0f): Boolean =
            !loops && seconds > 0f && delay.isFinite() && elapsedMs >= (delay.coerceAtLeast(0f) + seconds) * 1000.0
    }
}

enum class EmoteRequirement(val key: String) {
    FLOOR("floor"),
    LEDGE("ledge"),
    WALL("wall");

    companion object {
        fun of(key: String?): EmoteRequirement = entries.firstOrNull { it.key == key } ?: FLOOR
    }
}

data class EmoteRole(
    val index: Int,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val dz: Float = 0f,
    val yaw: Float = 0f,
)

data class EmoteMark(
    val id: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val yaw: Float = 0f,
    val seconds: Float = 0f,
    val loops: Boolean? = null,
    val delay: Float = 0f,
)
