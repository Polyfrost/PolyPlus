package org.polyfrost.polyplus.client.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
//? if > 1.8.9 {
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
//?} else {
/*import net.minecraft.client.resource.pack.ResourcePacks
*///?}
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.party.PartyScene
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

@Serializable
data class LobbyScenePack(
    val id: String,
    val name: String,
    val size: Long = 0L,
    val hash: String = "",
    val format: Int = 0,
    val fullHash: String = "",
)

object LobbyPacks {
    private class Local(val id: String, val supported: Boolean)

    private class Print(val size: Long, val hash: String, val format: Int, val full: String, val until: Long)

    private const val FILE_PREFIX = "file/"
    private const val ZIP_SUFFIX = ".zip"
    private const val META_FILE = "pack.mcmeta"
    private const val EDGE_BYTES = 65536L
    private const val MAX_DEPTH = 32
    private const val MAX_ENTRIES = 50000
    private const val DIR_RECHECK_MS = 30000L
    private val JUNK_FILES = setOf("thumbs.db", "desktop.ini")
    //? if = 1.8.9
    /*private const val LEGACY_FORMAT = 1*/
    private val FORMATTING = Regex("§.")

    private val prints = ConcurrentHashMap<String, Print>()
    private val glances = ConcurrentHashMap<String, Print>()
    private val pending = ConcurrentHashMap<String, FutureTask<Print?>>()
    private val waiting = ConcurrentHashMap.newKeySet<String>()
    private val settled = AtomicBoolean(false)
    private val hasher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus Lobby Packs").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    private var lastStack: List<String>? = null

    var revision by mutableIntStateOf(0)
        private set

    fun activeIds(): List<String> = selected().map { it.id }

    fun changed(): Boolean {
        val stack = activeIds()
        val previous = lastStack
        lastStack = stack
        if (previous != stack) stack.forEach { id -> fileOf(id)?.takeIf(File::exists)?.let { prefetch(it, key(it)) } }
        val ready = settled.getAndSet(false)
        if (previous == null || (previous == stack && !ready)) return false
        revision++
        return true
    }

    fun record(ids: Collection<String>): List<LobbyScenePack> = ids.map { id ->
        val file = fileOf(id)
        val print = file?.let(::print)
        val size = if (file?.isDirectory == true) 0L else print?.size ?: 0L
        LobbyScenePack(id, title(id), size, print?.hash.orEmpty(), print?.format ?: 0, print?.full.orEmpty())
    }

    fun problem(packs: List<LobbyScenePack>, version: String): String? {
        if (packs.isEmpty()) return null
        if (version.isNotEmpty() && version != PartyScene.mcVersion) return "Made with ${packs.first().name} on $version"
        val active = selected()
        val spots = packs.map { need -> active.indexOfFirst { same(need, it.id) } }
        packs.indices.firstOrNull { spots[it] < 0 }?.let { return reason(packs[it]) }
        (1 until packs.size).firstOrNull { spots[it] < spots[it - 1] }?.let { return "Needs ${packs[it].name} above ${packs[it - 1].name}" }
        val extra = active.indices.lastOrNull { it > spots.first() && it !in spots } ?: return null
        return "Turn off ${title(active[extra].id)} to use this scene"
    }

    fun write(packs: List<LobbyScenePack>): ListTag = ListTag().also { list ->
        packs.forEach { pack ->
            list.add(
                CompoundTag().apply {
                    putString("id", pack.id)
                    putString("name", pack.name)
                    putLong("size", pack.size)
                    putString("hash", pack.hash)
                    putInt("format", pack.format)
                    if (pack.fullHash.isNotEmpty()) putString("full_hash", pack.fullHash)
                },
            )
        }
    }

    fun read(list: ListTag): List<LobbyScenePack> = list.compounds().map { tag ->
        LobbyScenePack(
            tag.getStringOr("id", ""),
            tag.getStringOr("name", ""),
            tag.getLongOr("size", 0L),
            tag.getStringOr("hash", ""),
            tag.getIntOr("format", 0),
            tag.getStringOr("full_hash", ""),
        )
    }

