package org.polyfrost.polyplus.client.party.soundboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.party.LocalMonitor
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PartyVoice
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

object Soundboard {
    private val LOGGER = LogManager.getLogger()

    const val MAX_PLAYING = 4
    const val MAX_VOLUME = 2f
    private const val DEFAULT_VOLUME = 1f
    private const val MAX_SOUNDS = 200
    private const val CACHE_SIZE = 24
    private const val STUCK_GRACE_MS = 3_000L

    data class Sound(val id: String, val name: String, val path: Path)

    private class Decoded(val modified: Long, val pcm: ShortArray)

    private class Playback(val id: String, val pcm: ShortArray, @Volatile var send: Boolean) {
        val preview = !send
        val startedAt = System.currentTimeMillis()
        @Volatile var sent = 0
        @Volatile var heard = 0
        @Volatile var monitored = true
        @Volatile var stopped = false

        fun done(now: Long): Boolean {
            if (stopped) return true
            if (now - startedAt > pcm.size * 1000L / PartyAudio.SAMPLE_RATE + STUCK_GRACE_MS) return true
            val sendDone = !send || sent >= pcm.size
            val heardDone = !monitored || heard >= pcm.size
            return sendDone && heardDone
        }
    }

    private val _sounds = MutableStateFlow<List<Sound>>(emptyList())
    val sounds = _sounds.asStateFlow()

    private val _playing = MutableStateFlow<Set<String>>(emptySet())
    val playing = _playing.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    val problem = _problem.asStateFlow()

    var volumes by mutableStateOf(parseVolumes(PolyPlusConfig.voiceSoundVolumes))
        private set

    var favorites by mutableStateOf(parseSet(PolyPlusConfig.voiceSoundFavorites))
        private set

