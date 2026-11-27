package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.CosmeticSounds
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import com.jcraft.jogg.Packet
import com.jcraft.jogg.Page
import com.jcraft.jogg.StreamState
import com.jcraft.jogg.SyncState
import com.jcraft.jorbis.Comment
import com.jcraft.jorbis.Info
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sound.sampled.AudioFormat

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.SoundBuffer
import it.unimi.dsi.fastutil.floats.FloatConsumer
import org.lwjgl.BufferUtils
import java.io.IOException
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import java.util.concurrent.CompletableFuture
//?} else {
/*import net.minecraft.client.resource.Resource
import net.minecraft.client.resource.SimpleResource
import net.minecraft.client.sound.system.SoundEngine
import paulscode.sound.SoundSystem
import paulscode.sound.codecs.CodecJOrbis
import java.lang.reflect.Field
import java.util.WeakHashMap
import java.util.function.BooleanSupplier
import net.minecraft.client.sound.Sound
import net.minecraft.client.sound.SoundCategory
import net.minecraft.client.sound.SoundContainer
import net.minecraft.client.sound.SoundPool
import net.minecraft.client.sound.instance.SimpleSoundInstance
import net.minecraft.entity.particle.ParticleType
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
*///?}

internal class CosmeticSoundRef(val vanilla: String?, val file: Identifier?, val key: String? = null)

internal object CosmeticSoundLimits {
    const val MAX_SECONDS = 3
    const val MAX_RATE = 96_000
    const val MAX_CHANNELS = 2
    const val PADDING_SLACK = 8_192L

    fun fits(rate: Int, channels: Int): Boolean = rate in 1..MAX_RATE && channels in 1..MAX_CHANNELS

    fun maxSamples(rate: Int): Long = rate.toLong() * MAX_SECONDS

    fun pcmBytes(samples: Long, rate: Int, channels: Int): Int =
        (samples.coerceIn(0, maxSamples(rate)) * channels * 2).toInt()

    fun samples(bytes: ByteArray): Long = try {
        scan(bytes)
    } catch (_: Exception) {
        -1
    }

    private fun scan(bytes: ByteArray): Long {
        val sync = SyncState()
        val stream = StreamState()
        val page = Page()
        val packet = Packet()
        val info = Info()
        val comment = Comment()
        sync.init()
        info.init()
        comment.init()
        val offset = sync.buffer(bytes.size)
        System.arraycopy(bytes, 0, sync.data, offset, bytes.size)
        sync.wrote(bytes.size)
        var headers = 0
        var started = false
        var previous = 0
        var samples = 0L
        var limit = 0L
        var granule = 0L
        while (true) {
            val paged = sync.pageout(page)
            if (paged == 0) break
            if (paged < 0) continue
            if (!started) {
                stream.init(page.serialno())
                started = true
            }
            if (stream.pagein(page) < 0) return -1
            val position = page.granulepos()
            if (position != -1L) {
                if (position < granule) return -1
                granule = position
            }
            while (true) {
                val out = stream.packetout(packet)
                if (out == 0) break
                if (out < 0) return -1
                if (headers < 3) {
                    if (info.synthesis_headerin(comment, packet) < 0) return -1
                    headers++
                    if (headers == 1) {
                        if (!fits(info.rate, info.channels)) return -1
                        limit = maxSamples(info.rate)
                    }
                    continue
                }
                val size = info.blocksize(packet)
                if (size <= 0) continue
                if (previous > 0) samples += previous / 4 + size / 4
                previous = size
                if (samples > limit + PADDING_SLACK) return -1
            }
        }
        if (headers != 3 || granule <= 0 || granule > samples || granule > limit) return -1
        return granule
    }
}

internal class RetiredBuffers(private val graceMs: Long, private val retryMs: Long = RETRY_MS) {
    private class Entry(val owner: Any, val discard: () -> Boolean, var due: Long)

    private val entries = ArrayList<Entry>()

    val size: Int
        @Synchronized get() = entries.size

    @Synchronized
    fun retire(owner: Any, discard: () -> Boolean, now: Long) {
        entries += Entry(owner, discard, now + graceMs)
    }

    @Synchronized
    fun discardNow(owner: Any, discard: () -> Boolean, now: Long) {
        entries += Entry(owner, discard, now)
    }

