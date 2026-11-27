package org.polyfrost.polyplus.client.emotes.playback

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.CosmeticMotion
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.HostEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.emotes.EmoteRole
import org.polyfrost.polyplus.client.emotes.props.EmotePropSkin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.ref.Cleaner

internal class EmoteScene(asset: CosmeticAsset, meta: JsonObject?, private val label: String, name: String) {
    private val renderer = Renderer()
    private val cleanable = CLEANER.register(this, renderer::close)
    private val sections = IntArray(Renderer.SECTION_INTS)
    private val partIds: IntArray
    private val signalsAt: Int
    private val eventsAt: Int
    private val detailAt: Int
    private val playAt: Int
    private val roleAt: Int
    private val rootAt: Int
    private val holdAt: Int
    private val deltasAt: Int
    private val statusAt: Int
    private val runsAt: Int
    private val input: FloatArray
    private val out: FloatArray
    internal val props: EmotePropSkin?
    private val effects: HostEffects
    private val wearers = LongArray(1)
    private val sceneClock = EmoteSceneClock()
    private var failed = false
    private var effectsOn = true
    private var motion: CosmeticMotion? = null

    init {
        try {
            renderer.loadPlayer(SLOT, false)
            renderer.loadEmote(SLOT, PROPS_SLOT, asset)
            partIds = IntArray(PARTS.size) { renderer.partId(PARTS[it]) }
            sections[Renderer.SECTION_PARTS] = (partIds.maxOrNull() ?: -1) + 1
            sections[Renderer.SECTION_ROOT] = 1
            sections[Renderer.SECTION_PLAY] = 1
            sections[Renderer.SECTION_ROLE] = 1
            sections[Renderer.SECTION_PART_DELTAS] = 1
            sections[Renderer.SECTION_HOLD] = 1
            val layout = IntArray(Renderer.INPUT_LAYOUT_INTS)
            Renderer.inputLayout(sections, layout)
            signalsAt = layout[Renderer.INPUT_SIGNALS_AT]
            eventsAt = layout[Renderer.INPUT_EVENTS_AT]
            detailAt = layout[Renderer.INPUT_DETAIL_AT]
            playAt = layout[Renderer.INPUT_PLAY_AT]
            roleAt = layout[Renderer.INPUT_ROLE_AT]
            rootAt = layout[Renderer.INPUT_ROOT_AT]
            holdAt = layout[Renderer.INPUT_HOLD_AT]
            input = FloatArray(layout[Renderer.INPUT_STRIDE])
            val output = IntArray(Renderer.LAYOUT_INTS)
            renderer.outputLayout(sections, output)
            deltasAt = output[Renderer.LAYOUT_PART_DELTAS_AT]
            statusAt = deltasAt + output[Renderer.LAYOUT_PART_DELTAS] * Renderer.PART_DELTA_FLOATS
            runsAt = output[Renderer.LAYOUT_RUNS_AT]
            out = FloatArray(output[Renderer.LAYOUT_STRIDE])
            props = propSkin(output[Renderer.LAYOUT_BONES], output[Renderer.LAYOUT_RUNS])
            effects = HostEffects(renderer, asset, meta, name)
        } catch (ex: RuntimeException) {
            cleanable.clean()
            throw ex
        }
    }

    val hasEffects: Boolean get() = effects.hasEffects && effectsOn

    val effectTextures: List<Identifier> get() = effects.effectTextures

    fun part(
        index: Int,
        restX: Float, restY: Float, restZ: Float,
        x: Float, y: Float, z: Float,
        xRot: Float, yRot: Float, zRot: Float,
        xScale: Float, yScale: Float, zScale: Float,
    ) {
        val i = partIds[index] * Renderer.PART_FLOATS
        input[i] = restX
        input[i + 1] = restY
        input[i + 2] = restZ
        input[i + 3] = x
        input[i + 4] = y
        input[i + 5] = z
        input[i + 6] = xRot
        input[i + 7] = yRot
        input[i + 8] = zRot
        input[i + 9] = xScale
        input[i + 10] = yScale
        input[i + 11] = zScale
    }

