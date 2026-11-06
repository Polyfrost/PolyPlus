package org.polyfrost.polyplus.client.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

internal class RankedPlace(val id: String, val name: String, val millis: Long)

internal class DayPlay(val label: String, val millis: Long, val today: Boolean)

internal class StatsReport(
    val player: UUID,
    val worlds: List<RankedPlace>,
    val servers: List<RankedPlace>,
    val days: List<DayPlay>,
    val sessions: Int,
    val sessionMillis: Long,
    val totals: Map<VanillaStat, Long>,
    val blocksMined: Long,
    val itemsCrafted: Long,
    val topMined: String?,
    val topKilled: String?,
) {
    val worldMillis: Long = worlds.sumOf { it.millis }
    val serverMillis: Long = servers.sumOf { it.millis }
    val totalMillis: Long = worldMillis + serverMillis
    val weekMillis: Long = days.sumOf { it.millis }
    val empty: Boolean = worlds.isEmpty() && servers.isEmpty() && totals.values.all { it == 0L } && blocksMined == 0L
}

internal object LocalStats {
    private val LOGGER = LogManager.getLogger("PolyPlus/Stats")
    private const val MILLIS_PER_TICK = 50L
    private const val WEEK_DAYS = 7L
    private val STATS_FOLDERS = listOf("players/stats", "stats")

    var report by mutableStateOf<StatsReport?>(null)
        private set

    fun refresh() {
        val player = ClientPlatform.localPlayerUuid()
        if (report?.player != player) report = null
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val next = build(player, readWorlds(player), PlaySessions.log(player))
            ClientPlatform.runOnMain { if (ClientPlatform.localPlayerUuid() == player) report = next }
        }
    }

    private fun readWorlds(player: UUID): Map<String, StatTally> {
        val saves = File(FabricLoader.getInstance().gameDir.toFile(), "saves")
        return saves.listFiles().orEmpty().mapNotNull { world ->
            val stats = STATS_FOLDERS.map { File(world, "$it/$player.json") }.filter(File::isFile).maxByOrNull(File::lastModified)
                ?: return@mapNotNull null
            runCatching { world.name to VanillaStats.parse(stats.readText()) }
                .onFailure { LOGGER.warn("Could not read the stats of {}", world.name, it) }
                .getOrNull()
        }.toMap()
    }

    private fun build(player: UUID, worlds: Map<String, StatTally>, log: PlayLog): StatsReport {
        val tallies = worlds.values
        return StatsReport(
            player,
            rankedWorlds(worlds, log),
            log.spots.filter { it.value.server }
                .map { (key, spot) -> RankedPlace(key.removePrefix(PlaySessions.SERVER_PREFIX), spot.name, spot.millis) }
                .sortedByDescending { it.millis },
            lastWeek(log),
            log.spots.values.sumOf { it.sessions },
            log.spots.values.sumOf { it.millis },
            VanillaStat.entries.associateWith { stat -> tallies.sumOf { it.values[stat] ?: 0L } },
            tallies.sumOf { it.mined.values.sum() },
            tallies.sumOf { it.crafted },
            top(tallies.map { it.mined }),
            top(tallies.map { it.killed }),
        )
    }

    private fun rankedWorlds(worlds: Map<String, StatTally>, log: PlayLog): List<RankedPlace> {
        val saved = worlds.mapNotNull { (id, tally) ->
            val ticks = tally.values[VanillaStat.PlayTicks] ?: 0L
            if (ticks <= 0L) return@mapNotNull null
            RankedPlace(id, log.spots[PlaySessions.WORLD_PREFIX + id]?.name ?: id, ticks * MILLIS_PER_TICK)
        }
        val gone = log.spots.filterNot { (key, spot) -> spot.server || key.removePrefix(PlaySessions.WORLD_PREFIX) in worlds }
            .map { (key, spot) -> RankedPlace(key.removePrefix(PlaySessions.WORLD_PREFIX), spot.name, spot.millis) }
        return (saved + gone).sortedByDescending { it.millis }
    }

    private fun lastWeek(log: PlayLog): List<DayPlay> {
        val today = LocalDate.now()
        return (WEEK_DAYS - 1 downTo 0L).map { back ->
            val day = today.minusDays(back)
            DayPlay(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), log.days[day.toEpochDay()] ?: 0L, back == 0L)
        }
    }

    private fun top(counts: List<Map<String, Long>>): String? {
        val merged = HashMap<String, Long>()
        counts.forEach { map -> map.forEach { (key, value) -> merged.merge(key, value, Long::plus) } }
        return merged.maxByOrNull { it.value }?.key
    }
}