    fun expire(now: Long) {
        val due = ArrayList<Entry>()
        synchronized(this) {
            val held = entries.iterator()
            while (held.hasNext()) {
                val entry = held.next()
                if (entry.due > now) continue
                held.remove()
                due += entry
            }
        }
        for (entry in due) {
            if (entry.discard()) continue
            entry.due = now + retryMs
            synchronized(this) { entries += entry }
        }
    }

    fun flush(owner: Any) {
        val due = ArrayList<Entry>()
        synchronized(this) {
            val held = entries.iterator()
            while (held.hasNext()) {
                val entry = held.next()
                if (entry.owner === owner) {
                    held.remove()
                    due += entry
                }
            }
        }
        for (entry in due) entry.discard()
    }

    private companion object {
        const val RETRY_MS = 1_000L
    }
}

internal class SoundReleases {
    private val latest = HashMap<String, Long>()
    private var serial = 0L

    @Synchronized
    fun released(key: String): Long {
        val next = ++serial
        latest[key] = next
        return next
    }

    @Synchronized
    fun pending(key: String): Boolean = key in latest

    @Synchronized
    fun settle(key: String, serial: Long): Boolean {
        if (latest[key] != serial) return false
        latest.remove(key)
        return true
    }
}

internal object StereoDownmix {
    fun format(format: AudioFormat): AudioFormat =
        AudioFormat(format.encoding, format.sampleRate, format.sampleSizeInBits, 1, format.sampleSizeInBits / 8, format.frameRate, format.isBigEndian)

    fun applies(format: AudioFormat): Boolean = format.channels == 2 && format.sampleSizeInBits == 16

    fun pcm16(stereo: ByteBuffer, bigEndian: Boolean): ByteBuffer {
        val order = if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val source = stereo.duplicate().order(order)
        val frames = source.remaining() / 4
        val mono = ByteBuffer.allocateDirect(frames * 2).order(order)
        repeat(frames) {
            val left = source.short.toInt()
            val right = source.short.toInt()
            mono.putShort(((left + right) shr 1).toShort())
        }
        mono.flip()
        return mono
    }
}

object CosmeticSoundLibrary {
    private const val DIRECTORY = "cosmetic"
    private const val FILE_PREFIX = "sounds/$DIRECTORY/"
    private const val FILE_SUFFIX = ".ogg"

    private val LOGGER = LogManager.getLogger()

    private class SoundFile(@Volatile var bytes: ByteArray) {
        var refs = 0

        @Volatile
        var samples = CosmeticSoundLimits.samples(bytes)

        val playable get() = samples >= 0
    }

    private val files = ConcurrentHashMap<String, SoundFile>()

    internal fun register(key: String, bytes: ByteArray): Identifier {
        val file = files.compute(key) { _, held ->
            val file = held ?: SoundFile(bytes)
            if (file.bytes !== bytes && !file.bytes.contentEquals(bytes)) {
                file.bytes = bytes
                file.samples = CosmeticSoundLimits.samples(bytes)
                dropBuffer(key)
                //? if = 1.8.9 {
                /*replacedKeys += key
                *///?}
            }
            file.refs++
            file
        }
        if (file != null && !file.playable && file.refs == 1) {
            LOGGER.warn("Cosmetic sound {} is longer than {} s or not a supported Ogg Vorbis stream; it won't play", key, CosmeticSoundLimits.MAX_SECONDS)
        }
        //? if = 1.8.9 {
        /*if (file != null && file.refs == 1 && releases.pending(key)) replacedKeys += key
        if (file != null && file.playable) prepareMono(key, file.bytes)
        *///?}
        return Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$DIRECTORY/$key")
    }

    internal fun playable(key: String): Boolean = files[key]?.playable == true

    internal fun release(key: String) {
        files.computeIfPresent(key) { _, held ->
            held.refs--
            if (held.refs > 0) {
                held
            } else {
                dropBuffer(key)
                null
            }
        }
    }

    @JvmStatic
    fun owns(path: Identifier): Boolean = fileKey(path) != null

    @JvmStatic
    fun downmixes(format: AudioFormat): Boolean = StereoDownmix.applies(format)

    @JvmStatic
    fun downmix(data: ByteBuffer, format: AudioFormat): ByteBuffer = StereoDownmix.pcm16(data, format.isBigEndian)

    @JvmStatic
    fun monoFormat(format: AudioFormat): AudioFormat = StereoDownmix.format(format)

