package org.polyfrost.polyplus.client.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.serverapi.ServerApiProtocol
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.io.path.deleteIfExists
import kotlin.math.hypot
import kotlin.math.pow

@Serializable
data class LobbySceneEntry(
    val id: String,
    val file: String,
    val world: String,
    val label: String,
    val biome: String,
    val clock: Long,
    val score: Double,
    val capturedAt: Long,
    val bytes: Long,
    val x: Int,
    val z: Int,
    val dataVersion: Int,
    val origin: String,
    val packs: List<LobbyScenePack> = emptyList(),
    val gameVersion: String = "",
    val points: Int? = null,
    val manual: Boolean = false,
    val profile: String = "outdoor",
) {
    val bundled: Boolean
        get() = origin == ORIGIN_BUNDLED

    val setting: LobbySceneScore.Profile
        get() = LobbySceneScore.Profile.of(profile)

    companion object {
        const val ORIGIN_WORLD = "world"
        const val ORIGIN_SERVER = "server"
        const val ORIGIN_BUNDLED = "bundled"
        const val ORIGIN_PARTY = "party"
        const val PARTY_PREFIX = "party:"
    }
}

object LobbySceneStore {
    @Serializable
    private data class State(
        val scenes: List<LobbySceneEntry> = emptyList(),
        val capture: Boolean = true,
        val pinned: String? = null,
        val servers: Map<String, Boolean> = emptyMap(),
        val serverPolicies: Map<String, String> = emptyMap(),
    )

    //? if > 1.8.9 {
    private const val BUNDLED_ROOT = "/assets/polyplus/lobby/scenes/"
    //?} else {
    /*private const val BUNDLED_ROOT = "/assets/polyplus/lobby/scenes_legacy/"
    *///?}
    private const val MAX_PER_WORLD = 4
    private const val MAX_TOTAL_BYTES = 16L * 1024L * 1024L
    private const val SAME_SPOT_BLOCKS = 48.0
    private const val RECENCY_HALF_LIFE_DAYS = 14.0
    private const val RECENCY_FLOOR = 0.3
    private const val DAY_MILLIS = 86_400_000.0
    private const val NAME_BYTES = 6

    //? if > 1.8.9 {
    private val BUNDLED = listOf(
        bundled("snowy_village", "Snowy village", "minecraft:snowy_taiga", 18000L),
        bundled("desert_village", "Desert village", "minecraft:desert", 12254L),
        bundled("cherry_grove", "Cherry grove", "minecraft:cherry_grove", 3358L),
        bundled("lush_cave", "Lush cave", "minecraft:lush_caves", 6000L, "cave"),
    )
    //?} else {
    /*private val BUNDLED = listOf(
        bundled("snowy_hills", "Snowy hills", "Extreme Hills", 18000L),
        bundled("desert_dunes", "Desert dunes", "Desert", 12254L),
        bundled("village_green", "Village green", "Plains", 3358L),
        bundled("lava_mineshaft_cave", "Lava mineshaft cave", "Taiga", 6000L, "cave"),
    )
    *///?}

    private fun bundled(name: String, label: String, biome: String, clock: Long, profile: String = "outdoor") = LobbySceneEntry(
        "bundled:$name",
        "$name.nbt.gz",
        "polyfrost",
        label,
        biome,
        clock,
        0.0,
        0L,
        0L,
        0,
        0,
        0,
        LobbySceneEntry.ORIGIN_BUNDLED,
        profile = profile,
    )

    private val root: Path by lazy { Minecraft.getInstance().gameDirectory.toPath().resolve("polyplus").resolve("lobby") }
    private val indexFile: Path by lazy { root.resolve("scenes.json") }
    private val folder: Path by lazy { root.resolve("scenes") }

