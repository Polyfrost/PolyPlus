package org.polyfrost.polyplus.compat.replay

import com.google.gson.Gson
import com.google.gson.JsonObject
//? if >= 26.3
import com.mojang.blaze3d.Blaze3D
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
//? if >= 1.21.11 && < 26.3
//import net.minecraft.util.Util
//? if > 1.8.9 && < 1.21.11
//import net.minecraft.Util
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.Mods
import org.polyfrost.polyplus.compat.Reflect
import org.polyfrost.polyplus.compat.ReflectiveHook
//? if = 1.8.9
//import java.awt.Desktop
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

object FlashbackReplays : ReplayProvider {
    private const val MAIN = "com.moulberry.flashback.Flashback"
    private const val SELECT_SCREEN = "com.moulberry.flashback.screen.select_replay.SelectReplayScreen"
    private const val METADATA = "metadata.json"
    private const val ICON = "icon.png"
    private const val MILLIS_PER_TICK = 50L
    private const val NEWER_VERSION_WARNING = "Recorded on a newer game version"
    private const val MAX_DEPTH = 8
    private const val MAX_FILES = 5000

    private val logger = LogManager.getLogger("PolyPlus/FlashbackReplays")
    private val gson = Gson()
    private val loggedUnreadable = AtomicBoolean()
    private val failures = ReflectiveHook(logger)
    private val cache = ConcurrentHashMap<Path, ReplayEntry>()

    private class ReplayFile(val path: Path, val modified: Long, val size: Long)

    private class Hooks(
        val recorder: Field,
        val isPaused: Method,
        val inReplay: Method,
        val replayFolder: Method?,
        val start: Method,
        val pause: Method,
        val finish: Method,
        val cancel: Method,
        val openReplay: Method,
        val writtenTicks: Field?,
    )

    private val hooks: Hooks? by lazy {
        runCatching {
            val main = Class.forName(MAIN, false, FlashbackReplays::class.java.classLoader)
            val recorder = main.getField("RECORDER")
            Hooks(
                recorder = recorder,
                isPaused = recorder.type.getMethod("isPaused"),
                inReplay = main.getMethod("isInReplay"),
                replayFolder = runCatching { main.getMethod("getReplayFolder") }.getOrNull(),
                start = main.getMethod("startRecordingReplay"),
                pause = main.getMethod("pauseRecordingReplay", java.lang.Boolean.TYPE),
                finish = main.getMethod("finishRecordingReplay"),
                cancel = main.getMethod("cancelRecordingReplay"),
                openReplay = main.getMethod("openReplayWorld", Path::class.java),
                writtenTicks = privateField(recorder.type, "writtenTicks"),
            )
        }.getOrNull()
    }

    private val selectScreen: Constructor<*>? by lazy {
        runCatching {
            Class.forName(SELECT_SCREEN, false, FlashbackReplays::class.java.classLoader).getConstructor(Screen::class.java)
        }.getOrNull()
    }

    private fun privateField(owner: Class<*>, name: String): Field? = runCatching { Reflect.declared(owner, name) }.getOrNull()

    override val id = "flashback"
    override val name = "Flashback"

    override val available: Boolean get() = Mods.flashback && hooks != null

    override val inReplay: Boolean get() = call { it.inReplay.invoke(null) as Boolean } ?: false

    private fun replayFolder(): Path {
        val folder = hooks?.replayFolder?.let { method -> runCatching { method.invoke(null) as? Path }.getOrNull() }
        return folder ?: FabricLoader.getInstance().gameDir.resolve("flashback").resolve("replays")
    }

    override fun list(): List<ReplayEntry> {
        if (!available) return emptyList()
        val folder = runCatching { replayFolder() }.getOrNull() ?: return emptyList()
        if (!Files.isDirectory(folder)) {
            cache.clear()
            return emptyList()
        }
        val files = replayFiles(folder)
        cache.keys.retainAll(files.mapTo(HashSet()) { it.path })
        val currentDataVersion = runCatching { LobbyCompat.dataVersion() }.getOrNull()
        return files.mapNotNull { file ->
            cache[file.path]?.takeIf { it.sameFile(file.modified, file.size) }
                ?: read(file, currentDataVersion)?.also { cache[file.path] = it }
        }.sortedByDescending { it.modified }
    }

