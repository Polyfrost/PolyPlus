package org.polyfrost.polyplus.client.lobby

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.network.LanServerQueryManager
import net.minecraft.client.network.MultiplayerServerListPinger
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.options.ServerList
import net.minecraft.client.options.ServerListEntry
import net.minecraft.client.resource.language.I18n
import net.minecraft.world.WorldSettings
import net.minecraft.world.storage.WorldSaveInfo
import org.polyfrost.oneconfig.internal.legacy.chat.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LobbyI18n {
    fun get(key: String, vararg args: Any): String = I18n.translate(key, *args)
}

object LobbyMotd {
    private const val SECTION = '\u00a7'
    private const val PALETTE = "0123456789abcdef"
    private val COLORS = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )

    fun forEach(text: String, visit: (Int?, Int) -> Unit) {
        var color: Int? = null
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == SECTION && index + 1 < text.length) {
                val code = PALETTE.indexOf(text[index + 1].lowercaseChar())
                when {
                    code >= 0 -> color = COLORS[code]
                    text[index + 1].lowercaseChar() == 'r' -> color = null
                }
                index += 2
            } else {
                visit(color, text.codePointAt(index))
                index += Character.charCount(text.codePointAt(index))
            }
        }
    }
}

object LobbyCommonComponents {
    val EMPTY: Component = Component.literal("")
    val GUI_CANCEL: Component = Component.translatable("gui.cancel")
    val GUI_PROCEED: Component = Component.translatable("gui.proceed")
}

class LobbyPlayers(private val count: Int) {
    fun online(): Int = count
}

class LobbyServerData(name: String, ip: String, type: Type) {
    enum class Type { LAN, REALM, OTHER }

    enum class State { INITIAL, PINGING, UNREACHABLE, INCOMPATIBLE, SUCCESSFUL }

    enum class ServerPackStatus {
        ENABLED,
        DISABLED,
        PROMPT;

        fun getName(): Component = Component.translatable("addServer.resourcePack.${name.lowercase(Locale.ROOT)}")
    }

    val entry = ServerListEntry(name, ip, type == Type.LAN)
    private var state = State.INITIAL

    constructor(entry: ServerListEntry) : this(entry.name, entry.ip, if (entry.isLocal) Type.LAN else Type.OTHER) {
        this.entry.set(entry)
    }

    var name: String
        get() = entry.name
        set(value) {
            entry.name = value
        }

    var ip: String
        get() = entry.ip
        set(value) {
            entry.ip = value
        }

    val isLan: Boolean get() = entry.isLocal

    var motd: Component = LobbyCommonComponents.EMPTY
    var status: Component = LobbyCommonComponents.EMPTY
    val version: Component get() = Component.literal(entry.version.orEmpty())
    val ping: Long get() = entry.ping
    val protocol: Int get() = entry.protocol
    var players: LobbyPlayers? = null

    val iconBytes: ByteArray?
        get() = entry.icon?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    val resourcePackStatus: ServerPackStatus
        get() = when (entry.resourcePackStatus) {
            ServerListEntry.ResourcePackStatus.ENABLED -> ServerPackStatus.ENABLED
            ServerListEntry.ResourcePackStatus.DISABLED -> ServerPackStatus.DISABLED
            else -> ServerPackStatus.PROMPT
        }

    fun setResourcePackStatus(status: ServerPackStatus) {
        entry.resourcePackStatus = when (status) {
            ServerPackStatus.ENABLED -> ServerListEntry.ResourcePackStatus.ENABLED
            ServerPackStatus.DISABLED -> ServerListEntry.ResourcePackStatus.DISABLED
            ServerPackStatus.PROMPT -> ServerListEntry.ResourcePackStatus.PROMPT
        }
    }

    fun setIconBytes(bytes: ByteArray?) {
        entry.icon = bytes?.let { Base64.getEncoder().encodeToString(it) }
    }

    fun state(): State = state

    fun setState(next: State) {
        state = next
    }

    fun copyNameIconFrom(other: LobbyServerData) {
        name = other.name
        entry.icon = other.entry.icon
    }

    fun readStatus() {
        motd = Component.literal(entry.motd.orEmpty())
        status = Component.literal(entry.status.orEmpty())
        val online = entry.status?.replace(FORMATTING, "")?.substringBefore('/')?.trim()?.toIntOrNull()
        players = online?.let(::LobbyPlayers)
    }

    private companion object {
        val FORMATTING = Regex("§.")
    }
}

class LobbyServerList(private val minecraft: Minecraft) {
    private val list = ServerList(minecraft)
    private val rows = ArrayList<LobbyServerData>()
    private val hiddenRows = ArrayList<LobbyServerData>()

    fun load() {
        list.load()
        rows.clear()
        repeat(list.size()) { rows += LobbyServerData(list.get(it)).also { row -> list.set(it, row.entry) } }
    }

    fun save() = list.save()

    fun size(): Int = list.size()

    fun get(index: Int): LobbyServerData = rows[index]

    fun get(address: String): LobbyServerData? = rows.firstOrNull { it.ip == address } ?: hiddenRows.firstOrNull { it.ip == address }

    fun add(data: LobbyServerData, hidden: Boolean) {
        if (hidden) {
            hiddenRows += data
            return
        }
        rows += data
        list.add(data.entry)
    }

    fun remove(data: LobbyServerData) {
        if (hiddenRows.remove(data)) return
        val index = rows.indexOf(data)
        if (index < 0) return
        rows.removeAt(index)
        list.remove(index)
    }

