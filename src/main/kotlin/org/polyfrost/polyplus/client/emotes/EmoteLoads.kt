package org.polyfrost.polyplus.client.emotes

import org.polyfrost.polyplus.client.cosmetics.EmoteRevision

internal object EmoteLoads {
    enum class WheelSlot { Empty, Loading, Unloaded, Solo, Duo }

    fun retry(attempted: EmoteRevision?, current: EmoteRevision?, loaded: Boolean, attemptsLeft: Int): Boolean =
        attemptsLeft > 1 && (loaded || attempted != current)

    fun wheelSlot(defined: Boolean, ready: Boolean, loading: Boolean, duo: Boolean): WheelSlot = when {
        !defined -> WheelSlot.Empty
        !ready && loading -> WheelSlot.Loading
        !ready -> WheelSlot.Unloaded
        duo -> WheelSlot.Duo
        else -> WheelSlot.Solo
    }
}