    private fun reason(need: LobbyScenePack): String {
        val local = installed(need.id)
        return when {
            local == null || fileOf(need.id)?.exists() == false -> "Needs ${need.name} installed"
            !same(need, local.id) -> "Needs the version of ${need.name} it was made with"
            !local.supported -> "${need.name} doesn't support this version"
            else -> "Turn on ${need.name} to use this scene"
        }
    }

    private fun same(need: LobbyScenePack, id: String): Boolean {
        if (need.hash.isEmpty()) return need.id == id
        val file = fileOf(id)?.takeIf(File::exists) ?: return false
        val key = key(file)
        val done = fresh(prints, key) ?: run {
            waiting.add(key)
            prefetch(file, key)
            fresh(prints, key)?.also { waiting.remove(key) }
        }
        if (file.isDirectory && need.size > 0L && need.fullHash.isEmpty()) return done == null || (done.full == need.hash && done.size == need.size)
        val print = done ?: glance(file, key) ?: return false
        if (print.hash != need.hash || (need.size != 0L && need.size != print.size)) return false
        return done == null || need.fullHash.isEmpty() || done.full == need.fullHash
    }

    private fun title(id: String): String =
        id.removePrefix(FILE_PREFIX).substringAfterLast('/').removeSuffix(ZIP_SUFFIX).replace(FORMATTING, "")

    private fun fileOf(id: String): File? = if (id.startsWith(FILE_PREFIX)) File(directory(), id.removePrefix(FILE_PREFIX)) else null

    private fun print(file: File): Print? {
        if (!file.exists()) return null
        val key = key(file)
        fresh(prints, key)?.let { return it }
        val task = task(file, key)
        task.run()
        return runCatching { task.get() }.getOrNull()
    }

    private fun prefetch(file: File, key: String) {
        if (fresh(prints, key) == null && !pending.containsKey(key)) hasher.execute(task(file, key))
    }

    private fun glance(file: File, key: String): Print? {
        fresh(glances, key)?.let { return it }
        return runCatching {
            if (file.isDirectory) {
                Print(0L, sha1(meta(file)), 0, "", System.currentTimeMillis() + DIR_RECHECK_MS)
            } else {
                RandomAccessFile(file, "r").use { data -> Print(data.length(), edge(data), 0, "", Long.MAX_VALUE) }
            }
        }
            .onFailure { LOBBY_LOGGER.warn("Could not read resource pack {}", file.name, it) }
            .getOrNull()
            ?.also { glances[key] = it }
    }

    private fun key(file: File): String = "${file.path}|${file.length()}|${file.lastModified()}"

    private fun fresh(cache: Map<String, Print>, key: String): Print? = cache[key]?.takeIf { System.currentTimeMillis() < it.until }

    private fun task(file: File, key: String): FutureTask<Print?> {
        val task = FutureTask<Print?> {
            try {
                runCatching { read(file) }
                    .onFailure { LOBBY_LOGGER.warn("Could not read resource pack {}", file.name, it) }
                    .getOrNull()
                    ?.also { prints[key] = it }
            } finally {
                pending.remove(key)
                if (waiting.remove(key) && prints.containsKey(key)) settled.set(true)
            }
        }
        return pending.putIfAbsent(key, task) ?: task
    }

