package org.polyfrost.polyplus.client.gui.replays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.polyfrost.polyplus.client.gui.lobbyshell.relativeDayText
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.polyplus.compat.replay.ReplayProvider
import org.polyfrost.polyplus.compat.replay.ReplayProviders
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val SECONDS_IN_MINUTE = 60L
private const val SECONDS_IN_HOUR = 3600L
private const val MILLIS_IN_SECOND = 1000L
private const val BYTES_STEP = 1024.0
private const val MAX_ICONS = 48
private const val ICON_MAX_WIDTH = 640
private const val ICON_MAX_HEIGHT = 360
private const val RGBA = 4
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
    var failed by mutableStateOf(false)
        private set

    private val icons = mutableStateMapOf<String, ImageBitmap>()
    private val iconOrder = LinkedHashMap<String, Unit>(MAX_ICONS, 0.75f, true)
    private val pendingIcons = HashSet<String>()
    private val missingIcons = HashSet<String>()
    private var job: Job? = null

    val providers: List<ReplayProvider> get() = ReplayProviders.available

    val enabled: Boolean get() = providers.isNotEmpty()

    fun reload() {
        val sources = providers
        if (sources.isEmpty()) {
            entries = emptyList()
            loading = false
            loaded = true
            failed = false
            return
        }
        if (job?.isActive == true) return
        loading = true
        job = PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val results = sources.map { provider -> runCatching { provider.list() } }
            val listed = results.flatMap { it.getOrDefault(emptyList()) }.sortedByDescending { it.modified }
            val keep = listed.mapTo(HashSet(), ::keyOf)
            ClientPlatform.runOnMain {
                icons.keys.retainAll(keep)
                iconOrder.keys.retainAll(keep)
                missingIcons.retainAll(keep)
                entries = listed
                failed = results.any { it.isFailure }
                loading = false
                loaded = true
            }
        }
    }

    fun icon(entry: ReplayEntry): ImageBitmap? {
        val key = keyOf(entry)
        return icons[key]?.also { iconOrder[key] }
    }

    fun requestIcon(entry: ReplayEntry) {
        val key = keyOf(entry)
        if (key in icons || key in missingIcons || !pendingIcons.add(key)) return
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val bitmap = runCatching { entry.readIcon()?.let(PolyBitmap::decode)?.let(::downscaled)?.asComposeImageBitmap() }.getOrNull()
            ClientPlatform.runOnMain {
                pendingIcons -= key
                if (bitmap == null) {
                    missingIcons += key
                    return@runOnMain
                }
                icons[key] = bitmap
                iconOrder[key] = Unit
                while (iconOrder.size > MAX_ICONS) {
                    val eldest = iconOrder.keys.first()
                    iconOrder.remove(eldest)
                    icons.remove(eldest)
                }
            }
        }
    }

    fun find(path: Path?): ReplayEntry? = path?.let { wanted -> entries.firstOrNull { it.path == wanted } }

    private fun keyOf(entry: ReplayEntry): String = "${entry.path}|${entry.modified}"
}

@Composable
internal fun rememberReplayIcon(entry: ReplayEntry): ImageBitmap? {
    val icon = ReplayLibrary.icon(entry)
    LaunchedEffect(entry.path, entry.modified, icon == null) {
        if (icon == null) ReplayLibrary.requestIcon(entry)
    }
    return icon
}

private fun downscaled(source: PolyBitmap): PolyBitmap {
    val scale = minOf(ICON_MAX_WIDTH.toFloat() / source.width, ICON_MAX_HEIGHT.toFloat() / source.height)
    if (scale >= 1f) return source
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    val src = source.pixels
    val out = ByteArray(width * height * RGBA)
    val sums = IntArray(RGBA)
    for (y in 0 until height) {
        val y0 = y * source.height / height
        val y1 = maxOf(y0 + 1, (y + 1) * source.height / height)
        for (x in 0 until width) {
            val x0 = x * source.width / width
            val x1 = maxOf(x0 + 1, (x + 1) * source.width / width)
            sums.fill(0)
            for (sy in y0 until y1) {
                var i = (sy * source.width + x0) * RGBA
                for (sx in x0 until x1) {
                    for (c in 0 until RGBA) sums[c] += src[i + c].toInt() and 0xFF
                    i += RGBA
                }
            }
            val count = (y1 - y0) * (x1 - x0)
            val o = (y * width + x) * RGBA
            for (c in 0 until RGBA) out[o + c] = (sums[c] / count).toByte()
        }
    }
    return PolyBitmap(width, height, out)
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
    get() = relativeDayText(modified)?.replaceFirstChar { it.uppercaseChar() } ?: "Unknown date"

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
