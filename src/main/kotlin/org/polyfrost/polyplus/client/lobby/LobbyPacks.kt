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
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.party.PartyScene
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

@Serializable
data class LobbyScenePack(
    val id: String,
    val name: String,
    val size: Long = 0L,
    val hash: String = "",
    val format: Int = 0,
)

object LobbyPacks {
    private class Local(val id: String, val supported: Boolean)

    private class Print(val size: Long, val hash: String, val format: Int)

    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private const val FILE_PREFIX = "file/"
    private const val ZIP_SUFFIX = ".zip"
    private const val META_FILE = "pack.mcmeta"
    private const val EDGE_BYTES = 65536L
    //? if = 1.8.9
    /*private const val LEGACY_FORMAT = 1*/
    private val FORMATTING = Regex("§.")

    private val prints = ConcurrentHashMap<String, Print>()
    private var lastStack: List<String>? = null

    var revision by mutableIntStateOf(0)
        private set

    fun activeIds(): List<String> = selected().map { it.id }

    fun changed(): Boolean {
        val stack = activeIds()
        val previous = lastStack
        lastStack = stack
        if (previous == null || previous == stack) return false
        revision++
        return true
    }

    fun record(ids: Collection<String>): List<LobbyScenePack> = ids.map { id ->
        val print = fileOf(id)?.let(::print)
        LobbyScenePack(id, title(id), print?.size ?: 0L, print?.hash.orEmpty(), print?.format ?: 0)
    }

    fun problem(packs: List<LobbyScenePack>, version: String): String? {
        if (packs.isEmpty()) return null
        if (version.isNotEmpty() && version != PartyScene.mcVersion) return "Made with ${packs.first().name} on $version"
        val active = selected()
        return packs.firstNotNullOfOrNull { need -> if (active.any { same(need, it.id) }) null else reason(need) }
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
        val print = fileOf(id)?.let(::print) ?: return false
        return print.hash == need.hash && print.size == need.size
    }

    private fun title(id: String): String =
        id.removePrefix(FILE_PREFIX).substringAfterLast('/').removeSuffix(ZIP_SUFFIX).replace(FORMATTING, "")

    private fun fileOf(id: String): File? = if (id.startsWith(FILE_PREFIX)) File(directory(), id.removePrefix(FILE_PREFIX)) else null

    private fun print(file: File): Print? {
        if (!file.exists()) return null
        val key = "${file.path}|${file.length()}|${file.lastModified()}"
        prints[key]?.let { return it }
        return runCatching { read(file) }
            .onFailure { LOGGER.warn("Could not read resource pack {}", file.name, it) }
            .getOrNull()
            ?.also { prints[key] = it }
    }

    private fun read(file: File): Print {
        if (file.isDirectory) {
            val meta = File(file, META_FILE).takeIf(File::isFile)?.readBytes() ?: ByteArray(0)
            return Print(0L, hex(MessageDigest.getInstance("SHA-1").digest(meta)), format(meta))
        }
        val digest = MessageDigest.getInstance("SHA-1")
        val size = RandomAccessFile(file, "r").use { data ->
            val edge = ByteArray(minOf(EDGE_BYTES, data.length()).toInt())
            data.readFully(edge)
            digest.update(edge)
            data.seek(data.length() - edge.size)
            data.readFully(edge)
            digest.update(edge)
            data.length()
        }
        return Print(size, hex(digest.digest()), runCatching { zippedFormat(file) }.getOrDefault(0))
    }

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