    private fun replayFiles(folder: Path): List<ReplayFile> {
        val found = ArrayList<ReplayFile>()
        Files.walkFileTree(folder, emptySet(), MAX_DEPTH, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && file.fileName.toString().endsWith(".zip", ignoreCase = true)) {
                    found += ReplayFile(file, attrs.lastModifiedTime().toMillis(), attrs.size())
                }
                return if (found.size >= MAX_FILES) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                if (file == folder) throw exc
                logUnreadable(file, exc)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) logUnreadable(dir, exc)
                return FileVisitResult.CONTINUE
            }
        })
        return found
    }

    private fun readIcon(file: Path): ByteArray? = ZipFile(file.toFile()).use { zip ->
        zip.getEntry(ICON)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
    }

    private fun read(replay: ReplayFile, currentDataVersion: Int?): ReplayEntry? = runCatching {
        val file = replay.path
        ZipFile(file.toFile()).use { zip ->
            val metaEntry = zip.getEntry(METADATA) ?: return@runCatching null
            val meta = InputStreamReader(zip.getInputStream(metaEntry), Charsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
                ?: return@runCatching null
            val hasIcon = zip.getEntry(ICON) != null
            val dataVersion = meta.intOrNull("data_version")
            val tooNew = dataVersion != null && currentDataVersion != null && dataVersion > currentDataVersion
            ReplayEntry(
                provider = this,
                path = file,
                name = meta.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: file.fileName.toString().removeSuffix(".zip"),
                world = meta.stringOrNull("world_name")?.takeIf { it.isNotBlank() },
                durationMillis = meta.intOrNull("total_ticks")?.let { it.toLong() * MILLIS_PER_TICK },
                modified = replay.modified,
                size = replay.size,
                iconSource = { if (hasIcon) readIcon(file) else null },
                playable = !tooNew,
                warning = if (tooNew) NEWER_VERSION_WARNING else null,
            )
        }
    }.onFailure { logUnreadable(replay.path, it) }.getOrNull()

    private fun JsonObject.stringOrNull(key: String): String? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

    private fun JsonObject.intOrNull(key: String): Int? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

    private fun logUnreadable(path: Path, error: Throwable) {
        if (loggedUnreadable.compareAndSet(false, true)) logger.debug("Skipping unreadable Flashback replay {}", path, error)
    }

    override fun watch(entry: ReplayEntry) {
        if (!entry.playable) return
        ClientPlatform.runOnMain { call { it.openReplay.invoke(null, entry.path) } }
    }

    override fun openFolder() {
        val folder = runCatching { replayFolder() }.getOrNull() ?: return
        runCatching { Files.createDirectories(folder) }
        ClientPlatform.runOnMain {
            runCatching {
                //? if >= 26.3 {
                Blaze3D.openPath(folder)
                //?} elif > 1.8.9 {
                /*Util.getPlatform().openPath(folder)
                *///?} else {
                /*Desktop.getDesktop().open(folder.toFile())
                *///?}
            }.onFailure { logFailure(it) }
        }
    }

    override fun openNativeBrowser() {
        val constructor = selectScreen ?: return
        ClientPlatform.runOnMain {
            runCatching {
                val parent = ClientPlatform.currentScreen()
                (constructor.newInstance(parent) as? Screen)?.let(ClientPlatform::setScreen)
            }.onFailure { logFailure(it) }
        }
    }

    private class Session(val recorder: Any, val startedAt: Long) {
        var pausedSince: Long? = null
        var pausedTotal: Long = 0
    }

    @Volatile
    private var session: Session? = null

    override val recording: RecordingState?
        get() {
            val hooks = hooks ?: return null
            if (inReplay) return null
            return runCatching {
                val recorder = hooks.recorder.get(null)
                if (recorder == null) {
                    session = null
                    return@runCatching null
                }
                val paused = hooks.isPaused.invoke(recorder) as Boolean
                RecordingState(paused, elapsedMillis(hooks, recorder, paused))
            }.onFailure { logFailure(it) }.getOrNull()
        }

    private fun elapsedMillis(hooks: Hooks, recorder: Any, paused: Boolean): Long? {
        hooks.writtenTicks?.let { written ->
            runCatching { written.getInt(recorder).toLong() * MILLIS_PER_TICK }.getOrNull()?.let { return it }
        }
        val now = System.currentTimeMillis()
        val current = session?.takeIf { it.recorder === recorder } ?: Session(recorder, now).also { session = it }
        synchronized(current) {
            val since = current.pausedSince
            if (paused && since == null) current.pausedSince = now
            if (!paused && since != null) {
                current.pausedTotal += now - since
                current.pausedSince = null
            }
            val pausedNow = current.pausedSince?.let { now - it } ?: 0L
            return (now - current.startedAt - current.pausedTotal - pausedNow).coerceAtLeast(0L)
        }
    }

    override val canStart: Boolean
        get() = available && !inReplay && ClientPlatform.inWorld() && recording == null

    override val canPause: Boolean get() = recording != null

    override val canDiscard: Boolean get() = recording != null

    override val controlKeyPrefixes: List<String> = listOf("flashback.recording_controls.")

    override fun start() = ClientPlatform.runOnMain { call { it.start.invoke(null) } }

    override fun setPaused(paused: Boolean) = ClientPlatform.runOnMain { call { it.pause.invoke(null, paused) } }

    override fun finish() = ClientPlatform.runOnMain { call { it.finish.invoke(null) } }

    override fun discard() = ClientPlatform.runOnMain { call { it.cancel.invoke(null) } }

    private fun <T> call(action: (Hooks) -> T): T? {
        val hooks = hooks ?: return null
        return runCatching { action(hooks) }.onFailure { logFailure(it) }.getOrNull()
    }

    private fun logFailure(error: Throwable) = failures.fail("Flashback call failed", error)
}
