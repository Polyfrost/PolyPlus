package org.polyfrost.polyplus.client.emotes.duo

internal enum class DuoStop {
    Shared,
    Quiet,
    Solo,
    ;

    companion object {
        fun plan(markerCancelled: Boolean, inSession: Boolean, requestsOpen: Boolean, askCancelled: Boolean): DuoStop = when {
            inSession || requestsOpen -> Shared
            markerCancelled || askCancelled -> Quiet
            else -> Solo
        }

        fun stopsSolo(plan: DuoStop, soloPlaying: Boolean): Boolean = plan == Solo || soloPlaying
    }
}
