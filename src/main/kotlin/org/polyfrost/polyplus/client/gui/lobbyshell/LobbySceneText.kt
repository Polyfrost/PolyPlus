package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.polyplus.client.lobby.LobbyRealm
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneScore
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val SUNSET_START = 11500L
private const val NIGHT_START = 13500L
private const val NIGHT_END = 22500L
private const val DAY_TICKS = 24000L
private const val KILOBYTE = 1024L
private const val MEGABYTE = KILOBYTE * KILOBYTE
private const val WEEK_DAYS = 7L
private const val TENTHS = 10.0

internal const val MISSING_FACT = "-"

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

internal enum class SceneMood(val top: Long, val bottom: Long, val fadeStart: Float = 0f) {
    DAY(0xFF6BB5DE, 0xFF3E7A3C, 0.4f),
    SUNSET(0xFFE8A05A, 0xFF7B3651),
    NIGHT(0xFF283466, 0xFF13182F),
    CAVE(0xFF4C4842, 0xFF1C1B1A),
    INDOOR(0xFFB98552, 0xFF4A2F1E),
    NETHER(0xFFD2452E, 0xFF4A1216),
    END(0xFFB7A3E6, 0xFF3A2366),
}

internal fun sceneMood(entry: LobbySceneEntry): SceneMood = when {
    entry.realm == LobbyRealm.NETHER -> SceneMood.NETHER
    entry.realm == LobbyRealm.END -> SceneMood.END
    entry.setting == LobbySceneScore.Profile.CAVE -> SceneMood.CAVE
    entry.setting == LobbySceneScore.Profile.INDOOR -> SceneMood.INDOOR
    else -> when (entry.clock % DAY_TICKS) {
        in SUNSET_START until NIGHT_START -> SceneMood.SUNSET
        in NIGHT_START until NIGHT_END -> SceneMood.NIGHT
        else -> SceneMood.DAY
    }
}

internal fun sceneLine(entry: LobbySceneEntry): String {
    val world = if (entry.bundled) null else LobbySceneStore.worldName(entry).takeUnless { it.isBlank() || it.trim().equals(entry.label.trim(), ignoreCase = true) }
    return listOfNotNull(world, scenePlace(entry).takeIf { it.isNotBlank() }, sceneTime(entry)).joinToString(" · ")
}

internal fun sceneLook(entry: LobbySceneEntry): String =
    listOfNotNull(scenePlace(entry).takeIf { it.isNotBlank() }, sceneTime(entry)).joinToString(", ")

internal fun sceneFacts(entry: LobbySceneEntry): List<Pair<String, String>> {
    val place = scenePlace(entry).ifBlank { MISSING_FACT }
    val time = sceneTime(entry) ?: MISSING_FACT
    if (entry.bundled) return listOf("From" to "Polyfrost", "Place" to place, "Time" to time)
    val source = when (entry.origin) {
        LobbySceneEntry.ORIGIN_SERVER -> "Server"
        LobbySceneEntry.ORIGIN_WORLD -> "World"
        else -> "From"
    }
    val realm = when (entry.realm) {
        LobbyRealm.NETHER -> "Nether"
        LobbyRealm.END -> "The End"
        LobbyRealm.OVERWORLD -> "Overworld"
    }
    return listOf(
        source to LobbySceneStore.worldName(entry).ifBlank { MISSING_FACT },
        "Dimension" to realm,
        "Place" to place,
        "Time" to time,
        "Saved" to (sceneAge(entry.capturedAt) ?: MISSING_FACT),
        "Spot" to listOfNotNull(entry.x, entry.y, entry.z).joinToString(", "),
        "Packs" to entry.packs.joinToString(", ") { it.name }.ifBlank { MISSING_FACT },
        "Version" to entry.gameVersion.ifBlank { MISSING_FACT },
        "Size" to (sceneSize(entry.bytes) ?: MISSING_FACT),
    )
}

internal fun scenePlace(entry: LobbySceneEntry): String =
    if (entry.setting == LobbySceneScore.Profile.CAVE && entry.realm != LobbyRealm.NETHER) caveName(entry.biome) else biomeName(entry.biome)

internal fun sceneTime(entry: LobbySceneEntry): String? = when {
    entry.setting == LobbySceneScore.Profile.INDOOR -> "Indoors"
    entry.setting == LobbySceneScore.Profile.CAVE || entry.realm != LobbyRealm.OVERWORLD -> null
    else -> when (sceneMood(entry)) {
        SceneMood.SUNSET -> "Sunset"
        SceneMood.NIGHT -> "Night"
        else -> "Day"
    }
}

internal fun sceneProblem(entry: LobbySceneEntry): String? = when {
    LobbySceneStore.outdated(entry) -> "Made in another game version"
    else -> LobbySceneStore.problem(entry)
}

internal fun sceneDetail(entry: LobbySceneEntry): String =
    listOfNotNull(LobbySceneStore.worldName(entry), sceneAge(entry.capturedAt), biomeName(entry.biome).takeIf { it.isNotBlank() }).joinToString(", ")

internal fun sceneAge(capturedAt: Long, now: Long = System.currentTimeMillis()): String? {
    if (capturedAt <= 0L) return null
    val day = localDate(capturedAt)
    val days = ChronoUnit.DAYS.between(day, localDate(now))
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < WEEK_DAYS -> "$days days ago"
        else -> DATE_FORMAT.format(day)
    }
}

internal fun sceneSize(bytes: Long): String? = when {
    bytes <= 0L -> null
    bytes < MEGABYTE -> "${(bytes / KILOBYTE).coerceAtLeast(1L)} KB"
    else -> "${Math.round(bytes * TENTHS / MEGABYTE) / TENTHS} MB"
}

internal fun biomeName(biome: String): String =
    biome.substringAfter(':').replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun caveName(biome: String): String = when (biome) {
    "minecraft:lush_caves" -> "Lush cave"
    "minecraft:dripstone_caves" -> "Dripstone cave"
    "minecraft:deep_dark" -> "Deep dark"
    else -> "Cave"
}

private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
