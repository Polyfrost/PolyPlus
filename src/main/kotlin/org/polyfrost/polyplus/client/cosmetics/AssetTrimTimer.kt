package org.polyfrost.polyplus.client.cosmetics

import java.util.concurrent.atomic.AtomicBoolean

internal class AssetTrimTimer(private val settleTicks: Int, private val departureTicks: Int) {
    private val departed = AtomicBoolean(false)
    private var countdown = 0

    fun settle() {
        countdown = settleTicks
    }

    fun departed() {
        departed.set(true)
    }

    fun tick(): Boolean {
        if (departed.getAndSet(false) && countdown == 0) countdown = departureTicks
        return countdown > 0 && --countdown == 0
    }
}
