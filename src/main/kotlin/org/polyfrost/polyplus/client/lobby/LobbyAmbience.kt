package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.exp

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.Channel
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.sounds.SoundSource
import org.polyfrost.polyplus.mixin.client.access.ChannelHandleAccessor
import org.polyfrost.polyplus.mixin.client.access.SoundEngineAccessor
import org.polyfrost.polyplus.mixin.client.access.SoundManagerAccessor
//?} else {
/*import net.minecraft.client.sound.SoundCategory
import net.minecraft.client.sound.instance.AbstractTickableSoundInstance
import net.minecraft.client.sound.instance.SoundInstance
*///?}

private const val SILENT_FLOOR = 1.0e-4f

//? if >= 1.21.6 {
private val AMBIENCE_SOURCE = SoundSource.UI
//?} elif > 1.8.9 {
/*private val AMBIENCE_SOURCE = SoundSource.MASTER
*///?}

//? if > 1.8.9 {
private class AmbienceSound(id: Identifier, private val loop: Boolean) :
    AbstractSoundInstance(id, AMBIENCE_SOURCE, SoundInstance.createUnseededRandom()), TickableSoundInstance {
    var gain = 0f
    var ended = false
    var fade: (() -> Float)? = null

    override fun getVolume(): Float = (gain * (fade?.invoke() ?: 1f)).coerceAtLeast(SILENT_FLOOR)

    override fun isLooping(): Boolean = loop

    override fun isRelative(): Boolean = true

    override fun getAttenuation(): SoundInstance.Attenuation = SoundInstance.Attenuation.NONE

    override fun canStartSilent(): Boolean = true

    override fun isStopped(): Boolean = ended

    override fun tick() = Unit
}
//?} else {
/*private class AmbienceSound(id: Identifier, private val loop: Boolean) : AbstractTickableSoundInstance(id) {
    var gain = 0f
    var ended = false
    var fade: (() -> Float)? = null

    override fun getVolume(): Float = (gain * (fade?.invoke() ?: 1f)).coerceAtLeast(SILENT_FLOOR)

    override fun isLooping(): Boolean = loop

    override fun getAttenuationType(): SoundInstance.Attenuation = SoundInstance.Attenuation.NONE

    override fun isStopped(): Boolean = ended

    override fun tick() = Unit
}
*///?}

object LobbyAmbience {
    private enum class Layer(val key: String) {
        DAY("day"),
        NIGHT("night"),
        GOLDEN("golden"),
        AURORA("aurora"),
        BUILDING("interior_building"),
        CAVE("interior_cave"),
        NETHER("nether"),
        END("the_end"),
    }

    private class Voice(val layer: Layer, val sound: AmbienceSound, val born: Int) {
        var weight = 0f
    }

    private const val NAMESPACE = "polyplus"
    private const val STARTUP = "startup"
    private const val TICK_SECONDS = 0.05f
    private const val EASE_SECONDS = 0.6f
    private const val FADE_IN_SECONDS = 0.6f
    private const val FADE_OUT_SECONDS = 0.5f
    private const val DEPART_FADE_SECONDS = 1.0f
    private const val SETTLE_TICKS = 40
    private const val RETRY_TICKS = 100
    private const val FAILED_TICKS = 200
    private const val HANDOFF_SECONDS = 3.2
    private const val INITIAL_FADE_NANOS = 400_000_000L
    private const val AMBIENCE_GAIN = 0.5f
    private val EASE = 1f - exp(-TICK_SECONDS / EASE_SECONDS)
    private val OUTDOOR = listOf(Layer.DAY, Layer.NIGHT, Layer.GOLDEN, Layer.AURORA)
    private val NETHER_BIOMES = setOf("nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas", "hell")
    private val END_BIOMES = setOf("the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens", "sky")
    private val VORBIS = byteArrayOf(1, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())

    private var voices = emptyList<Voice>()
    private var intro: AmbienceSound? = null
    private var introBorn = 0
    private var presence = 0f
    private var leaving = false
    private var departing = false
    private var clock = 0
    private var retry = 0
    private var introClaimed = false
    private var present = emptySet<Layer>()
    private val failedAt = HashMap<Layer, Int>()
    private var sceneKey: String? = null
    private var scenePlace: Layer? = null
    private var silenced = false
    //? if > 1.8.9 {
    private var introGate: LobbySoundGate.Group? = null
    private var introDelay = 0L
    private val layerGates = ArrayList<LobbySoundGate.Group>()
    //?} else {
    /*private var dueNanos = 0L
    *///?}

