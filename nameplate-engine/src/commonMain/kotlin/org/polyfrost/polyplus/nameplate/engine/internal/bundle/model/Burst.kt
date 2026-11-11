package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule

internal class Burst(
    val schedule: Schedule?,
    val event: Int,
    val steps: IntArray,
    val counts: IntArray,
    val shot: Int = NO_SHOT,
    val reaction: Reaction? = null,
) {
    val firstStep: Int
        get() = steps.first()

    val lastStep: Int
        get() = steps.last()

    val total: Int
        get() = counts.sum()

    companion object {
        const val NO_SHOT = -1
    }
}
