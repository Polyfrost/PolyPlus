package org.polyfrost.polyplus.client.gui.singleplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.lobbyshell.relativeDayText
import org.polyfrost.polyplus.client.lobby.LobbyCompat
//? if = 1.8.9 {
/*import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
//? if > 1.8.9
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.network.chat.Component
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.world.level.GameType
//? if > 1.8.9
import net.minecraft.world.level.storage.LevelStorageException
import net.minecraft.world.level.storage.LevelSummary
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

private val LOGGER = LogManager.getLogger("PolyPlus/SingleplayerWorlds")

private const val META_SEPARATOR = " · "

internal enum class WorldTone { Neutral, Warning, Danger }

internal class WorldTag(val label: String, val tone: WorldTone)

internal enum class WorldFilter(val label: String) {
    All("All"),
    Survival("Survival"),
    Creative("Creative"),
    Hardcore("Hardcore"),
}

internal enum class WorldListPhase { Loading, Ready, Failed }

private fun tr(key: String): String = Platform.i18n().getUnformattedText(Component.translatable(key))

internal fun playedText(lastPlayed: Long): String = relativeDayText(lastPlayed)?.let { "played $it" } ?: "not played yet"

internal class SingleplayerWorld(val summary: LevelSummary) {
    val id: String = summary.levelId
    val name: String = summary.levelName
    val lastPlayed: Long = summary.lastPlayed
    val iconPath: Path? = summary.icon

    val broken = summary is LevelSummary.CorruptedLevelSummary || summary is LevelSummary.SymlinkLevelSummary
    //? if >= 1.21.8 {
    private val dataVersion = if (broken) 0 else summary.levelVersion().minecraftVersion().version()
    //?} elif > 1.8.9 {
    /*private val dataVersion = if (broken) 0 else summary.levelVersion().minecraftVersion().version
    *///?} else {
    /*private val dataVersion = LobbyCompat.dataVersion()
    *///?}
    private val currentDataVersion = LobbyCompat.dataVersion()

    val canPlay = summary.primaryActionActive()
    val canEdit = summary.canEdit()
    val canRecreate = summary.canRecreate()
    val canDelete = summary.canDelete()
    val canBackup = summary.canEdit()
    //? if >= 26.1 {
    val needsFileFixing = summary.requiresFileFixing()
    //?} else {
    /*val needsFileFixing = false
    *///?}

    //? if > 1.8.9 {
    val primaryLabel: String = summary.primaryActionMessage().let { if (it === LevelSummary.PLAY_WORLD) "Play" else Platform.i18n().getUnformattedText(it) }
    //?} else {
    /*val primaryLabel: String = "Play"
    *///?}

    val gameMode: String = when {
        broken -> ""
        summary.isHardcore -> tr("selectWorld.gameMode.hardcore")
        //? if > 1.8.9 {
        else -> tr("selectWorld.gameMode.${summary.gameMode.getName()}")
        //?} else {
        /*else -> tr("selectWorld.gameMode.${summary.gameMode.key}")
        *///?}
    }
    val version: String = if (broken) "" else Platform.i18n().getUnformattedText(summary.worldVersionName)
    val commands: Boolean = !broken && summary.hasCommands()

    val meta: String = if (broken) playedText(lastPlayed) else listOf(gameMode, playedText(lastPlayed), version).joinToString(META_SEPARATOR)

    val lastPlayedDate: String = if (lastPlayed > 0L) {
        //? if > 1.8.9 {
        WorldSelectionList.DATE_FORMAT.format(Instant.ofEpochMilli(lastPlayed).atZone(ZoneId.systemDefault()))
        //?} else {
        /*LevelSummary.date(lastPlayed)
        *///?}
    } else {
        "Never"
    }

    val status: String? = when {
        broken -> Platform.i18n().getUnformattedText(summary.info)
        summary.isLocked -> tr("selectWorld.locked")
        summary.requiresManualConversion() -> tr("selectWorld.conversion")
        !summary.isCompatible -> Platform.i18n().getUnformattedText(summary.info)
        summary.isDowngrade -> "${tr("selectWorld.tooltip.fromNewerVersion1")} ${tr("selectWorld.tooltip.fromNewerVersion2")}"
        needsFileFixing -> tr("selectWorld.requiresFileFixingTooltip.play")
        summary.shouldBackup() -> "${tr("selectWorld.tooltip.snapshot1")} ${tr("selectWorld.tooltip.snapshot2")}"
        else -> null
    }

    val statusTone: WorldTone = when {
        broken || summary.isDisabled || (!broken && summary.isDowngrade) -> WorldTone.Danger
        else -> WorldTone.Warning
    }

    val tags: List<WorldTag> = buildList {
        when {
            summary is LevelSummary.CorruptedLevelSummary -> add(WorldTag("Corrupted", WorldTone.Danger))
            summary is LevelSummary.SymlinkLevelSummary -> add(WorldTag("Symlink", WorldTone.Danger))
            else -> {
                if (summary.isLocked) add(WorldTag("Locked", WorldTone.Danger))
                if (summary.requiresManualConversion()) add(WorldTag("Needs conversion", WorldTone.Danger))
                if (!summary.isCompatible) add(WorldTag("Incompatible", WorldTone.Danger))
                if (summary.isCompatible && summary.isDowngrade) add(WorldTag("Newer version", WorldTone.Danger))
                if (summary.isHardcore) add(WorldTag("Hardcore", WorldTone.Danger))
                if (needsFileFixing) add(WorldTag("Needs upgrade", WorldTone.Warning))
                if (!needsFileFixing && summary.isCompatible && dataVersion < currentDataVersion) add(WorldTag("Older version", WorldTone.Neutral))
                if (summary.isExperimental) add(WorldTag("Experimental", WorldTone.Warning))
                if (commands) add(WorldTag("Cheats", WorldTone.Neutral))
            }
        }
    }

    fun matches(filter: WorldFilter): Boolean = when (filter) {
        WorldFilter.All -> true
        WorldFilter.Survival -> !broken && !summary.isHardcore && summary.gameMode == GameType.SURVIVAL
        WorldFilter.Creative -> !broken && summary.gameMode == GameType.CREATIVE
        WorldFilter.Hardcore -> !broken && summary.isHardcore
    }

    fun matches(needle: String): Boolean {
        return name.lowercase(Locale.ROOT).contains(needle) || id.lowercase(Locale.ROOT).contains(needle)
    }
}