    private val active = CopyOnWriteArrayList<Playback>()
    private val pending = ConcurrentHashMap<String, Long>()
    private val requests = AtomicLong()
    private val playLock = Any()
    private val cache = object : LinkedHashMap<String, Decoded>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Decoded>?): Boolean = size > CACHE_SIZE
    }

    @Volatile private var loaded = false

    val folder: Path by lazy { FabricLoader.getInstance().gameDir.resolve("polyplus").resolve("soundboard") }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _sounds.value = scan()
    }

    fun reload() {
        loaded = true
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) { _sounds.value = scan() }
    }

    private fun scan(): List<Sound> = runCatching {
        Files.createDirectories(folder)
        Files.list(folder).use { files ->
            files.filter { Files.isRegularFile(it) && SoundDecoder.supports(it) }
                .map { Sound(it.fileName.toString(), it.fileName.toString().substringBeforeLast('.'), it) }
                .sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .limit(MAX_SOUNDS.toLong())
                .toList()
        }
    }.onFailure { LOGGER.warn("Couldn't list soundboard sounds in {}", folder, it) }.getOrDefault(emptyList())

    fun ordered(list: List<Sound> = _sounds.value): List<Sound> {
        val stars = favorites
        return list.filter { it.id in stars } + list.filterNot { it.id in stars }
    }

    fun clearProblem() {
        _problem.value = null
    }

    fun toggle(sound: Sound) {
        if (sound.id in _playing.value) stop(sound.id) else play(sound)
    }

    fun play(sound: Sound) {
        val token = requests.incrementAndGet()
        val send = PartyVoice.canSendSounds()
        synchronized(playLock) { pending[sound.id] = token }
        publish()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val pcm = load(sound)
            synchronized(playLock) {
                if (!pending.remove(sound.id, token) || pcm == null) return@synchronized
                val stillSending = send && PartyVoice.canSendSounds()
                active.filter { it.id == sound.id }.forEach { it.stopped = true }
                val running = active.filterNot { it.stopped }
                if (running.size >= MAX_PLAYING) running.first().stopped = true
                active += Playback(sound.id, pcm, stillSending)
                _problem.value = null
            }
            LocalMonitor.wake()
            publish()
        }
    }

    fun stop(id: String) {
        synchronized(playLock) {
            pending.remove(id)
            active.filter { it.id == id }.forEach { it.stopped = true }
        }
        publish()
    }

    fun stopAll() {
        synchronized(playLock) {
            pending.clear()
            active.forEach { it.stopped = true }
        }
        publish()
    }

    fun onVoiceStopped() {
        synchronized(playLock) {
            pending.clear()
            active.forEach { it.send = false }
        }
        publish()
    }

    internal fun onMonitorUnavailable() {
        active.forEach { it.monitored = false }
        publish()
    }

    internal fun mixOutgoing(frame: ShortArray, allowed: Boolean): Boolean {
        if (active.isEmpty()) return false
        val master = PolyPlusConfig.voiceSoundboardVolume / 100f
        var mixed = false
        for (playback in active) {
            if (playback.stopped || !playback.send) continue
            if (!allowed) {
                playback.send = false
                continue
            }
            val start = playback.sent
            if (start >= playback.pcm.size) continue
            val gain = volumeOf(playback.id) * master
            val count = min(frame.size, playback.pcm.size - start)
            if (gain > 0f) {
                for (i in 0 until count) frame[i] = clip(frame[i] + playback.pcm[start + i] * gain)
                mixed = true
            }
            playback.sent = start + count
        }
        publish()
        return mixed
    }

    internal fun mixMonitor(out: ShortArray): Boolean {
        if (active.isEmpty()) return false
        val hear = PolyPlusConfig.voiceSoundboardMonitor / 100f
        var busy = false
        val frames = out.size / 2
        for (playback in active) {
            if (playback.stopped || !playback.monitored) continue
            val start = playback.heard
            if (start >= playback.pcm.size) continue
            busy = true
            val gain = volumeOf(playback.id) * if (playback.preview) 1f else hear
            val count = min(frames, playback.pcm.size - start)
            if (gain > 0f) {
                for (i in 0 until count) {
                    val sample = playback.pcm[start + i] * gain
                    out[i * 2] = clip(out[i * 2] + sample)
                    out[i * 2 + 1] = clip(out[i * 2 + 1] + sample)
                }
            }
            playback.heard = start + count
        }
        publish()
        return busy
    }

    private fun clip(value: Float): Short = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun publish() {
        val now = System.currentTimeMillis()
        active.removeIf { it.done(now) }
        val ids = active.mapTo(HashSet()) { it.id }
        ids += pending.keys
        if (ids != _playing.value) _playing.value = ids
    }

    private fun load(sound: Sound): ShortArray? {
        val modified = runCatching { Files.getLastModifiedTime(sound.path).toMillis() }.getOrDefault(0L)
        synchronized(cache) { cache[sound.id]?.takeIf { it.modified == modified }?.let { return it.pcm } }
        return runCatching { SoundDecoder.decode(sound.path) }
            .onSuccess { pcm -> synchronized(cache) { cache[sound.id] = Decoded(modified, pcm) } }
            .onFailure { error ->
                LOGGER.warn("Couldn't load soundboard sound {}", sound.path, error)
                _problem.value = (error as? SoundException)?.message?.let { "${sound.name}: $it" } ?: "Couldn't play ${sound.name}."
            }
            .getOrNull()
    }

    suspend fun add(source: Path): String? = withContext(Dispatchers.IO) {
        if (!SoundDecoder.supports(source)) {
            return@withContext "That file type isn't supported. Use ${SoundDecoder.FORMATS}."
        }
        try {
            SoundDecoder.decode(source)
            Files.createDirectories(folder)
            Files.copy(source, freeName(source.fileName.toString()), StandardCopyOption.COPY_ATTRIBUTES)
            _sounds.value = scan()
            null
        } catch (error: SoundException) {
            error.message
        } catch (error: Exception) {
            LOGGER.warn("Couldn't add soundboard sound {}", source, error)
            "Couldn't copy that file into your sounds folder."
        }
    }

    private fun freeName(name: String): Path {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var candidate = folder.resolve(name)
        var index = 2
        while (Files.exists(candidate)) candidate = folder.resolve("$base ($index).$extension").also { index++ }
        return candidate
    }

    fun remove(sound: Sound) {
        stop(sound.id)
        forget(sound.id)
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runCatching { Files.deleteIfExists(sound.path) }.onFailure { LOGGER.warn("Couldn't delete soundboard sound {}", sound.path, it) }
            synchronized(cache) { cache.remove(sound.id) }
            _sounds.value = scan()
        }
    }

    private fun forget(id: String) {
        if (id in volumes) {
            volumes = volumes - id
            PolyPlusConfig.voiceSoundVolumes = encodeVolumes(volumes)
        }
        if (id in favorites) {
            favorites = favorites - id
            PolyPlusConfig.voiceSoundFavorites = encodeSet(favorites)
        }
        PolyPlusConfig.save()
    }

    fun volumeOf(id: String): Float = volumes[id] ?: DEFAULT_VOLUME

    fun setVolume(id: String, volume: Float) {
        val clamped = volume.coerceIn(0f, MAX_VOLUME)
        volumes = if (clamped == DEFAULT_VOLUME) volumes - id else volumes + (id to clamped)
        PolyPlusConfig.voiceSoundVolumes = encodeVolumes(volumes)
    }

    fun isFavorite(id: String): Boolean = id in favorites

    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        PolyPlusConfig.voiceSoundFavorites = encodeSet(favorites)
        PolyPlusConfig.save()
    }

    fun save() = PolyPlusConfig.save()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

    private fun encodeVolumes(map: Map<String, Float>): String = map.entries.joinToString(",") { "${encode(it.key)}=${it.value}" }

    private fun encodeSet(set: Set<String>): String = set.joinToString(",") { encode(it) }

    private fun parseVolumes(raw: String): Map<String, Float> = raw.split(',').mapNotNull { entry ->
        val key = decode(entry.substringBefore('=', "").trim())
        val value = entry.substringAfter('=', "").toFloatOrNull()
        if (key.isEmpty() || value == null) null else key to value.coerceIn(0f, MAX_VOLUME)
    }.toMap()

    private fun parseSet(raw: String): Set<String> = raw.split(',').map { decode(it.trim()) }.filter { it.isNotEmpty() }.toSet()
}