    private fun read(file: File): Print {
        if (file.isDirectory) {
            val meta = meta(file)
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(meta)
            var total = 0L
            var count = 0
            fun walk(dir: File, path: String, depth: Int) {
                if (depth > MAX_DEPTH) return
                val children = dir.listFiles()?.sortedBy(File::getName) ?: return
                for (child in children) {
                    if (count >= MAX_ENTRIES) return
                    if (child.name.startsWith('.') || child.name.lowercase() in JUNK_FILES) continue
                    val rel = if (path.isEmpty()) child.name else "$path/${child.name}"
                    if (child.isDirectory) {
                        walk(child, rel, depth + 1)
                    } else {
                        val length = child.length()
                        total += length
                        count++
                        digest.update("$rel\u0000$length\n".toByteArray(Charsets.UTF_8))
                    }
                }
            }
            walk(file, "", 0)
            return Print(total, sha1(meta), format(meta), hex(digest.digest()), System.currentTimeMillis() + DIR_RECHECK_MS)
        }
        val digest = MessageDigest.getInstance("SHA-1")
        val (size, edge) = RandomAccessFile(file, "r").use { data ->
            val edge = edge(data)
            data.seek(0L)
            val buffer = ByteArray(EDGE_BYTES.toInt())
            while (true) {
                val read = data.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            data.length() to edge
        }
        return Print(size, edge, runCatching { zippedFormat(file) }.getOrDefault(0), hex(digest.digest()), Long.MAX_VALUE)
    }

    private fun edge(data: RandomAccessFile): String {
        val length = data.length()
        val edge = ByteArray(minOf(EDGE_BYTES, length).toInt())
        val digest = MessageDigest.getInstance("SHA-1")
        data.seek(0L)
        data.readFully(edge)
        digest.update(edge)
        data.seek(length - edge.size)
        data.readFully(edge)
        digest.update(edge)
        return hex(digest.digest())
    }

    private fun meta(dir: File): ByteArray = File(dir, META_FILE).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)

    private fun sha1(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    private fun zippedFormat(file: File): Int = ZipFile(file).use { zip ->
        zip.getEntry(META_FILE)?.let { entry -> zip.getInputStream(entry).use { format(it.readBytes()) } } ?: 0
    }

    private fun format(meta: ByteArray): Int = runCatching {
        val pack = PolyPlusClient.JSON.parseToJsonElement(meta.toString(Charsets.UTF_8)).jsonObject.getValue("pack").jsonObject
        val value = pack["pack_format"] ?: pack["min_format"] ?: return 0
        ((value as? JsonArray)?.first() ?: value).jsonPrimitive.int
    }.getOrDefault(0)

    private fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)

    //? if > 1.8.9 {
    private fun directory(): File = File(Minecraft.getInstance().gameDirectory, "resourcepacks")

    private fun selected(): List<Local> = Minecraft.getInstance().resourcePackRepository.selectedPacks
        .filter { !it.isRequired && !fromServer(it) }
        .map(::local)

    private fun installed(id: String): Local? = Minecraft.getInstance().resourcePackRepository.getPack(id)?.let(::local)

    private fun local(pack: Pack) = Local(pack.id, pack.compatibility.isCompatible)

    private fun fromServer(pack: Pack): Boolean = pack.packSource === PackSource.SERVER || pack.packSource === PackSource.WORLD

    fun serverPackApplied(mc: Minecraft): Boolean = mc.resourcePackRepository.selectedPacks.any(::fromServer)

    fun connectionOf(mc: Minecraft): Connection? = mc.connection?.getConnection()
    //?} else {
    /*private fun directory(): File = Minecraft.getInstance().resourcePacks.directory

    private fun selected(): List<Local> = Minecraft.getInstance().resourcePacks.selectedPacks.map(::local)

    private fun installed(id: String): Local? =
        Minecraft.getInstance().resourcePacks.availablePacks.firstOrNull { FILE_PREFIX + it.name == id }?.let(::local)

    private fun local(pack: ResourcePacks.UnopenedPack) = Local(FILE_PREFIX + pack.name, pack.format == LEGACY_FORMAT)

    fun serverPackApplied(mc: Minecraft): Boolean = mc.resourcePacks.serverResourcePack != null

    fun connectionOf(mc: Minecraft): Connection? = mc.networkHandler?.getConnection()
    *///?}
}