    private fun fileKey(path: Identifier): String? {
        if (path.namespace != PolyPlusConstants.ID) return null
        val name = path.path
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) return null
        return name.substring(FILE_PREFIX.length, name.length - FILE_SUFFIX.length)
    }

    //? if > 1.8.9 {
    private const val RETIRE_MS = 30_000L

    private class Retired(val buffer: CompletableFuture<SoundBuffer>, val at: Long)

    private val buffers = ConcurrentHashMap<String, CompletableFuture<SoundBuffer>>()
    private val retired = ConcurrentLinkedQueue<Retired>()

    init {
        eventHandler<TickEvent.End> { discardExpired() }
    }

    @JvmStatic
    fun buffer(path: Identifier): CompletableFuture<SoundBuffer>? {
        discardExpired()
        val key = fileKey(path) ?: return null
        var buffer: CompletableFuture<SoundBuffer>? = null
        files.computeIfPresent(key) { _, file ->
            buffer = if (!file.playable) {
                CompletableFuture.failedFuture(IOException("Cosmetic sound $key exceeds the sound limits"))
            } else {
                buffers.computeIfAbsent(key) {
                    val bytes = file.bytes
                    val samples = file.samples
                    CompletableFuture.supplyAsync { decode(bytes, samples) }
                }
            }
            file
        }
        return buffer
    }

    private fun decode(bytes: ByteArray, samples: Long): SoundBuffer =
        JOrbisAudioStream(ByteArrayInputStream(bytes)).use { stream ->
            val format = stream.format
            val rate = format.sampleRate.toInt()
            val channels = format.channels
            if (!CosmeticSoundLimits.fits(rate, channels)) throw IOException("Unsupported cosmetic sound format $format")
            val pcm = BufferUtils.createByteBuffer(CosmeticSoundLimits.pcmBytes(samples, rate, 1))
            var left = 0f
            var paired = false
            val sink = FloatConsumer { sample ->
                if (channels == 1) {
                    if (pcm.remaining() >= 2) pcm.putShort(pcm16(sample))
                } else if (!paired) {
                    left = sample
                    paired = true
                } else {
                    paired = false
                    if (pcm.remaining() >= 2) pcm.putShort(pcm16((left + sample) * 0.5f))
                }
            }
            while (pcm.remaining() >= 2) {
                if (!stream.readChunk(sink)) break
            }
            pcm.flip()
            SoundBuffer(pcm, if (channels == 1) format else StereoDownmix.format(format))
        }

    private fun pcm16(sample: Float): Short = (sample * 32767.5f - 0.5f).toInt().coerceIn(-32768, 32767).toShort()

    @JvmStatic
    fun clearBuffers() {
        for (key in buffers.keys) buffers.remove(key)?.thenAccept(SoundBuffer::discardAlBuffer)
        discardRetired(Long.MAX_VALUE)
    }

    private fun dropBuffer(key: String) {
        val buffer = buffers.remove(key) ?: return
        retired += Retired(buffer, System.currentTimeMillis())
    }

    private fun discardExpired() {
        if (retired.isNotEmpty()) discardRetired(System.currentTimeMillis() - RETIRE_MS)
    }

    private fun discardRetired(before: Long) {
        while (true) {
            val head = retired.peek() ?: return
            if (head.at > before) return
            if (retired.remove(head)) head.buffer.thenAccept(SoundBuffer::discardAlBuffer)
        }
    }
    //?} else {
    /*private const val EVENT_PREFIX = "$DIRECTORY/"
    private const val VANILLA_PREFIX = "${DIRECTORY}_vanilla/"
    private const val URL_SCHEME = "polyplus-cosmetic"

    internal fun vanillaEvent(name: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$VANILLA_PREFIX$name")

    @JvmStatic
    fun pool(id: Identifier): SoundPool? {
        if (id.namespace != PolyPlusConstants.ID) return null
        val name = id.path
        if (name.startsWith(VANILLA_PREFIX)) return vanillaPool(id, name.substring(VANILLA_PREFIX.length))
        if (!name.startsWith(EVENT_PREFIX)) return null
        val key = name.substring(EVENT_PREFIX.length)
        if (files[key]?.playable != true) return null
        val file = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$FILE_PREFIX$key$FILE_SUFFIX")
        return SoundPool(id, 1.0, 1.0, SoundCategory.PLAYERS).also { it.add(CosmeticSoundFile(file)) }
    }

    private fun vanillaPool(id: Identifier, name: String): SoundPool? {
        val source = Minecraft.getInstance().soundManager.get(Identifier.fromNamespaceAndPath("minecraft", name)) ?: return null
        return SoundPool(id, 1.0, 1.0, SoundCategory.PLAYERS).also { it.add(source) }
    }

    @JvmStatic
    fun url(path: Identifier): URL? {
        val key = fileKey(path) ?: return null
        val bytes = files[key]?.takeIf { it.playable }?.bytes ?: return null
        return URL.of(URI(URL_SCHEME, key, null), CosmeticSoundStream(bytes))
    }

    @JvmStatic
    fun resource(path: Identifier): Resource? {
        val key = fileKey(path) ?: return null
        val bytes = files[key]?.takeIf { it.playable }?.bytes ?: return null
        return SimpleResource(PolyPlusConstants.ID, path, ByteArrayInputStream(bytes), null, null)
    }

    private class MonoPcm(val source: ByteArray, val data: ByteArray, val format: AudioFormat)

    private class RetiredSound(val key: String, val at: Long, val serial: Long)

    private const val RETIRE_MS = 30_000L
    private val NO_PCM = ByteArray(0)
    private val STALE = ByteArray(0)

    private val monoPcm = ConcurrentHashMap<String, MonoPcm>()
    private val loadedMono = WeakHashMap<SoundSystem, HashMap<String, ByteArray>>()
    private val retiredSounds = ConcurrentLinkedQueue<RetiredSound>()
    private val releases = SoundReleases()

    init {
        eventHandler<TickEvent.End> { discardExpired() }
    }

    private val systemField: Field? by lazy {
        SoundEngine::class.java.declaredFields
            .firstOrNull { SoundSystem::class.java.isAssignableFrom(it.type) }
            ?.also { it.isAccessible = true }
    }

    private fun prepareMono(key: String, bytes: ByteArray) {
        if (monoPcm[key]?.source === bytes) return
        val decoded = try {
            decodeMono(key, bytes)
        } catch (ex: Exception) {
            LOGGER.warn("Could not decode cosmetic sound {}", key, ex)
            null
        }
        if (decoded == null) monoPcm.remove(key) else monoPcm[key] = decoded
    }

    private fun decodeMono(key: String, bytes: ByteArray): MonoPcm? {
        val codec = CodecJOrbis()
        val buffer = try {
            if (!codec.initialize(URL.of(URI(URL_SCHEME, key, null), CosmeticSoundStream(bytes)))) return null
            codec.readAll() ?: return null
        } finally {
            codec.cleanup()
        }
        val format = buffer.audioFormat ?: return null
        if (!StereoDownmix.applies(format)) return null
        val mono = StereoDownmix.pcm16(ByteBuffer.wrap(buffer.audioData), format.isBigEndian)
        return MonoPcm(bytes, ByteArray(mono.remaining()).also { mono.get(it) }, StereoDownmix.format(format))
    }

    @JvmStatic
    fun loadMono(engine: SoundEngine, event: Identifier) {
        if (event.namespace != PolyPlusConstants.ID || !event.path.startsWith(EVENT_PREFIX)) return
        val key = event.path.substring(EVENT_PREFIX.length)
        if (files[key]?.playable != true) return
        val system = systemField?.get(engine) as? SoundSystem ?: return
        val identifier = soundIdentifier(key)
        val pcm = monoPcm[key]
        synchronized(loadedMono) {
            loadedMono.keys.removeIf { it !== system }
            val loaded = loadedMono.getOrPut(system) { HashMap() }
            val held = loaded[identifier]
            if (pcm == null) {
                if (held != null && held !== NO_PCM) system.unloadSound(identifier)
                loaded[identifier] = NO_PCM
                return
            }
            if (held === pcm.data) return
            system.unloadSound(identifier)
            system.loadSound(pcm.data, pcm.format, identifier)
            loaded[identifier] = pcm.data
        }
    }

    private fun soundPath(key: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$FILE_PREFIX$key$FILE_SUFFIX")

    private fun soundIdentifier(key: String): String = soundPath(key).toString()

    private val bufferCaches = WeakHashMap<CosmeticSoundCache, Unit>()
    private val replacedKeys = ConcurrentLinkedQueue<String>()
    private val retiredBuffers = RetiredBuffers(RETIRE_MS)

    @JvmStatic
    fun trackCache(cache: CosmeticSoundCache) {
        synchronized(bufferCaches) { bufferCaches[cache] = Unit }
    }

    @JvmStatic
    fun cacheCleared(cache: CosmeticSoundCache) {
        retiredBuffers.flush(cache)
    }

    private fun detachBuffers(key: String, discard: (CosmeticSoundCache, () -> Boolean) -> Unit) {
        val path = soundPath(key)
        val caches = synchronized(bufferCaches) { bufferCaches.keys.toList() }
        for (cache in caches) cache.`polyplus$detach`(path)?.let { discard(cache, it::getAsBoolean) }
    }

    private fun dropBuffer(key: String) {
        monoPcm.remove(key)
        val identifier = soundIdentifier(key)
        synchronized(loadedMono) {
            for (loaded in loadedMono.values) loaded.replace(identifier, STALE)
        }
        retiredSounds += RetiredSound(key, System.currentTimeMillis(), releases.released(key))
    }

    private fun discardExpired() {
        val now = System.currentTimeMillis()
        while (true) {
            val key = replacedKeys.poll() ?: break
            detachBuffers(key) { cache, discard -> retiredBuffers.retire(cache, discard, now) }
        }
        discardReleased(now)
        retiredBuffers.expire(now)
    }

    private fun discardReleased(now: Long) {
        if (retiredSounds.isEmpty()) return
        val before = now - RETIRE_MS
        while (true) {
            val head = retiredSounds.peek() ?: return
            if (head.at > before) return
            if (!retiredSounds.remove(head) || !releases.settle(head.key, head.serial) || files.containsKey(head.key)) continue
            val identifier = soundIdentifier(head.key)
            synchronized(loadedMono) {
                for ((system, loaded) in loadedMono) {
                    if (loaded.remove(identifier) != null) system.unloadSound(identifier)
                }
            }
            detachBuffers(head.key) { cache, discard -> retiredBuffers.discardNow(cache, discard, now) }
        }
    }
    *///?}
}