    private var state by mutableStateOf(State())
    private var loaded = false

    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus lobby store").apply { isDaemon = true }
    }

    val scenes: List<LobbySceneEntry>
        get() {
            ensureLoaded()
            return state.scenes.sortedByDescending(::rank)
        }

    val usableScenes: List<LobbySceneEntry>
        get() = scenes.filter { it.dataVersion == LobbySnapshot.dataVersion && problem(it) == null }

    val lockedScenes: List<Pair<LobbySceneEntry, String>>
        get() = scenes.filter { it.dataVersion == LobbySnapshot.dataVersion }.mapNotNull { entry -> problem(entry)?.let { entry to it } }

    val bundledScenes: List<LobbySceneEntry>
        get() = BUNDLED

    var captureEnabled: Boolean
        get() {
            ensureLoaded()
            return state.capture
        }
        set(value) = update { copy(capture = value) }

    var pinned: String?
        get() {
            ensureLoaded()
            return state.pinned?.takeIf { id -> (usableScenes + BUNDLED).any { it.id == id } }
        }
        set(value) = update { copy(pinned = value) }

    fun problem(entry: LobbySceneEntry): String? = LobbyPacks.problem(entry.packs, entry.gameVersion)

    fun serverChoice(address: String): Boolean? {
        ensureLoaded()
        return state.servers[key(address)]
    }

    fun setServerChoice(address: String, allowed: Boolean?) = update {
        copy(servers = if (allowed == null) servers - key(address) else servers + (key(address) to allowed))
    }

    fun serverPolicy(address: String): ServerApiProtocol.ScenePolicy {
        ensureLoaded()
        return state.serverPolicies[key(address)]?.let { name -> ServerApiProtocol.ScenePolicy.entries.firstOrNull { it.name == name } }
            ?: ServerApiProtocol.ScenePolicy.UNSET
    }

    fun rememberServerPolicy(address: String, policy: ServerApiProtocol.ScenePolicy) {
        if (serverPolicy(address) == policy) return
        update { copy(serverPolicies = serverPolicies + (key(address) to policy.name)) }
    }

    fun serverAllows(address: String, policy: ServerApiProtocol.ScenePolicy): Boolean =
        serverChoice(address) != false && policy != ServerApiProtocol.ScenePolicy.DENY

    fun queue(): List<LobbySceneEntry> {
        val usable = usableScenes
        val choice = pinned?.let { id -> (usable + BUNDLED).firstOrNull { it.id == id } } ?: LobbyCapture.returnEntry()?.takeIf { problem(it) == null }
        val own = listOfNotNull(choice) + usable.filterNot { it.id == choice?.id } + BUNDLED.filterNot { it == choice }
        val party = PartyScene.startEntries()
        return party + own.filterNot { entry -> party.any { it.id == entry.id } }
    }

    fun read(entry: LobbySceneEntry): CompoundTag {
        LobbyCapture.takeRoot(entry.id)?.let { return it }
        if (entry.origin == LobbySceneEntry.ORIGIN_PARTY) return LobbySnapshot.read(Path.of(entry.file))
        if (!entry.bundled) return LobbySnapshot.read(folder.resolve(entry.file))
        val stream = LobbySceneStore::class.java.getResourceAsStream(BUNDLED_ROOT + entry.file)
            ?: error("Missing bundled scene ${entry.file}")
        return stream.use(LobbySnapshot::read)
    }

    fun wouldKeep(world: String, x: Int, z: Int, score: Double, stack: Set<String>): Boolean {
        val rivals = scenes.filter { !it.manual && it.world == world && it.dataVersion == LobbySnapshot.dataVersion && stackOf(it) == stack }
        val near = rivals.filter { hypot((it.x - x).toDouble(), (it.z - z).toDouble()) < SAME_SPOT_BLOCKS }
        if (near.any { it.score >= score }) return false
        return near.isNotEmpty() || rivals.size < MAX_PER_WORLD || rivals.any { it.score < score }
    }

    fun admit(candidate: LobbySceneEntry, data: ByteArray): Boolean {
        val stack = stackOf(candidate)
        if (!candidate.manual && !wouldKeep(candidate.world, candidate.x, candidate.z, candidate.score, stack)) return false
        val inWorld = state.scenes.filter { it.world == candidate.world }
        val current = inWorld.filter { it.dataVersion == LobbySnapshot.dataVersion }
        val rivals = current.filter { !it.manual && stackOf(it) == stack }
        val replaced = (inWorld - current.toSet()).toMutableList()
        replaced += state.scenes.filter { it.file == fileName(candidate) }
        if (!candidate.manual) {
            replaced += rivals.filter { hypot((it.x - candidate.x).toDouble(), (it.z - candidate.z).toDouble()) < SAME_SPOT_BLOCKS }
            val left = rivals - replaced.toSet()
            if (left.size >= MAX_PER_WORLD) replaced += left.sortedBy { it.score }.take(left.size - MAX_PER_WORLD + 1)
        }
        var kept = state.scenes - replaced.toSet()
        var total = kept.sumOf { it.bytes } + data.size
        val spare = kept.sortedWith(compareBy({ it.dataVersion == LobbySnapshot.dataVersion }, ::rank)).toMutableList()
        while (total > MAX_TOTAL_BYTES && spare.isNotEmpty()) {
            val dropped = spare.removeAt(0)
            replaced += dropped
            kept = kept - dropped
            total -= dropped.bytes
        }
        val entry = named(candidate).copy(bytes = data.size.toLong())
        val name = entry.file
        val stale = replaced.filter { it.file != name }.map { folder.resolve(it.file) }
        state = state.copy(scenes = kept + entry, pinned = state.pinned?.takeUnless { id -> replaced.any { it.id == id && it.file != name } })
        val index = encoded()
        writer.execute {
            runCatching {
                Files.createDirectories(folder)
                atomicWrite(folder.resolve(name), data)
                stale.forEach(Path::deleteIfExists)
                atomicWrite(indexFile, index.toByteArray(Charsets.UTF_8))
            }.onFailure { LOBBY_LOGGER.warn("Could not save a scene", it) }
        }
        return true
    }

    fun fileOf(entry: LobbySceneEntry): Path? = if (entry.bundled || entry.origin == LobbySceneEntry.ORIGIN_PARTY) null else folder.resolve(entry.file)

    fun delete(entry: LobbySceneEntry) {
        if (entry.bundled) return
        val path = folder.resolve(entry.file)
        update { copy(scenes = scenes - entry, pinned = pinned?.takeUnless { it == entry.id }) }
        Util.ioPool().execute { runCatching { path.deleteIfExists() }.onFailure { LOBBY_LOGGER.warn("Could not delete a scene", it) } }
    }

    fun worldKey(address: String): String = "server:${key(address)}"

    fun named(entry: LobbySceneEntry): LobbySceneEntry {
        val name = fileName(entry)
        return entry.copy(id = name.removeSuffix(".nbt.gz"), file = name)
    }

    private fun key(address: String): String = address.trim().lowercase(Locale.ROOT)

    private fun stackOf(entry: LobbySceneEntry): Set<String> = entry.packs.mapTo(LinkedHashSet()) { it.id }

    private fun rank(entry: LobbySceneEntry): Double {
        val days = (System.currentTimeMillis() - entry.capturedAt).coerceAtLeast(0L) / DAY_MILLIS
        return entry.score * 0.5.pow(days / RECENCY_HALF_LIFE_DAYS).coerceAtLeast(RECENCY_FLOOR)
    }

    private fun fileName(entry: LobbySceneEntry): String {
        val stack = entry.packs.joinToString("") { "|${it.id}" }
        val kind = if (entry.manual) "|manual" else ""
        val digest = MessageDigest.getInstance("SHA-1").digest("${entry.world}|${entry.x}|${entry.z}$stack$kind".toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest, 0, NAME_BYTES) + ".nbt.gz"
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.exists(indexFile)) return
        val text = runCatching { Files.readString(indexFile) }.getOrNull()
        runCatching {
            state = PolyPlusClient.JSON.decodeFromString(State.serializer(), text ?: error("unreadable"))
        }.onFailure { error ->
            LOBBY_LOGGER.warn("Could not read the scene index", error)
            state = salvage(text)
            runCatching {
                Files.move(indexFile, indexFile.resolveSibling("scenes.json.bad-${System.currentTimeMillis()}"))
            }.onFailure { LOBBY_LOGGER.warn("Could not set the scene index aside", it) }
        }
    }

    private fun salvage(text: String?): State {
        val root = runCatching { PolyPlusClient.JSON.parseToJsonElement(text ?: "").jsonObject }.getOrNull()
        val servers = root?.let { section(it, "servers", Boolean.serializer()) }.orEmpty()
        val policies = root?.let { section(it, "serverPolicies", String.serializer()) }.orEmpty()
        return State(capture = root != null && runCatching { root["capture"]?.jsonPrimitive?.boolean }.getOrNull() != false, servers = servers, serverPolicies = policies)
    }

    private fun <T> section(root: JsonObject, name: String, value: KSerializer<T>): Map<String, T>? =
        root[name]?.let { runCatching { PolyPlusClient.JSON.decodeFromJsonElement(MapSerializer(String.serializer(), value), it) }.getOrNull() }

    private inline fun update(change: State.() -> State) {
        ensureLoaded()
        state = state.change()
        val index = encoded()
        writer.execute {
            runCatching {
                Files.createDirectories(root)
                atomicWrite(indexFile, index.toByteArray(Charsets.UTF_8))
            }.onFailure { LOBBY_LOGGER.warn("Could not save the scene index", it) }
        }
    }

    private fun encoded(): String = PolyPlusClient.JSON.encodeToString(State.serializer(), state)

    private fun atomicWrite(path: Path, data: ByteArray) {
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.write(temporary, data)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
    }
}