    @JvmStatic
    fun holdsMusic(): Boolean = playing() || silent(Minecraft.getInstance())

    private fun playing(): Boolean = voices.isNotEmpty() || intro != null

    private fun silent(mc: Minecraft): Boolean = PolyPlusLobbyConfig.lobbyMusic == LobbyMusic.OFF && lobbyUp(mc)

    private fun loops(): Boolean = when (PolyPlusLobbyConfig.lobbyMusic) {
        LobbyMusic.MINECRAFT, LobbyMusic.OFF -> false
        else -> true
    }

    private fun fixed(): Layer? = when (PolyPlusLobbyConfig.lobbyMusic) {
        LobbyMusic.DAY -> Layer.DAY
        LobbyMusic.NIGHT -> Layer.NIGHT
        LobbyMusic.SUNSET -> Layer.GOLDEN
        LobbyMusic.AURORA -> Layer.AURORA
        LobbyMusic.HOME -> Layer.BUILDING
        LobbyMusic.CAVE -> Layer.CAVE
        LobbyMusic.NETHER -> Layer.NETHER
        LobbyMusic.THE_END -> Layer.END
        else -> null
    }

    fun startup() {
        if (introClaimed) return
        introClaimed = true
        val mc = Minecraft.getInstance()
        if (!audible(mc) || !lobbyUp(mc)) return
        val seconds = read(mc, file(STARTUP))?.let(::oggSeconds) ?: return
        halt(mc)
        mc.musicManager.stopPlaying()
        val sound = AmbienceSound(event(STARTUP), false)
        sound.gain = volume()
        presence = 1f
        mc.soundManager.play(sound)
        //? if > 1.8.9
        if (!active(mc, sound)) return
        intro = sound
        introBorn = clock
        val delay = (minOf(seconds, HANDOFF_SECONDS) * 1_000_000_000.0).toLong()
        //? if > 1.8.9 {
        introGate = channelOf(mc, sound)?.let { LobbySoundGate.hold(listOf(it)) }
        introDelay = delay
        begin(mc, true)
        //?} else {
        /*dueNanos = System.nanoTime() + delay
        *///?}
    }

    fun depart() {
        if (playing()) departing = true
    }

    fun arrive() {
        departing = false
    }

    fun onFrame() {
        //? if = 1.8.9 {
        /*if (dueNanos == 0L || System.nanoTime() < dueNanos) return
        dueNanos = 0L
        begin(Minecraft.getInstance(), true)
        *///?}
    }

    fun tick() {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9
        LobbySoundGate.expire()
        clock++
        val quiet = silent(mc)
        if (quiet && !silenced) {
            mc.musicManager.stopPlaying()
            UiSounds.releaseAmbience()
        }
        silenced = quiet
        if (departing && !playing() && !lobbyUp(mc)) departing = false
        val wanted = lobbyUp(mc) && audible(mc) && !departing && !(StartupOverlay.active && !introClaimed)
        if (!playing()) {
            if (!wanted || !loops() || LobbyScene.phase != LobbyScene.Phase.READY || --retry > 0) return
            if (!begin(mc, false)) retry = RETRY_TICKS
            return
        }
        leaving = !wanted || (!loops() && intro == null)
        intro?.let { if (clock - introBorn > SETTLE_TICKS && !active(mc, it)) intro = null }
        voices.filter { clock - it.born > SETTLE_TICKS && !active(mc, it.sound) }.forEach { drop(mc, it, true) }
        val layers = desired()
        if (!leaving) join(mc, layers, null)
        val targets = targets(layers)
        for (voice in voices) voice.weight += (targets[voice.layer.ordinal] - voice.weight) * EASE
        voices.filter { it.layer !in layers && it.weight < SILENT_FLOOR }.forEach { drop(mc, it, false) }
        presence = if (leaving) presence - TICK_SECONDS / (if (departing) DEPART_FADE_SECONDS else FADE_OUT_SECONDS) else minOf(1f, presence + TICK_SECONDS / FADE_IN_SECONDS)
        if (presence <= 0f || !playing()) {
            halt(mc)
            return
        }
        apply()
    }

