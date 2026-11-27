package org.polyfrost.polyplus.client.cosmetics.signals

interface PlayerSignalsRenderAccess {
    fun `polyplus$signals`(): PlayerSignalRecord?

    fun `polyplus$setSignals`(record: PlayerSignalRecord?)
}
