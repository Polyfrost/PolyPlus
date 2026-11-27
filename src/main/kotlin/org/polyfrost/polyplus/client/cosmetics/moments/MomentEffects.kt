package org.polyfrost.polyplus.client.cosmetics.moments

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.UUID

enum class Moment(val effectName: String) {
    Join("join"),
    Respawn("respawn"),
    Death("death"),
    Kill("kill"),
}

class MomentPlay(
    val moment: Moment,
    val wearer: UUID,
    val slot: BodySlot,
    val cosmeticId: Int,
    val colorway: String?,
    val customColors: Map<String, Int>?,
    val detached: Boolean,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val sequence: Int,
)

fun interface MomentEffects {
    fun play(play: MomentPlay): Boolean

    companion object {
        val NONE: MomentEffects = MomentEffects { true }

        @Volatile
        @JvmStatic
        var current: MomentEffects = NONE
    }
}
