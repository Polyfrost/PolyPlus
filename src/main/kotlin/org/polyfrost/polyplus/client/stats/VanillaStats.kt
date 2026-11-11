package org.polyfrost.polyplus.client.stats

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.polyfrost.polyplus.client.PolyPlusClient
import java.util.Locale

internal enum class VanillaStat(val nested: List<String>, val flat: List<String>) {
    PlayTicks(listOf("play_time", "play_one_minute"), listOf("stat.playOneMinute")),
    Deaths(listOf("deaths"), listOf("stat.deaths")),
    MobKills(listOf("mob_kills"), listOf("stat.mobKills")),
    PlayerKills(listOf("player_kills"), listOf("stat.playerKills")),
    WalkedCm(listOf("walk_one_cm", "sprint_one_cm", "crouch_one_cm"), listOf("stat.walkOneCm")),
    Jumps(listOf("jump"), listOf("stat.jump")),
    FishCaught(listOf("fish_caught"), listOf("stat.fishCaught")),
    VillagerTrades(listOf("traded_with_villager"), listOf("stat.tradedWithVillager")),
    AnimalsBred(listOf("animals_bred"), listOf("stat.animalsBred")),
}

internal class StatTally(
    val values: Map<VanillaStat, Long>,
    val mined: Map<String, Long>,
    val killed: Map<String, Long>,
    val crafted: Long,
)

internal object VanillaStats {
    private const val NAMESPACE = "minecraft:"
    private const val MINED_PREFIX = "stat.mineBlock."
    private const val KILLED_PREFIX = "stat.killEntity."
    private const val CRAFTED_PREFIX = "stat.craftItem."
    private val WORD_BREAK = Regex("(?<=[a-z])(?=[A-Z])")

    fun parse(text: String): StatTally {
        val root = PolyPlusClient.JSON.parseToJsonElement(text).jsonObject
        val nested = root["stats"] as? JsonObject
        return if (nested != null) readNested(nested) else readFlat(root)
    }

    private fun readNested(stats: JsonObject): StatTally {
        val custom = counts(stats[NAMESPACE + "custom"])
        return StatTally(
            VanillaStat.entries.associateWith { stat -> stat.nested.sumOf { custom[NAMESPACE + it] ?: 0L } },
            named(counts(stats[NAMESPACE + "mined"])),
            named(counts(stats[NAMESPACE + "killed"])),
            counts(stats[NAMESPACE + "crafted"]).values.sum(),
        )
    }

    private fun readFlat(root: JsonObject): StatTally {
        val flat = counts(root)
        return StatTally(
            VanillaStat.entries.associateWith { stat -> stat.flat.sumOf { flat[it] ?: 0L } },
            named(flat.filterKeys { it.startsWith(MINED_PREFIX) }),
            named(flat.filterKeys { it.startsWith(KILLED_PREFIX) }),
            flat.filterKeys { it.startsWith(CRAFTED_PREFIX) }.values.sum(),
        )
    }

    private fun counts(element: JsonElement?): Map<String, Long> {
        val entries = element as? JsonObject ?: return emptyMap()
        return entries.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.longOrNull?.let { key to it } }.toMap()
    }

    private fun named(counts: Map<String, Long>): Map<String, Long> {
        val out = HashMap<String, Long>()
        counts.forEach { (key, value) -> out.merge(displayName(key), value, Long::plus) }
        return out
    }

    private fun displayName(key: String): String =
        key.substringAfterLast(':').substringAfterLast('.').replace(WORD_BREAK, " ").replace('_', ' ').lowercase(Locale.ROOT)
}