//? if > 1.8.9 {
private class CosmeticSoundInstance(
    private val id: Identifier,
    volume: Float,
    pitch: Float,
    x: Double,
    y: Double,
    z: Double,
) : AbstractSoundInstance(id, SoundSource.PLAYERS, SoundInstance.createUnseededRandom()) {
    init {
        this.volume = volume
        this.pitch = pitch
        this.x = x
        this.y = y
        this.z = z
    }

    //? if >= 26.3 {
    override fun getOrResolve(manager: SoundManager): WeighedSoundEvents = resolved()
    //?} else {
    /*override fun resolve(manager: SoundManager): WeighedSoundEvents = resolved()
    *///?}

    private fun resolved(): WeighedSoundEvents {
        val file = Sound(id, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, false, false, ATTENUATION_BLOCKS)
        sound = file
        return WeighedSoundEvents(id, null).also { it.addSound(file) }
    }

    private companion object {
        const val ATTENUATION_BLOCKS = 16
    }
}
//?} else {
/*interface CosmeticSoundCache {
    fun `polyplus$detach`(path: Identifier): BooleanSupplier?
}

private class CosmeticSoundFile(private val file: Identifier) : SoundContainer<Sound> {
    override fun getWeight(): Int = 1

    override fun get(): Sound = Sound(file, 1.0, 1.0, false)
}

private class CosmeticSoundStream(private val bytes: ByteArray) : URLStreamHandler() {
    override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
        override fun connect() = Unit

        override fun getInputStream(): InputStream = CosmeticSoundBytes(bytes)
    }
}

private class CosmeticSoundBytes(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).coerceAtLeast(0)
}
*///?}

