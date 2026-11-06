package org.polyfrost.polyplus.client.gui.stats

import java.util.Locale

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val CM_PER_METRE = 100L
private const val METRES_PER_KM = 1_000.0

internal fun formatCount(value: Long): String = "%,d".format(Locale.ROOT, value)

internal fun formatDuration(millis: Long): String {
    val minutes = millis / MILLIS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        minutes < 1L -> "<1m"
        hours == 0L -> "${minutes}m"
        else -> "${formatCount(hours)}h ${minutes % MINUTES_PER_HOUR}m"
    }
}

internal fun formatDistance(centimetres: Long): String {
    val metres = centimetres / CM_PER_METRE
    return if (metres < METRES_PER_KM) "${formatCount(metres)} m" else "%,.1f km".format(Locale.ROOT, metres / METRES_PER_KM)
}

internal fun plural(count: Long, one: String, many: String): String = if (count == 1L) one else many
