package org.polyfrost.polyplus.compat.replay

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.Mods
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.zip.ZipFile

object ReplayModReplays : ReplayProvider {
    private const val MOD_ID = "replaymod"
    private const val EXTENSION = ".mcpr"
    private const val DEFAULT_FOLDER = "replay_recordings"
    private val THUMB_MAGIC = byteArrayOf(0, 1, 1, 2, 3, 5, 8)
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    private val cache = ConcurrentHashMap<Path, ReplayEntry>()

    override val id = MOD_ID
    override val name = "Replay Mod"

    private val loaded: Boolean get() = Mods.replayMod

    private class Core {
        val modClass: Class<*> = Class.forName("com.replaymod.core.ReplayMod")
        val instance: Field = modClass.getField("instance")
        val folders: Field = modClass.getField("folders")
        val replayFolder: Method = Class.forName("com.replaymod.core.files.ReplayFoldersService").getMethod("getReplayFolder")
        val isCompatible: Method = modClass.getMethod("isCompatible", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    }

    private class Replay {
        val replayClass: Class<*> = Class.forName("com.replaymod.replay.ReplayModReplay")
        val instance: Field = replayClass.getField("instance")
        val replayHandler: Method = replayClass.getMethod("getReplayHandler")
        val startReplay: Method = replayClass.getMethod("startReplay", File::class.java)
        val viewer: java.lang.reflect.Constructor<*> = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer").getConstructor(replayClass)
        val display: Method = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen").getMethod("display")
    }

    private class Recording {
        val recordingClass: Class<*> = Class.forName("com.replaymod.recording.ReplayModRecording")
        val instance: Field = recordingClass.getField("instance")
        val connectionHandler: Method = recordingClass.getMethod("getConnectionEventHandler")
        val handlerClass: Class<*> = Class.forName("com.replaymod.recording.handler.ConnectionEventHandler")
        val packetListener: Method = handlerClass.getMethod("getPacketListener")
        val controls: Field = handlerClass.getDeclaredField("guiControls").apply { isAccessible = true }
        val currentDuration: Method = Class.forName("com.replaymod.recording.packet.PacketListener").getMethod("getCurrentDuration")
        val controlsClass: Class<*> = Class.forName("com.replaymod.recording.gui.GuiRecordingControls")
        val isPaused: Method = controlsClass.getMethod("isPaused")
        val isStopped: Method = controlsClass.getMethod("isStopped")
        val pauseButton: Field = controlsClass.getDeclaredField("buttonPauseResume").apply { isAccessible = true }
        val startStopButton: Field = controlsClass.getDeclaredField("buttonStartStop").apply { isAccessible = true }
        val onClick: Method = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.element.IGuiClickable").getMethod("getOnClick")
        val consume: Method = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.utils.Consumer").getMethod("consume", Any::class.java)
    }

    private class Wrappers {
        val screenClass: Class<*> = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen")
        val map: Field = screenClass.getDeclaredField("WRAPPERS").apply { isAccessible = true }
        val handler: Field = screenClass.getDeclaredField("eventHandler").apply { isAccessible = true }
        val unregister: Method = Class.forName("com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations").getMethod("unregister")
    }

    private val wrappers: Wrappers? by lazy { if (loaded) runCatching { Wrappers() }.getOrNull() else null }

    override fun detachPauseOverlay(screen: Any) {
        val w = wrappers ?: return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val map = w.map.get(null) as MutableMap<Any?, Any?>
            val wrapper = synchronized(map) { map.remove(screen) } ?: return
            w.unregister.invoke(w.handler.get(wrapper))
        }
    }

    private val core: Core? by lazy { if (loaded) runCatching { Core() }.getOrNull() else null }
    private val replay: Replay? by lazy { if (loaded) runCatching { Replay() }.getOrNull() else null }
    private val recordingHandles: Recording? by lazy { if (loaded) runCatching { Recording() }.getOrNull() else null }

    override val available: Boolean
        get() = loaded && core != null && replay != null

    override val inReplay: Boolean
        get() {
            val r = replay ?: return false
            return runCatching { r.instance.get(null)?.let { r.replayHandler.invoke(it) } != null }.getOrDefault(false)
        }

    private fun folder(): Path? {
        val c = core
        if (c != null) {
            val fromMod = runCatching {
                val mod = c.instance.get(null) ?: return@runCatching null
                val folders = c.folders.get(mod) ?: return@runCatching null
                c.replayFolder.invoke(folders) as? Path
            }.getOrNull()
            if (fromMod != null) return fromMod
        }
        return runCatching { FabricLoader.getInstance().gameDir.resolve(DEFAULT_FOLDER) }.getOrNull()
    }