    fun standing() {
        for (index in PARTS.indices) {
            val x = STANDING_X[index]
            val y = STANDING_Y[index]
            part(index, x, y, 0f, x, y, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        }
    }

    fun clock(wearer: Long, wanted: Double, shift: Double, real: Double): Double =
        sceneClock.at(wearer, wanted, shift, real)

    fun frame(
        wearer: Long,
        clock: Double,
        signals: PlayerSignalRecord,
        events: Int,
        clip: Int,
        age: Float,
        role: EmoteRole?,
        held: Boolean,
        root: FloatArray,
        effectMode: Int,
        local: Boolean,
        detail: Int,
    ): Int {
        if (failed) return FAILED
        signals.copyInto(input, signalsAt)
        input[eventsAt] = events.toFloat()
        input[detailAt] = detail.toFloat()
        input[playAt + Renderer.PLAY_CLIP] = clip.toFloat()
        input[playAt + Renderer.PLAY_AGE] = age
        input[roleAt + Renderer.ROLE_INDEX] = role?.index?.toFloat() ?: NO_ROLE
        input[roleAt + Renderer.ROLE_DX] = role?.dx ?: 0f
        input[roleAt + Renderer.ROLE_DY] = role?.dy ?: 0f
        input[roleAt + Renderer.ROLE_DZ] = role?.dz ?: 0f
        input[roleAt + Renderer.ROLE_YAW] = role?.yaw ?: 0f
        input[holdAt] = if (held) 1f else 0f
        System.arraycopy(root, 0, input, rootAt, Renderer.ROOT_FLOATS)
        wearers[0] = wearer
        val began = System.nanoTime()
        val status = try {
            syncMotion()
            renderer.frameWearersShaped(clock, wearers, 1, sections, input, Renderer.JAVA_MODEL_SPACE, out)
            out[statusAt].toInt()
        } catch (ex: RuntimeException) {
            failed = true
            LOGGER.error("Stopped playing emote {} after plusrenderer failed to pose it", label, ex)
            CosmeticWearers.charge(System.nanoTime() - began)
            return FAILED
        }
        if (hasEffects && effectMode != EFFECTS_OFF) {
            guardEffects { effects.afterFrame(root, local, effectMode == EFFECTS_DRAWN) }
        }
        CosmeticWearers.charge(System.nanoTime() - began)
        return status
    }

    private fun syncMotion() {
        val wanted = PolyPlusCosmeticsConfig.cosmeticMotion
        if (wanted == motion) return
        renderer.setMotion(
            when (wanted) {
                CosmeticMotion.FULL -> Renderer.MOTION_FULL
                CosmeticMotion.REDUCED -> Renderer.MOTION_REDUCED
                CosmeticMotion.OFF -> Renderer.MOTION_OFF
            },
        )
        motion = wanted
    }

    private inline fun guardEffects(block: () -> Unit) {
        try {
            block()
        } catch (ex: RuntimeException) {
            effectsOn = false
            LOGGER.error("Stopped the effects of emote {} after plusrenderer failed to run them", label, ex)
        }
    }

    fun close() {
        effectsOn = false
        effects.close()
    }

    fun weight(index: Int): Float = out[deltasAt + partIds[index] * Renderer.PART_DELTA_FLOATS + WEIGHT]

    fun delta(index: Int, field: Int): Float = out[deltasAt + partIds[index] * Renderer.PART_DELTA_FLOATS + field]

    fun copyProps(into: FloatArray) {
        val skin = props ?: return
        val bones = skin.boneCount * BONE_FLOATS
        System.arraycopy(out, 0, into, 0, bones)
        System.arraycopy(out, runsAt, into, bones, skin.runCount)
    }

    private fun propSkin(boneCount: Int, runCount: Int): EmotePropSkin? {
        if (renderer.buildSkin() == 0) return null
        val runs = renderer.skinRuns()
        val picked = ArrayList<Int>()
        for (run in 0 until runs.size / RUN_INTS) {
            if (runs[run * RUN_INTS + RUN_TEXTURE] != PROPS_SLOT) continue
            val start = runs[run * RUN_INTS + RUN_START] * QUAD_VERTICES
            picked += run
            picked += start
            picked += start + runs[run * RUN_INTS + RUN_COUNT] * QUAD_VERTICES
        }
        if (picked.isEmpty() || renderer.skinBoneCount() != boneCount) return null
        return EmotePropSkin(
            renderer.skinPositions(),
            renderer.skinNormals(),
            renderer.skinUvs(),
            renderer.skinGlow(),
            renderer.skinBones(),
            picked.toIntArray(),
            runCount,
            boneCount,
        )
    }

    fun forget(wearer: Long) {
        sceneClock.forget(wearer)
        try {
            renderer.forgetWearer(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped forgetting a wearer of a freed emote scene", closed)
        }
    }

    fun breakRings(wearer: Long) {
        try {
            renderer.breakRings(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped breaking the rings of a freed emote scene", closed)
        }
    }

    companion object {
        const val HEAD = 0
        const val HAT = 1
        const val BODY = 2
        const val RIGHT_ARM = 3
        const val LEFT_ARM = 4
        const val RIGHT_LEG = 5
        const val LEFT_LEG = 6
        const val ROOT = 7

        const val OFFSET_X = 0
        const val OFFSET_Y = 1
        const val OFFSET_Z = 2
        const val ROT_X = 3
        const val ROT_Y = 4
        const val ROT_Z = 5
        const val SCALE_X = 6
        const val SCALE_Y = 7
        const val SCALE_Z = 8
        const val WEIGHT = 9

        const val FAILED = -1
        const val POSING = 1
        const val ENDED = 1 shl 1
        const val CANCEL = 1 shl 2
        const val HELD = Renderer.EMOTE_HELD

        const val EFFECTS_OFF = 0
        const val EFFECTS_EVENTS = 1
        const val EFFECTS_DRAWN = 2

        private const val SLOT = 0
        private const val PROPS_SLOT = 1
        private const val BONE_FLOATS = 12
        private const val RUN_INTS = 4
        private const val RUN_TEXTURE = 0
        private const val RUN_START = 1
        private const val RUN_COUNT = 2
        private const val QUAD_VERTICES = 4
        private const val NO_ROLE = -1f
        private val PARTS = arrayOf("head", "hat", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "root")
        private val STANDING_X = floatArrayOf(0f, 0f, 0f, -5f, 5f, -1.9f, 1.9f, 0f)
        private val STANDING_Y = floatArrayOf(0f, 0f, 0f, 2f, 2f, 12f, 12f, 0f)
        private val CLEANER: Cleaner = Cleaner.create()
        private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/emotes")
    }
}
