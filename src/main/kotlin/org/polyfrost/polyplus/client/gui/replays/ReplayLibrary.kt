package org.polyfrost.polyplus.client.gui.replays

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.polyplus.compat.replay.ReplayProvider
import org.polyfrost.polyplus.compat.replay.ReplayProviders
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L
private const val DAYS_IN_YEAR = 365L
private const val SECONDS_IN_MINUTE = 60L
private const val SECONDS_IN_HOUR = 3600L
private const val MILLIS_IN_SECOND = 1000L
private const val BYTES_STEP = 1024.0
internal const val REPLAY_SEPARATOR = " · "

private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

internal object ReplayLibrary {
    var entries by mutableStateOf<List<ReplayEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    private val icons = mutableStateMapOf<String, ImageBitmap>()
    private var job: Job? = null

    val providers: List<ReplayProvider> get() = ReplayProviders.available

    val enabled: Boolean get() = providers.isNotEmpty()

    fun reload() {
        val sources = providers
        if (sources.isEmpty()) {
            entries = emptyList()
            loading = false
            loaded = true
            return
        }
        if (job?.isActive == true) return
        loading = true
        val known = icons.keys.toSet()
        job = PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val listed = sources
                .flatMap { provider -> runCatching { provider.list() }.getOrDefault(emptyList()) }
                .sortedByDescending { it.modified }
            val decoded = listed.mapNotNull { entry ->
                val key = keyOf(entry)
                if (key in known) return@mapNotNull null
                val bytes = entry.icon?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                runCatching { PolyBitmap.decode(bytes)?.asComposeImageBitmap() }.getOrNull()?.let { key to it }
            }
            val keep = listed.mapTo(HashSet(), ::keyOf)
            ClientPlatform.runOnMain {
                icons.keys.retainAll(keep)
                decoded.forEach { (key, bitmap) -> icons[key] = bitmap }
                entries = listed
                loading = false
                loaded = true
            }
        }
    }

    fun icon(entry: ReplayEntry): ImageBitmap? = icons[keyOf(entry)]

    fun find(path: Path?): ReplayEntry? = path?.let { wanted -> entries.firstOrNull { it.path == wanted } }

    private fun keyOf(entry: ReplayEntry): String = "${entry.path}|${entry.modified}"
}

internal fun ReplayEntry.watchSafely() {
    if (playable) runCatching { provider.watch(this) }
}

internal fun ReplayProvider.openFolderSafely() {
    runCatching { openFolder() }
}

internal fun ReplayProvider.openBrowserSafely() {
    runCatching { openNativeBrowser() }
}

internal val ReplayEntry.lengthText: String?
    get() {
        val millis = durationMillis?.takeIf { it > 0L } ?: return null
        val total = millis / MILLIS_IN_SECOND
        val hours = total / SECONDS_IN_HOUR
        val minutes = total % SECONDS_IN_HOUR / SECONDS_IN_MINUTE
        val seconds = total % SECONDS_IN_MINUTE
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

internal val ReplayEntry.dateText: String
    get() {
        if (modified <= 0L) return "Unknown date"
        val zone = ZoneId.systemDefault()
        val recorded = Instant.ofEpochMilli(modified).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(recorded, LocalDate.now(zone))
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < DAYS_IN_WEEK -> "$days days ago"
            days < DAYS_IN_MONTH -> ago(days / DAYS_IN_WEEK, "week")
            days < DAYS_IN_YEAR -> ago(days / DAYS_IN_MONTH, "month")
            else -> ago(days / DAYS_IN_YEAR, "year")
        }
    }

internal val ReplayEntry.fullDateText: String
    get() = if (modified <= 0L) "Unknown" else runCatching { FULL_DATE.format(Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault())) }.getOrDefault("Unknown")

internal val ReplayEntry.sizeText: String
    get() {
        var value = size.coerceAtLeast(0L).toDouble()
        var unit = 0
        while (value >= BYTES_STEP && unit < SIZE_UNITS.lastIndex) {
            value /= BYTES_STEP
            unit++
        }
        return if (unit == 0) "${value.toLong()} ${SIZE_UNITS[unit]}" else String.format(Locale.ROOT, "%.1f %s", value, SIZE_UNITS[unit])
    }

internal val ReplayEntry.summaryText: String
    get() = listOfNotNull(lengthText, dateText).joinToString(REPLAY_SEPARATOR)

internal val ReplayEntry.problem: String
    get() = warning?.takeIf { it.isNotBlank() } ?: "Can't be opened"

private fun ago(count: Long, unit: String): String = "$count $unit${if (count == 1L) "" else "s"} ago"