    fun swap(first: Int, second: Int) {
        list.swap(first, second)
        rows[first] = rows[second].also { rows[second] = rows[first] }
    }

    fun unhide(address: String): LobbyServerData? {
        val data = hiddenRows.firstOrNull { it.ip == address } ?: return null
        hiddenRows.remove(data)
        rows += data
        list.add(data.entry)
        return data
    }
}

class LobbyServerPinger {
    private class Pending(val data: LobbyServerData, val persist: Runnable, val done: Runnable)

    private val pinger = MultiplayerServerListPinger()
    private val pending = ConcurrentLinkedQueue<Pending>()

    fun pingServer(data: LobbyServerData, persist: Runnable, done: Runnable) {
        pending += Pending(data, persist, done)
        data.entry.ping = WAITING
        pinger.add(data.entry)
    }

    fun tick() {
        pinger.tick()
        pending.removeIf { job ->
            val entry = job.data.entry
            when {
                entry.ping == WAITING || entry.ping == NOT_ANSWERED && entry.motd == PINGING_TEXT -> false
                entry.ping < 0L -> {
                    job.data.readStatus()
                    job.data.setState(LobbyServerData.State.UNREACHABLE)
                    job.done.run()
                    true
                }
                else -> {
                    job.data.readStatus()
                    job.persist.run()
                    job.done.run()
                    true
                }
            }
        }
    }

    fun removeAll() {
        pinger.cancel()
        pending.clear()
    }

    private companion object {
        const val WAITING = -2L
        const val NOT_ANSWERED = -1L
        const val PINGING_TEXT = "Pinging..."
    }
}

object LobbyServerAddress {
    fun parseString(address: String): ServerAddress = ServerAddress.parse(address)

    fun isValidAddress(address: String): Boolean = address.isNotBlank() && !address.contains(' ')
}

object LobbyLan {
    class LanServer(val motd: String, val address: String)

    class LanServerList {
        val source = LanServerQueryManager.LanServerList()

        fun takeDirtyServers(): List<LanServer>? {
            if (!source.needsUpdate()) return null
            source.markClean()
            return source.servers.map { LanServer(it.motd, it.port) }
        }
    }

    class LanServerDetector(list: LanServerList) {
        private val detector = LanServerQueryManager.LanServerDetector(list.source)

        fun start() = detector.start()

        fun interrupt() = detector.interrupt()
    }
}

open class LobbyLevelSummary(private val save: WorldSaveInfo?, val levelId: String) : Comparable<LobbyLevelSummary> {
    class CorruptedLevelSummary(levelId: String) : LobbyLevelSummary(null, levelId)

    class SymlinkLevelSummary(levelId: String) : LobbyLevelSummary(null, levelId)

    val levelName: String get() = save?.name ?: levelId
    val lastPlayed: Long get() = save?.lastPlayed ?: 0L
    val icon: Path get() = Minecraft.getInstance().gameDir.toPath().resolve("saves").resolve(levelId).resolve("icon.png")
    val gameMode: WorldSettings.GameMode get() = save?.gameMode ?: WorldSettings.GameMode.SURVIVAL
    val isHardcore: Boolean get() = save?.isHardcore == true
    val isLocked: Boolean get() = false
    val isCompatible: Boolean get() = true
    val isDowngrade: Boolean get() = false
    val isExperimental: Boolean get() = false
    val isDisabled: Boolean get() = save == null
    val info: Component get() = LobbyCommonComponents.EMPTY
    val worldVersionName: Component get() = Component.literal(VERSION_NAME)

    fun hasCommands(): Boolean = save?.areCheatsEnabled() == true

    fun requiresManualConversion(): Boolean = save?.isSameVersion == true

    fun shouldBackup(): Boolean = false

    fun primaryActionActive(): Boolean = save != null

    fun canEdit(): Boolean = save != null

    fun canRecreate(): Boolean = save != null

    fun canDelete(): Boolean = true

    override fun compareTo(other: LobbyLevelSummary): Int = other.lastPlayed.compareTo(lastPlayed)

    companion object {
        private const val VERSION_NAME = "1.8.9"
        val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT)

        fun all(mc: Minecraft): List<LobbyLevelSummary> =
            mc.worldStorageSource.all.map { LobbyLevelSummary(it, it.saveName) }

        fun date(time: Long): String = DATE_FORMAT.format(Date(time))
    }
}

object LobbyWorldBackup {
    private const val BACKUPS = "backups"

    fun write(id: String): String {
        val game = Minecraft.getInstance().gameDir.toPath()
        val world = game.resolve("saves").resolve(id)
        val directory = game.resolve(BACKUPS)
        Files.createDirectories(directory)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
        var zip = directory.resolve("${stamp}_$id.zip")
        var copy = 1
        while (Files.exists(zip)) zip = directory.resolve("${stamp}_${id}_${copy++}.zip")
        val partial = directory.resolve("${zip.fileName}.tmp")
        var complete = false
        try {
            ZipOutputStream(Files.newOutputStream(partial)).use { out ->
                world.toFile().walkTopDown().filter(File::isFile).forEach { file ->
                    out.putNextEntry(ZipEntry(world.relativize(file.toPath()).toString().replace('\\', '/')))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            Files.move(partial, zip)
            complete = true
        } finally {
            if (!complete) Files.deleteIfExists(partial)
        }
        return zip.fileName.toString()
    }
}
*///?}