    override fun list(): List<ReplayEntry> {
        if (!available) return emptyList()
        val dir = folder() ?: return emptyList()
        if (!Files.isDirectory(dir)) {
            cache.clear()
            return emptyList()
        }
        val files = Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(EXTENSION, ignoreCase = true) }.collect(Collectors.toList())
        }
        cache.keys.retainAll(files.toSet())
        return files.mapNotNull { path ->
            runCatching {
                val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
                val size = runCatching { Files.size(path) }.getOrDefault(0L)
                cache[path]?.takeIf { it.sameFile(modified, size) } ?: read(path, modified, size).also { cache[path] = it }
            }.getOrNull()
        }.sortedByDescending { it.modified }
    }

    private fun read(path: Path, modified: Long, size: Long): ReplayEntry {
        val fileName = path.fileName.toString()
        val name = fileName.substring(0, fileName.length - EXTENSION.length)
        var meta: JsonObject? = null
        runCatching {
            ZipFile(path.toFile()).use { zip ->
                meta = zip.getEntry("metaData.json")?.let { entry ->
                    zip.getInputStream(entry).use { JsonParser.parseReader(it.reader()).asJsonObject }
                }
            }
        }
        val data = meta
        val serverName = data?.string("serverName")?.takeIf { it.isNotBlank() }
        val singleplayer = data?.bool("singleplayer") == true
        val world = serverName ?: if (singleplayer) "Singleplayer" else null
        val duration = data?.long("duration")?.takeIf { it > 0 }
        val compatible = data?.let { compatible(it) } ?: true
        val mcVersion = data?.string("mcversion")?.takeIf { it.isNotBlank() }
        val warning = when {
            data == null -> "Couldn't read this replay"
            !compatible && mcVersion != null -> "Recorded in $mcVersion, can't be played on this version"
            !compatible -> "Can't be played on this version"
            else -> null
        }
        return ReplayEntry(
            provider = this,
            path = path,
            name = name,
            world = world,
            durationMillis = duration,
            modified = modified,
            size = size,
            iconSource = { ZipFile(path.toFile()).use(::thumbnail) },
            playable = data != null && compatible,
            warning = warning,
        )
    }

    private fun compatible(meta: JsonObject): Boolean {
        val c = core ?: return true
        val format = meta.long("fileFormatVersion")?.toInt() ?: return true
        val protocol = meta.long("protocol")?.toInt() ?: 0
        return runCatching { c.isCompatible.invoke(null, format, protocol) as Boolean }.getOrDefault(true)
    }

    private fun thumbnail(zip: ZipFile): ByteArray? {
        val entry = zip.getEntry("thumb.jpg") ?: zip.getEntry("thumb") ?: return null
        var bytes = zip.getInputStream(entry).use { it.readBytes() }
        if (bytes.size > THUMB_MAGIC.size && THUMB_MAGIC.indices.all { bytes[it] == THUMB_MAGIC[it] }) {
            bytes = bytes.copyOfRange(THUMB_MAGIC.size, bytes.size)
        }
        val jpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes.size > PNG_MAGIC.size && PNG_MAGIC.indices.all { bytes[it] == PNG_MAGIC[it] }
        return if (jpeg || png) bytes else null
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong }.getOrNull()

    override fun watch(entry: ReplayEntry) {
        val r = replay ?: return
        val file = entry.path.toFile()
        ClientPlatform.runOnMain {
            runCatching {
                val instance = r.instance.get(null) ?: return@runCatching
                r.startReplay.invoke(instance, file)
            }
        }
    }

    override fun openFolder() {
        if (!available) return
        val dir = folder() ?: return
        runCatching { Files.createDirectories(dir) }
        ClientPlatform.runOnMain {
            runCatching {
                //? if >= 26.3 {
                com.mojang.blaze3d.Blaze3D.openPath(dir)
                //?} elif >= 1.21.11 {
                /*net.minecraft.util.Util.getPlatform().openPath(dir)
                *///?} elif > 1.8.9 {
                /*net.minecraft.Util.getPlatform().openPath(dir)
                *///?} else {
                /*java.awt.Desktop.getDesktop().open(dir.toFile())
                *///?}
            }
        }
    }

    override fun openNativeBrowser() {
        val r = replay ?: return
        ClientPlatform.runOnMain {
            runCatching {
                val instance = r.instance.get(null) ?: return@runCatching
                r.display.invoke(r.viewer.newInstance(instance))
            }
        }
    }

    private class Session(val handles: Recording, val listener: Any, val controls: Any?)

    private fun session(): Session? {
        if (!available) return null
        val h = recordingHandles ?: return null
        return runCatching {
            val mod = h.instance.get(null) ?: return@runCatching null
            val handler = h.connectionHandler.invoke(mod) ?: return@runCatching null
            val listener = h.packetListener.invoke(handler) ?: return@runCatching null
            Session(h, listener, h.controls.get(handler))
        }.getOrNull()
    }

    private fun Session.paused(): Boolean =
        controls != null && runCatching { handles.isPaused.invoke(controls) as Boolean }.getOrDefault(false)

    private fun Session.stopped(): Boolean =
        controls != null && runCatching { handles.isStopped.invoke(controls) as Boolean }.getOrDefault(false)

    private fun Session.press(button: Field) {
        val target = controls ?: return
        ClientPlatform.runOnMain {
            runCatching {
                val widget = button.get(target) ?: return@runCatching
                val callback = handles.onClick.invoke(widget) ?: return@runCatching
                handles.consume.invoke(callback, null)
            }
        }
    }

    override val recording: RecordingState?
        get() {
            val s = session() ?: return null
            if (s.stopped()) return null
            val elapsed = runCatching { s.handles.currentDuration.invoke(s.listener) as Long }.getOrNull()
            return RecordingState(paused = s.paused(), elapsedMillis = elapsed)
        }

    override val canStart: Boolean
        get() = session()?.let { it.controls != null && it.stopped() } ?: false

    override val canPause: Boolean
        get() = session()?.let { it.controls != null && !it.stopped() } ?: false

    override val canDiscard = false

    override val controlKeyPrefixes: List<String> = listOf("replaymod.gui.recording.")

    override fun start() {
        val s = session() ?: return
        if (s.stopped()) s.press(s.handles.startStopButton)
    }

    override fun setPaused(paused: Boolean) {
        val s = session() ?: return
        if (s.stopped()) return
        if (s.paused() != paused) s.press(s.handles.pauseButton)
    }

    override fun finish() {
        val s = session() ?: return
        if (!s.stopped()) s.press(s.handles.startStopButton)
    }

    override fun discard() = Unit
}