    private fun begin(mc: Minecraft, afterIntro: Boolean): Boolean {
        present = Layer.entries.filterTo(HashSet()) { exists(mc, file(it.key)) }
        mc.musicManager.stopPlaying()
        if (!afterIntro) presence = 0f
        leaving = false
        val layers = desired()
        join(mc, layers, targets(layers))
        if (voices.isEmpty()) return false
        UiSounds.releaseAmbience()
        if (afterIntro) {
            //? if > 1.8.9 {
            val gate = layerGates.lastOrNull()
            val start = { gate?.startedAt ?: 0L }
            //?} else {
            /*val began = System.nanoTime()
            val start = { began }
            *///?}
            val fade = {
                val from = start()
                if (from == 0L) 0f else ((System.nanoTime() - from).toFloat() / INITIAL_FADE_NANOS).coerceIn(0f, 1f)
            }
            for (voice in voices) voice.sound.fade = fade
        }
        apply()
        return true
    }

    private fun join(mc: Minecraft, layers: List<Layer>, initial: FloatArray?) {
        val have = voices.mapTo(HashSet()) { it.layer }
        val added = ArrayList<Voice>()
        for (layer in layers) {
            if (layer in have) continue
            val voice = Voice(layer, AmbienceSound(event(layer.key), true), clock)
            voice.weight = initial?.get(layer.ordinal) ?: 0f
            voice.sound.gain = presence * volume() * voice.weight
            mc.soundManager.play(voice.sound)
            //? if > 1.8.9 {
            if (!active(mc, voice.sound)) {
                voice.sound.ended = true
                failedAt[layer] = clock
                continue
            }
            //?}
            added += voice
        }
        if (added.isEmpty()) return
        voices = voices + added
        //? if > 1.8.9
        layerGates += LobbySoundGate.hold(added.mapNotNull { channelOf(mc, it.sound) }, introGate, introDelay)
    }

    private fun drop(mc: Minecraft, voice: Voice, failed: Boolean) {
        voice.sound.ended = true
        mc.soundManager.stop(voice.sound)
        voices = voices - voice
        if (failed) failedAt[voice.layer] = clock
    }

    private fun halt(mc: Minecraft) {
        //? if > 1.8.9 {
        LobbySoundGate.cancel(introGate)
        layerGates.forEach(LobbySoundGate::cancel)
        layerGates.clear()
        introGate = null
        introDelay = 0L
        //?} else {
        /*dueNanos = 0L
        *///?}
        for (sound in voices.map { it.sound } + listOfNotNull(intro)) {
            sound.ended = true
            mc.soundManager.stop(sound)
        }
        voices = emptyList()
        intro = null
        presence = 0f
        leaving = false
    }

    private fun apply() {
        val level = presence * volume()
        for (voice in voices) voice.sound.gain = level * voice.weight
        intro?.gain = level
    }

    private fun usable(layer: Layer): Boolean =
        layer in present && failedAt[layer].let { it == null || clock - it > FAILED_TICKS }

    private fun desired(): List<Layer> {
        if (!loops()) return emptyList()
        fixed()?.takeIf(::usable)?.let { return listOf(it) }
        val mood = LobbySky.mood
        val place = place(mood)?.takeIf(::usable)
        if (place != null) return listOf(place)
        val clear = 1f - mood.golden
        val strength = mapOf(
            Layer.DAY to clear * (1f - mood.night),
            Layer.NIGHT to if (mood.aurora > 0f) 0f else clear * mood.night,
            Layer.AURORA to if (mood.aurora > 0f) clear * mood.night else 0f,
            Layer.GOLDEN to mood.golden,
        )
        return listOfNotNull(OUTDOOR.filter(::usable).maxByOrNull { strength.getValue(it) })
    }

    private fun targets(layers: List<Layer>): FloatArray {
        val weights = FloatArray(Layer.entries.size)
        for (layer in layers) weights[layer.ordinal] = 1f
        return weights
    }

