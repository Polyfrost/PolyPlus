package org.polyfrost.polyplus.nameplate.engine.internal.track

internal object ReducedTracks {
    fun hold(tracks: ChannelTracks): ChannelTracks {
        val x = tracks.positionX
        val y = tracks.positionY
        val moving = moves(x) || moves(y)
        val molang = x?.usesMolang == true || y?.usesMolang == true
        return ChannelTracks(
            frame = tracks.frame,
            positionX = if (!moving) x else if (molang) null else first(x),
            positionY = if (!moving) y else if (molang) null else first(y),
            opacity = held(tracks.opacity),
            visible = held(tracks.visible),
        )
    }

    fun moves(track: Track?): Boolean = track != null && (track.usesMolang || track.values().toSet().size > 1)

    private fun held(track: Track?): Track? = when {
        track == null || !moves(track) -> track
        track.usesMolang -> null
        else -> first(track)
    }

    private fun first(track: Track?): Track? = track?.let { Track(intArrayOf(0), intArrayOf(it.values()[0])) }
}