internal class SingleplayerState {
    private val mc = Minecraft.getInstance()
    private var token = 0

    val flows = SingleplayerFlows()
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val iconBytes = mutableStateMapOf<String, ByteArray>()

    var phase by mutableStateOf(WorldListPhase.Loading)
        private set
    var worlds by mutableStateOf(emptyList<SingleplayerWorld>())
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var onSelect: (String) -> Unit = {}
    var query by mutableStateOf("")
    var filter by mutableStateOf(WorldFilter.All)

    val featured: SingleplayerWorld?
        get() = worlds.firstOrNull { it.canPlay && !it.broken && it.lastPlayed > 0L } ?: worlds.firstOrNull { it.canPlay && !it.broken } ?: worlds.firstOrNull()

    val lastPlayedWorld: SingleplayerWorld?
        get() = worlds.firstOrNull { it.canPlay && !it.broken && it.lastPlayed > 0L }

    val selected: SingleplayerWorld?
        get() = worlds.firstOrNull { it.id == selectedId } ?: featured

    val narrowed: Boolean
        get() = query.isNotBlank() || filter != WorldFilter.All

    fun visible(): List<SingleplayerWorld> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val matching = worlds.filter { it.matches(filter) && it.matches(needle) }
        return if (narrowed) matching else lastPlayedWorld.let { last -> matching.filter { it !== last } }
    }

    fun reload() {
        val current = ++token
        //? if = 1.8.9 {
        /*PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val result = runCatching { LevelSummary.all(mc) }
            ClientPlatform.runOnMain {
                if (current == token) {
                    result.fold({ publish(it) }, {
                        LOGGER.error("Couldn't load level list", it)
                        fail(tr("selectWorld.unable_to_load"))
                    })
                }
            }
        }
        *///?} else {
        val levelSource = mc.levelSource
        val candidates = try {
            levelSource.findLevelCandidates()
        } catch (error: LevelStorageException) {
            LOGGER.error("Couldn't load level list", error)
            fail(Platform.i18n().getUnformattedText(error.messageComponent))
            return
        }
        levelSource.loadLevelSummaries(candidates).whenCompleteAsync({ summaries, error ->
            if (current != token) return@whenCompleteAsync
            if (error != null) {
                LOGGER.error("Couldn't load level list", error)
                fail(tr("selectWorld.unable_to_load"))
            } else {
                publish(summaries)
            }
        }, mc)
        //?}
    }

    private fun fail(message: String) {
        failure = message
        phase = WorldListPhase.Failed
    }

    private fun publish(summaries: List<LevelSummary>) {
        val list = summaries.sorted().map(::SingleplayerWorld)
        val keep = list.mapTo(HashSet()) { it.id }
        icons.keys.retainAll(keep)
        iconBytes.keys.retainAll(keep)
        if (list.none { it.id == selectedId }) selectedId = null
        worlds = list
        failure = null
        phase = WorldListPhase.Ready
        list.forEach(::loadIcon)
    }

    private fun loadIcon(world: SingleplayerWorld) {
        CompletableFuture
            //? if > 1.8.9 {
            .supplyAsync({ readIcon(world) }, Util.backgroundExecutor())
            //?} else {
            /*.supplyAsync({ readIcon(world) }, Util.ioPool())
            *///?}
            .thenAcceptAsync({ icon ->
                val image = icon?.second?.asComposeImageBitmap()
                if (icon != null && image != null) {
                    icons[world.id] = image
                    iconBytes[world.id] = icon.first
                } else {
                    icons.remove(world.id)
                    iconBytes.remove(world.id)
                }
            }, LobbyCompat.mainExecutor(mc))
    }

    private fun readIcon(world: SingleplayerWorld): Pair<ByteArray, PolyBitmap?>? {
        val path = world.iconPath ?: return null
        return try {
            if (iconAllowed(path)) Files.readAllBytes(path).let { it to PolyBitmap.decode(it) } else null
        } catch (error: IOException) {
            LOGGER.warn("Couldn't read icon for world {}", world.id, error)
            null
        }
    }

    private fun iconAllowed(path: Path): Boolean {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isRegularFile) return true
        if (!attributes.isSymbolicLink) return false
        //? if > 1.8.9 {
        return mc.directoryValidator().validateSymlink(path).isEmpty() && Files.isRegularFile(path)
        //?} else {
        /*return Files.isRegularFile(path)
        *///?}
    }

    companion object {
        private val byHost = WeakHashMap<Screen, SingleplayerState>()

        fun of(host: Screen?): SingleplayerState = if (host == null) SingleplayerState() else byHost.getOrPut(host, ::SingleplayerState)
    }
}