    private fun place(mood: LobbyMood): Layer? {
        val id = LobbyScene.currentId
        if (id != sceneKey) {
            sceneKey = id
            scenePlace = id?.let { runCatching { LobbyScene.findScene(it) }.getOrNull() }?.let { entry ->
                val biome = entry.biome.substringAfter(':').lowercase()
                when {
                    entry.realm == LobbyRealm.NETHER -> Layer.NETHER
                    entry.realm == LobbyRealm.END -> Layer.END
                    biome in NETHER_BIOMES -> Layer.NETHER
                    biome in END_BIOMES -> Layer.END
                    entry.setting == LobbySceneScore.Profile.CAVE -> Layer.CAVE
                    entry.setting == LobbySceneScore.Profile.INDOOR -> Layer.BUILDING
                    else -> null
                }
            }
        }
        return scenePlace ?: Layer.BUILDING.takeIf { mood.interior }
    }

    private fun lobbyUp(mc: Minecraft): Boolean {
        if (mc.level != null || LobbyScene.phase == LobbyScene.Phase.FAILED) return false
        return ClientPlatform.currentScreen() is PolyPlusLobbyScreen || LobbyScene.showing()
    }

    private fun volume(): Float = AMBIENCE_GAIN * OneConfigConfig.uiAmbienceVolume.coerceIn(0f, 1f)

    private fun audible(mc: Minecraft): Boolean =
        OneConfigConfig.enableUIAmbience && volume() > 0f &&
            //? if > 1.8.9 {
            mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0f
            //?} else {
            /*mc.options.getSoundCategoryVolume(SoundCategory.MASTER) > 0f
            *///?}

    private fun active(mc: Minecraft, sound: AmbienceSound): Boolean =
        //? if > 1.8.9 {
        mc.soundManager.isActive(sound)
        //?} else {
        /*mc.soundManager.isPlaying(sound)
        *///?}

    private fun event(name: String): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, "lobby/$name")

    private fun file(name: String): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, "sounds/lobby/$name.ogg")

    private fun exists(mc: Minecraft, id: Identifier): Boolean = runCatching {
        //? if > 1.8.9 {
        mc.resourceManager.getResource(id).isPresent
        //?} else {
        /*mc.resourceManager.getResource(id).asStream().close()
        true
        *///?}
    }.getOrDefault(false)

    private fun read(mc: Minecraft, id: Identifier): ByteArray? = runCatching {
        //? if > 1.8.9 {
        mc.resourceManager.getResource(id).orElse(null)?.open()?.use { it.readBytes() }
        //?} else {
        /*mc.resourceManager.getResource(id).asStream().use { it.readBytes() }
        *///?}
    }.getOrNull()

    //? if > 1.8.9 {
    private fun channelOf(mc: Minecraft, sound: SoundInstance): Channel? {
        val engine = (mc.soundManager as SoundManagerAccessor).`polyplus$engine`() as SoundEngineAccessor
        val handle = engine.`polyplus$channels`()[sound] ?: return null
        return (handle as ChannelHandleAccessor).`polyplus$channel`()
    }
    //?}

    private fun oggSeconds(bytes: ByteArray): Double? {
        val header = indexOf(bytes, VORBIS)
        if (header < 0 || header + 16 > bytes.size) return null
        val rate = littleEndian(bytes, header + 12, 4)
        var page = bytes.size - 27
        while (page >= 0 && !(bytes[page] == 'O'.code.toByte() && bytes[page + 1] == 'g'.code.toByte() && bytes[page + 2] == 'g'.code.toByte() && bytes[page + 3] == 'S'.code.toByte() && bytes[page + 4] == 0.toByte())) page--
        if (page < 0 || rate <= 0L) return null
        val samples = littleEndian(bytes, page + 6, 8)
        return if (samples > 0L) samples.toDouble() / rate else null
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray): Int {
        outer@ for (start in 0..bytes.size - pattern.size) {
            for (i in pattern.indices) if (bytes[start + i] != pattern[i]) continue@outer
            return start
        }
        return -1
    }

    private fun littleEndian(bytes: ByteArray, offset: Int, count: Int): Long {
        var value = 0L
        for (i in count - 1 downTo 0) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        return value
    }
}
