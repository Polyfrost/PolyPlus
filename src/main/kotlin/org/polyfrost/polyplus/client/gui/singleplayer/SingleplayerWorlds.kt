package org.polyfrost.polyplus.client.gui.singleplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.lobby.LobbyCompat
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

private val LOGGER = LogManager.getLogger("PolyPlus/SingleplayerWorlds")

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L
private const val DAYS_IN_YEAR = 365L
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

internal fun playedText(lastPlayed: Long): String {
    if (lastPlayed <= 0L) return "not played yet"
    val zone = ZoneId.systemDefault()
    val played = Instant.ofEpochMilli(lastPlayed).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(played, LocalDate.now(zone))
    return when {
        days <= 0L -> "played today"
        days == 1L -> "played yesterday"
        days < DAYS_IN_WEEK -> "played $days days ago"
        days < DAYS_IN_MONTH -> plural("played", days / DAYS_IN_WEEK, "week")
        days < DAYS_IN_YEAR -> plural("played", days / DAYS_IN_MONTH, "month")
        else -> plural("played", days / DAYS_IN_YEAR, "year")
    }
}

private fun plural(prefix: String, count: Long, unit: String): String =
    "$prefix $count $unit${if (count == 1L) "" else "s"} ago"

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

    fun matches(query: String): Boolean {
        val needle = query.lowercase(Locale.ROOT)
        return name.lowercase(Locale.ROOT).contains(needle) || id.lowercase(Locale.ROOT).contains(needle)
    }
}

internal class SingleplayerState {
    private val mc = Minecraft.getInstance()
    private var token = 0

    val flows = SingleplayerFlows()
    val icons = mutableStateMapOf<String, ImageBitmap>()

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
        val matching = worlds.filter { it.matches(filter) && it.matches(query) }
        return if (narrowed) matching else lastPlayedWorld.let { last -> matching.filter { it !== last } }
    }

    fun reload() {
        val current = ++token
        //? if = 1.8.9 {
        /*runCatching { LevelSummary.all(mc) }.fold({ if (current == token) publish(it) }, {
            LOGGER.error("Couldn't load level list", it)
            fail(tr("selectWorld.unable_to_load"))
        })
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
        val list = summaries.map(::SingleplayerWorld)
        val keep = list.mapTo(HashSet()) { it.id }
        icons.keys.retainAll(keep)
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
            .thenAcceptAsync({ bitmap ->
                val image = bitmap?.asComposeImageBitmap()
                if (image != null) icons[world.id] = image else icons.remove(world.id)
            }, LobbyCompat.mainExecutor(mc))
    }

    private fun readIcon(world: SingleplayerWorld): PolyBitmap? {
        val path = world.iconPath ?: return null
        return try {
            if (iconAllowed(path)) PolyBitmap.decode(Files.readAllBytes(path)) else null
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
