package org.polyfrost.polyplus.compat.replay

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.InputStreamReader
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.streams.asSequence

object FlashbackReplays : ReplayProvider {
    private const val MOD_ID = "flashback"
    private const val MAIN = "com.moulberry.flashback.Flashback"
    private const val SELECT_SCREEN = "com.moulberry.flashback.screen.select_replay.SelectReplayScreen"
    private const val METADATA = "metadata.json"
    private const val ICON = "icon.png"
    private const val MILLIS_PER_TICK = 50L
    private const val NEWER_VERSION_WARNING = "Recorded on a newer game version"

    private val logger = LogManager.getLogger("PolyPlus/FlashbackReplays")
    private val gson = Gson()
    private val loggedUnreadable = AtomicBoolean()
    private val loggedFailure = AtomicBoolean()

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
        val chunkTicks: Field?,
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
                chunkTicks = privateField(recorder.type, "writtenTicksInChunk"),
            )
        }.getOrNull()
    }

    private val selectScreen: Constructor<*>? by lazy {
        runCatching {
            Class.forName(SELECT_SCREEN, false, FlashbackReplays::class.java.classLoader).getConstructor(Screen::class.java)
        }.getOrNull()
    }

    private fun privateField(owner: Class<*>, name: String): Field? =
        runCatching { owner.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    private val modLoaded: Boolean by lazy { runCatching { FabricLoader.getInstance().isModLoaded(MOD_ID) }.getOrDefault(false) }

    override val id = "flashback"
    override val name = "Flashback"

    override val available: Boolean get() = modLoaded && hooks != null

    override val inReplay: Boolean get() = call { it.inReplay.invoke(null) as Boolean } ?: false

    private fun replayFolder(): Path {
        val folder = hooks?.replayFolder?.let { method -> runCatching { method.invoke(null) as? Path }.getOrNull() }
        return folder ?: FabricLoader.getInstance().gameDir.resolve("flashback").resolve("replays")
    }

    override fun list(): List<ReplayEntry> {
        if (!available) return emptyList()
        val folder = runCatching { replayFolder() }.getOrNull() ?: return emptyList()
        if (!Files.isDirectory(folder)) return emptyList()
        val files = runCatching {
            Files.walk(folder).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".zip", ignoreCase = true) }
                    .toList()
            }
        }.getOrElse {
            logUnreadable(folder, it)
            return emptyList()
        }
        val currentDataVersion = runCatching { LobbyCompat.dataVersion() }.getOrNull()
        return files.mapNotNull { read(it, currentDataVersion) }.sortedByDescending { it.modified }
    }

    private fun read(file: Path, currentDataVersion: Int?): ReplayEntry? = runCatching {
        ZipFile(file.toFile()).use { zip ->
            val metaEntry = zip.getEntry(METADATA) ?: return@runCatching null
            val meta = InputStreamReader(zip.getInputStream(metaEntry), Charsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
                ?: return@runCatching null
            val icon = zip.getEntry(ICON)?.let { entry -> runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull() }
            val dataVersion = meta.intOrNull("dataVersion")
            val tooNew = dataVersion != null && currentDataVersion != null && dataVersion > currentDataVersion
            ReplayEntry(
                provider = this,
                path = file,
                name = meta.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: file.fileName.toString().removeSuffix(".zip"),
                world = meta.stringOrNull("worldName")?.takeIf { it.isNotBlank() },
                durationMillis = meta.intOrNull("totalTicks")?.let { it.toLong() * MILLIS_PER_TICK },
                modified = Files.getLastModifiedTime(file).toMillis(),
                size = Files.size(file),
                icon = icon,
                playable = !tooNew,
                warning = if (tooNew) NEWER_VERSION_WARNING else null,
            )
        }
    }.onFailure { logUnreadable(file, it) }.getOrNull()

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
                com.mojang.blaze3d.Blaze3D.openPath(folder)
                //?} elif >= 1.21.11 {
                /*net.minecraft.util.Util.getPlatform().openPath(folder)
                *///?} elif > 1.8.9 {
                /*net.minecraft.Util.getPlatform().openPath(folder)
                *///?} else {
                /*java.awt.Desktop.getDesktop().open(folder.toFile())
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
        val written = hooks.writtenTicks
        val chunk = hooks.chunkTicks
        if (written != null && chunk != null) {
            runCatching { (written.getInt(recorder).toLong() + chunk.getInt(recorder).toLong()) * MILLIS_PER_TICK }
                .getOrNull()?.let { return it }
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

    private fun logFailure(error: Throwable) {
        if (loggedFailure.compareAndSet(false, true)) logger.warn("Flashback call failed", error)
    }
}