internal object CosmeticSoundPlayer {
    fun play(ref: CosmeticSoundRef, x: Double, y: Double, z: Double, volume: Float, pitch: Float, local: Boolean) {
        when (PolyPlusCosmeticsConfig.cosmeticSounds) {
            CosmeticSounds.OFF -> return
            CosmeticSounds.OWN -> if (!local) return
            CosmeticSounds.EVERYONE -> Unit
        }
        //? if > 1.8.9 {
        val instance = when {
            ref.vanilla != null -> {
                val id = Identifier.tryParse(ref.vanilla) ?: return
                SimpleSoundInstance(
                    SoundEvent.createVariableRangeEvent(id), SoundSource.PLAYERS, volume, pitch,
                    SoundInstance.createUnseededRandom(), x, y, z,
                )
            }
            ref.file != null -> {
                if (ref.key != null && !CosmeticSoundLibrary.playable(ref.key)) return
                CosmeticSoundInstance(ref.file, volume, pitch, x, y, z)
            }
            else -> return
        }
        Minecraft.getInstance().soundManager.play(instance)
        //?} else {
        /*val id = when {
            ref.vanilla != null -> {
                val key = if (':' in ref.vanilla) ref.vanilla else "minecraft:${ref.vanilla}"
                CosmeticSoundLibrary.vanillaEvent(LEGACY_SOUNDS[key] ?: return)
            }
            ref.file != null -> {
                if (ref.key != null && !CosmeticSoundLibrary.playable(ref.key)) return
                ref.file
            }
            else -> return
        }
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance(id, volume, pitch, x.toFloat(), y.toFloat(), z.toFloat()))
        *///?}
    }

    fun particle(id: String, x: Double, y: Double, z: Double) {
        //? if > 1.8.9 {
        val level = Minecraft.getInstance().level ?: return
        val key = Identifier.tryParse(id) ?: return
        val type = BuiltInRegistries.PARTICLE_TYPE.getOptional(key).orElse(null) as? SimpleParticleType ?: return
        level.addParticle(type, x, y, z, 0.0, 0.0, 0.0)
        //?} else {
        /*val world = Minecraft.getInstance().world ?: return
        val particle = LEGACY_PARTICLES[id] ?: return
        world.addParticle(particle.type, x, y, z, particle.red, particle.green, particle.blue)
        *///?}
    }

    //? if = 1.8.9 {
    /*private val LEGACY_SOUNDS = mapOf(
        "minecraft:block.note_block.harp" to "note.harp",
        "minecraft:block.note_block.pling" to "note.pling",
        "minecraft:block.note_block.bass" to "note.bass",
        "minecraft:block.note_block.basedrum" to "note.bd",
        "minecraft:block.note_block.snare" to "note.snare",
        "minecraft:block.note_block.hat" to "note.hat",
        "minecraft:block.note_block.bell" to "note.pling",
        "minecraft:block.note_block.chime" to "note.pling",
        "minecraft:block.note_block.xylophone" to "note.pling",
        "minecraft:block.note_block.iron_xylophone" to "note.pling",
        "minecraft:block.note_block.flute" to "note.harp",
        "minecraft:block.note_block.guitar" to "note.harp",
        "minecraft:block.note_block.banjo" to "note.harp",
        "minecraft:block.note_block.bit" to "note.harp",
        "minecraft:block.note_block.cow_bell" to "note.harp",
        "minecraft:block.note_block.didgeridoo" to "note.bassattack",
        "minecraft:block.amethyst_block.chime" to "note.pling",
        "minecraft:block.enchantment_table.use" to "random.orb",
        "minecraft:block.end_portal_frame.fill" to "mob.endermen.portal",
        "minecraft:block.respawn_anchor.charge" to "mob.blaze.breathe",
        "minecraft:entity.allay.ambient_without_item" to "mob.bat.idle",
        "minecraft:entity.evoker.cast_spell" to "mob.zombie.remedy",
        "minecraft:entity.illusioner.cast_spell" to "mob.zombie.unfect",
        "minecraft:block.beacon.activate" to "portal.trigger",
        "minecraft:block.bubble_column.bubble_pop" to "liquid.lavapop",
        "minecraft:block.fire.extinguish" to "random.fizz",
        "minecraft:block.fire.ambient" to "fire.fire",
        "minecraft:item.flintandsteel.use" to "fire.ignite",
        "minecraft:item.firecharge.use" to "item.fireCharge.use",
        "minecraft:block.lava.pop" to "liquid.lavapop",
        "minecraft:block.lava.ambient" to "liquid.lava",
        "minecraft:block.water.ambient" to "liquid.water",
        "minecraft:block.portal.ambient" to "portal.portal",
        "minecraft:block.portal.trigger" to "portal.trigger",
        "minecraft:block.portal.travel" to "portal.travel",
        "minecraft:block.piston.extend" to "tile.piston.out",
        "minecraft:block.piston.contract" to "tile.piston.in",
        "minecraft:block.glass.break" to "dig.glass",
        "minecraft:block.anvil.land" to "random.anvil_land",
        "minecraft:block.anvil.use" to "random.anvil_use",
        "minecraft:block.anvil.break" to "random.anvil_break",
        "minecraft:block.chest.open" to "random.chestopen",
        "minecraft:block.chest.close" to "random.chestclosed",
        "minecraft:block.wooden_door.open" to "random.door_open",
        "minecraft:block.wooden_door.close" to "random.door_close",
        "minecraft:ui.button.click" to "gui.button.press",
        "minecraft:ambient.cave" to "ambient.cave.cave",
        "minecraft:weather.rain" to "ambient.weather.rain",
        "minecraft:entity.lightning_bolt.thunder" to "ambient.weather.thunder",
        "minecraft:entity.experience_orb.pickup" to "random.orb",
        "minecraft:entity.player.levelup" to "random.levelup",
        "minecraft:entity.player.burp" to "random.burp",
        "minecraft:entity.player.hurt" to "game.player.hurt",
        "minecraft:entity.player.death" to "game.player.die",
        "minecraft:entity.player.splash" to "game.player.swim.splash",
        "minecraft:entity.player.attack.strong" to "random.successful_hit",
        "minecraft:entity.generic.explode" to "random.explode",
        "minecraft:entity.generic.drink" to "random.drink",
        "minecraft:entity.generic.eat" to "random.eat",
        "minecraft:entity.generic.splash" to "random.splash",
        "minecraft:entity.item.pickup" to "random.pop",
        "minecraft:entity.item.break" to "random.break",
        "minecraft:entity.arrow.shoot" to "random.bow",
        "minecraft:entity.arrow.hit" to "random.bowhit",
        "minecraft:entity.arrow.hit_player" to "random.successful_hit",
        "minecraft:entity.firework_rocket.blast" to "fireworks.blast",
        "minecraft:entity.firework_rocket.blast_far" to "fireworks.blast_far",
        "minecraft:entity.firework_rocket.large_blast" to "fireworks.largeBlast",
        "minecraft:entity.firework_rocket.large_blast_far" to "fireworks.largeBlast_far",
        "minecraft:entity.firework_rocket.launch" to "fireworks.launch",
        "minecraft:entity.firework_rocket.twinkle" to "fireworks.twinkle",
        "minecraft:entity.firework_rocket.twinkle_far" to "fireworks.twinkle_far",
        "minecraft:entity.tnt.primed" to "game.tnt.primed",
        "minecraft:entity.creeper.primed" to "creeper.primed",
        "minecraft:entity.splash_potion.break" to "game.potion.smash",
        "minecraft:entity.enderman.teleport" to "mob.endermen.portal",
        "minecraft:entity.enderman.scream" to "mob.endermen.scream",
        "minecraft:entity.ender_dragon.growl" to "mob.enderdragon.growl",
        "minecraft:entity.ender_dragon.flap" to "mob.enderdragon.wings",
        "minecraft:entity.wither.spawn" to "mob.wither.spawn",
        "minecraft:entity.wither.shoot" to "mob.wither.shoot",
        "minecraft:entity.ghast.shoot" to "mob.ghast.fireball",
        "minecraft:entity.ghast.warn" to "mob.ghast.charge",
        "minecraft:entity.blaze.shoot" to "mob.ghast.fireball",
        "minecraft:entity.elder_guardian.curse" to "mob.guardian.curse",
        "minecraft:entity.bat.takeoff" to "mob.bat.takeoff",
        "minecraft:entity.cat.ambient" to "mob.cat.meow",
        "minecraft:entity.cat.purr" to "mob.cat.purr",
        "minecraft:entity.cat.purreow" to "mob.cat.purreow",
        "minecraft:entity.cat.hiss" to "mob.cat.hiss",
        "minecraft:entity.wolf.ambient" to "mob.wolf.bark",
        "minecraft:entity.wolf.growl" to "mob.wolf.growl",
        "minecraft:entity.wolf.howl" to "mob.wolf.howl",
        "minecraft:entity.wolf.pant" to "mob.wolf.panting",
        "minecraft:entity.wolf.whine" to "mob.wolf.whine",
        "minecraft:entity.wolf.shake" to "mob.wolf.shake",
        "minecraft:entity.chicken.ambient" to "mob.chicken.say",
        "minecraft:entity.chicken.egg" to "mob.chicken.plop",
        "minecraft:entity.cow.ambient" to "mob.cow.say",
        "minecraft:entity.pig.ambient" to "mob.pig.say",
        "minecraft:entity.sheep.ambient" to "mob.sheep.say",
        "minecraft:entity.rabbit.jump" to "mob.rabbit.hop",
        "minecraft:entity.horse.ambient" to "mob.horse.idle",
        "minecraft:entity.horse.gallop" to "mob.horse.gallop",
        "minecraft:entity.villager.ambient" to "mob.villager.idle",
        "minecraft:entity.villager.yes" to "mob.villager.yes",
        "minecraft:entity.villager.no" to "mob.villager.no",
        "minecraft:entity.villager.trade" to "mob.villager.haggle",
        "minecraft:entity.zombie.ambient" to "mob.zombie.say",
        "minecraft:entity.skeleton.ambient" to "mob.skeleton.say",
        "minecraft:entity.spider.ambient" to "mob.spider.say",
        "minecraft:entity.slime.jump" to "mob.slime.big",
        "minecraft:entity.iron_golem.attack" to "mob.irongolem.throw",
    )

    private class LegacyParticle(val type: ParticleType, val red: Double = 0.0, val green: Double = 0.0, val blue: Double = 0.0)

    private val LEGACY_PARTICLES = mapOf(
        "minecraft:bubble_pop" to LegacyParticle(ParticleType.WATER_SPLASH),
        "minecraft:cherry_leaves" to LegacyParticle(ParticleType.REDSTONE, 1.0, 0.6, 0.75),
        "minecraft:cloud" to LegacyParticle(ParticleType.CLOUD),
        "minecraft:composter" to LegacyParticle(ParticleType.VILLAGER_HAPPY),
        "minecraft:crit" to LegacyParticle(ParticleType.CRIT),
        "minecraft:electric_spark" to LegacyParticle(ParticleType.FIREWORKS_SPARK),
        "minecraft:enchant" to LegacyParticle(ParticleType.ENCHANTMENT_TABLE),
        "minecraft:enchanted_hit" to LegacyParticle(ParticleType.CRIT_MAGIC),
        "minecraft:end_rod" to LegacyParticle(ParticleType.FIREWORKS_SPARK),
        "minecraft:firework" to LegacyParticle(ParticleType.FIREWORKS_SPARK),
        "minecraft:flame" to LegacyParticle(ParticleType.FLAME),
        "minecraft:glow" to LegacyParticle(ParticleType.REDSTONE, 0.4, 1.0, 0.85),
        "minecraft:happy_villager" to LegacyParticle(ParticleType.VILLAGER_HAPPY),
        "minecraft:heart" to LegacyParticle(ParticleType.HEART),
        "minecraft:note" to LegacyParticle(ParticleType.NOTE),
        "minecraft:poof" to LegacyParticle(ParticleType.EXPLOSION_NORMAL),
        "minecraft:portal" to LegacyParticle(ParticleType.PORTAL),
        "minecraft:reverse_portal" to LegacyParticle(ParticleType.PORTAL),
        "minecraft:scrape" to LegacyParticle(ParticleType.REDSTONE, 0.45, 0.85, 0.8),
        "minecraft:small_flame" to LegacyParticle(ParticleType.FLAME),
        "minecraft:snowflake" to LegacyParticle(ParticleType.SNOW_SHOVEL),
        "minecraft:soul" to LegacyParticle(ParticleType.SPELL_MOB, 0.35, 0.85, 0.95),
        "minecraft:soul_fire_flame" to LegacyParticle(ParticleType.FLAME),
        "minecraft:totem_of_undying" to LegacyParticle(ParticleType.SPELL_MOB, 0.85, 0.95, 0.3),
        "minecraft:wax_off" to LegacyParticle(ParticleType.REDSTONE, 1.0, 1.0, 1.0),
        "minecraft:wax_on" to LegacyParticle(ParticleType.REDSTONE, 1.0, 0.6, 0.1),
        "minecraft:white_ash" to LegacyParticle(ParticleType.TOWN_AURA),
        "minecraft:witch" to LegacyParticle(ParticleType.SPELL_WITCH),
    )
    *///?}
}
