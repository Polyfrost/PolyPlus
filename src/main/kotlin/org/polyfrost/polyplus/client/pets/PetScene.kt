package org.polyfrost.polyplus.client.pets

import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.CosmeticMotion
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.EffectSource
import org.polyfrost.polyplus.client.cosmetics.render.HostEffects
import org.polyfrost.polyplus.client.cosmetics.render.PreviewEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.VertexConsumer
import org.polyfrost.polyplus.client.utils.CosmeticClock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal class PetScene(private val asset: PetAsset) : AutoCloseable {
    private val renderer = Renderer()
    private val variableIds: IntArray
    private val sections: IntArray
    private val signalsAt: Int
    private val eventsAt: Int
    private val detailAt: Int
    private val rootAt: Int
    private val variablesAt: Int
    private val playAt: Int
    private val roleAt: Int
    private val stride: Int
    private val boneCount: Int
    private val runsAt: Int
    private val colorwayAt: Int
    private val outStride: Int
    private val positions: FloatArray
    private val normals: FloatArray
    private val uvs: FloatArray
    private val glow: ByteArray
    private val bones: IntArray
    private val runs: IntArray
    private val effects: HostEffects

    val isEmpty: Boolean

    private var failed = false
    private var effectsFailed = false
    private var localRow = -1
    private var shiftX = 0f
    private var shiftY = 0f
    private var shiftZ = 0f
    private var motion: CosmeticMotion? = null
    private var frame = -1
    private var rows = 0
    private var computedFrame = -1
    private var computedClock = 0.0
    private var wearers = LongArray(INITIAL_ROWS)
    private var input: FloatArray
    private var out: FloatArray
    private var placedRoots = FloatArray(ROOT_FLOATS * INITIAL_ROWS)
    private val previews = HashMap<Long, Preview>()
    private val combatSent = PetLongMap<Boolean>()
    private val paints = PetLongMap<EffectPaint>()

    private class EffectPaint(val colorway: Int, val colors: Map<String, Int>?)

    init {
        try {
            renderer.setRig(Renderer.RIG_ACTOR)
            renderer.loadAsset(SLOT, asset.cosmetic)
            renderer.play(SLOT, CosmeticClock.seconds())
            effects = HostEffects(renderer, asset.cosmetic, asset.meta, PetAssets.effectPrefix(asset.id, asset.revision, PetAssets.nextScene()))
            variableIds = IntArray(PetVariables.NAMES.size) { renderer.hostVariableId(PetVariables.NAMES[it]) }
            sections = IntArray(Renderer.SECTION_INTS)
            sections[Renderer.SECTION_ROOT] = 1
            sections[Renderer.SECTION_VARIABLES] = (variableIds.maxOrNull() ?: -1) + 1
            sections[Renderer.SECTION_COLORWAY] = 1
            val shared = asset.sharedRoles.isNotEmpty()
            if (shared) {
                sections[Renderer.SECTION_PLAY] = 1
                sections[Renderer.SECTION_ROLE] = 1
            }
            val layout = IntArray(Renderer.INPUT_LAYOUT_INTS)
            Renderer.inputLayout(sections, layout)
            signalsAt = layout[Renderer.INPUT_SIGNALS_AT]
            eventsAt = layout[Renderer.INPUT_EVENTS_AT]
            detailAt = layout[Renderer.INPUT_DETAIL_AT]
            rootAt = layout[Renderer.INPUT_ROOT_AT]
            variablesAt = layout[Renderer.INPUT_VARIABLES_AT]
            playAt = if (shared) layout[Renderer.INPUT_PLAY_AT] else -1
            roleAt = if (shared) layout[Renderer.INPUT_ROLE_AT] else -1
            stride = layout[Renderer.INPUT_STRIDE]
            isEmpty = renderer.buildSkin() == 0
            positions = renderer.skinPositions()
            normals = renderer.skinNormals()
            uvs = renderer.skinUvs()
            glow = renderer.skinGlow()
            bones = renderer.skinBones()
            runs = renderer.skinRuns()
            val output = IntArray(Renderer.LAYOUT_INTS)
            renderer.outputLayout(sections, output)
            boneCount = output[Renderer.LAYOUT_BONES]
            runsAt = output[Renderer.LAYOUT_RUNS_AT]
            colorwayAt = output[Renderer.LAYOUT_COLORWAY_AT]
            outStride = output[Renderer.LAYOUT_STRIDE]
            input = FloatArray(stride * INITIAL_ROWS)
            out = FloatArray(outStride * INITIAL_ROWS)
        } catch (ex: RuntimeException) {
            renderer.close()
            throw ex
        }
    }

    fun begin(frame: Int, shiftX: Float, shiftY: Float, shiftZ: Float) {
        if (this.frame == frame) return
        this.frame = frame
        rows = 0
        localRow = -1
        this.shiftX = shiftX
        this.shiftY = shiftY
        this.shiftZ = shiftZ
    }

    val effectSource get() = effects.effectSource

    val hasEffects: Boolean get() = effects.hasEffects && !effectsFailed && !failed

    fun add(
        wearer: Long,
        root: FloatArray,
        variables: FloatArray,
        signals: PlayerSignalRecord,
        events: Int,
        celebrating: Boolean,
        combat: Boolean,
        detail: Int,
        local: Boolean,
        role: PetSharedRole? = null,
        roleAge: Float = 0f,
        anchor: FloatArray? = null,
    ): Int {
        if (failed || isEmpty) return -1
        ensureRows(rows + 1)
        val base = rows * stride
        fillRow(input, base, root, variables, signals, events, detail)
        val placed = rows * ROOT_FLOATS
        System.arraycopy(root, 0, placedRoots, placed, ROOT_FLOATS)
        if (role != null && playAt >= 0 && roleAt >= 0) {
            input[base + playAt] = 0f
            input[base + playAt + 1] = roleAge
            input[base + roleAt] = role.index.toFloat()
            if (anchor != null) {
                System.arraycopy(anchor, 0, input, base + roleAt + 1, ANCHOR_FLOATS)
                PetPose.placeRole(placedRoots, placed, role, roleAge, anchor)
            }
        }
        if (celebrating) input[base + signalsAt + PlayerSignals.IS_EMOTING] = 1f
        if (combat) input[base + signalsAt + PlayerSignals.IS_IN_COMBAT] = 1f
        syncCombat(wearer, combat)
        wearers[rows] = wearer
        if (local) localRow = rows
        return rows++
    }

    fun compute(clock: Double) {
        if (failed || rows == 0 || computedFrame == frame) return
        val began = System.nanoTime()
        try {
            frameRows(clock)
        } finally {
            CosmeticWearers.charge(System.nanoTime() - began)
        }
    }

    private fun frameRows(clock: Double) {
        try {
            syncMotion()
            renderer.frameWearersShaped(clock, wearers, rows, sections, input, Renderer.CORE_SPACE, out)
            computedFrame = frame
            computedClock = clock
        } catch (ex: RuntimeException) {
            fail(ex)
            return
        }
        if (effectsFailed) return
        try {
            effects.afterRows(placedRoots, ROOT_FLOATS, 0, localRow, queue = QUEUE_EFFECTS)
        } catch (ex: RuntimeException) {
            effectsFailed = true
            LOGGER.error("Stopped the effects of pet cosmetic {} after plusrenderer failed to run them", asset.id, ex)
        }
    }

    fun ready(row: Int): Boolean = !failed && row in 0 until rows && computedFrame == frame

    fun colorway(row: Int): Int {
        if (!ready(row)) return -1
        return out[row * outStride + colorwayAt].toInt()
    }

    fun emit(row: Int, pose: Pose, buffer: VertexConsumer, light: Int, overlay: Int) {
        if (!ready(row)) return
        SHIFT.translation(shiftX, shiftY, shiftZ)
        draw(out, row * outStride, computedClock, pose, buffer, light, overlay, SHIFT)
    }

    fun emitPlaced(row: Int, placement: Matrix4f, pose: Pose, buffer: VertexConsumer, light: Int, overlay: Int) {
        if (!ready(row)) return
        draw(out, row * outStride, computedClock, pose, buffer, light, overlay, placement)
    }

    fun rootOf(row: Int, target: FloatArray): Boolean {
        if (row !in 0 until rows) return false
        System.arraycopy(input, row * stride + rootAt, target, 0, ROOT_FLOATS)
        return true
    }

    inner class Preview(val wearer: Long) : EffectSource {
        internal val ids = longArrayOf(wearer)
        internal val input = FloatArray(stride)
        internal val out = FloatArray(outStride)
        internal val view = FloatArray(Renderer.VIEW_FLOATS)
        val model = Matrix4f()
        internal var clock = 0.0
        internal var ready = false
        internal var fired = 0
        internal var frame = -1
        internal var count = 0
        internal var data = FloatArray(0)

        override val effectTextures: List<Identifier> get() = effects.effectTextures
        override val quads: FloatArray get() = data
        override val quadCount: Int get() = count

        override fun computeFor(frame: Int): Boolean = !failed && !effectsFailed && this.frame == frame

        fun colorway(): Int {
            if (failed || !ready) return -1
            return out[colorwayAt].toInt()
        }

        fun emit(pose: Pose, buffer: VertexConsumer, light: Int, overlay: Int) {
            if (failed || !ready) return
            draw(out, 0, clock, pose, buffer, light, overlay, UNPLACED)
        }
    }

    fun framePreview(
        root: FloatArray,
        archetype: PetArchetype,
        signals: PlayerSignalRecord,
        events: Int,
        combat: Boolean,
        colors: Map<String, Int>?,
        eye: Matrix4f?,
    ): Preview? {
        if (failed || isEmpty) return null
        val wearer = previewWearer()
        val preview = previews.getOrPut(wearer) { Preview(wearer) }
        PetPreviewVariables.fill(PREVIEW_VARIABLES, archetype, signals)
        fillRow(preview.input, 0, root, PREVIEW_VARIABLES, signals, events, Renderer.DETAIL_FULL)
        preview.input[signalsAt + PlayerSignals.IS_IN_COMBAT] = if (combat) 1f else 0f
        val clock = CosmeticClock.seconds()
        try {
            syncMotion()
            syncCombat(wearer, combat)
            paintEffects(wearer, asset.colorway, colors)
            firePreview(preview)
            renderer.frameWearersShaped(clock, preview.ids, 1, sections, preview.input, Renderer.CORE_SPACE, preview.out)
            preview.clock = clock
            preview.ready = true
        } catch (ex: RuntimeException) {
            fail(ex)
            return null
        }
        if (eye != null) previewGeometry(preview, eye)
        return preview
    }

    private fun firePreview(preview: Preview) {
        if (!hasEffects) return
        val serial = PreviewTryOn.firingSerial()
        if (serial == 0 || preview.fired == serial) return
        preview.fired = serial
        for (name in PreviewTryOn.firing()) {
            try {
                renderer.fireEffect(preview.wearer, name)
            } catch (closed: IllegalStateException) {
                LOGGER.debug("Skipped an effect of a freed pet scene", closed)
            }
        }
    }

    private fun previewGeometry(preview: Preview, eye: Matrix4f) {
        CosmeticWearers.begin()
        preview.frame = CosmeticWearers.frame
        preview.count = 0
        if (!hasEffects) return
        try {
            PreviewEffects.place(preview.view, preview.model, eye, UNPLACED, PREVIEW_BODY_BLOCKS)
            val count = renderer.effectGeometry(preview.view, null)
            if (count <= 0) return
            val floats = count * Renderer.QUAD_FLOATS
            if (preview.data.size < floats) preview.data = FloatArray(floats)
            preview.count = renderer.effectGeometry(preview.view, preview.data)
        } catch (ex: RuntimeException) {
            effectsFailed = true
            LOGGER.error("Stopped the effects of pet cosmetic {} after plusrenderer failed to run them", asset.id, ex)
        }
    }

    private fun previewWearer(): Long {
        val base = CosmeticWearers.previewWearer()
        return PREVIEW_WEARERS.getOrPut(base) { Renderer.actorWearer(base, PetManager.PET_SLOT) }
    }

    fun paintEffects(wearer: Long, colorway: Int, colors: Map<String, Int>?) {
        if (!hasEffects) return
        val last = paints[wearer]
        if (last != null && last.colorway == colorway && last.colors == colors) return
        val custom = asset.customColors(colors)
        try {
            renderer.setEffectColors(wearer, colorway, custom?.slots, custom?.rgb)
        } catch (ex: RuntimeException) {
            LOGGER.debug("Could not colour the effects of a pet", ex)
        }
        paints.put(wearer, EffectPaint(colorway, colors))
    }

    fun forget(wearer: Long) {
        combatSent.remove(wearer)
        paints.remove(wearer)
        try {
            renderer.forgetWearer(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped forgetting a pet of a freed scene", closed)
        }
    }

    fun breakRings(wearer: Long) {
        try {
            renderer.breakRings(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped breaking the rings of a pet of a freed scene", closed)
        }
    }

    override fun close() {
        effects.close()
        renderer.close()
    }

    private fun fillRow(
        target: FloatArray,
        base: Int,
        root: FloatArray,
        variables: FloatArray,
        signals: PlayerSignalRecord,
        events: Int,
        detail: Int,
    ) {
        target.fill(0f, base, base + stride)
        signals.copyInto(target, base + signalsAt)
        target[base + eventsAt] = events.toFloat()
        target[base + detailAt] = detail.toFloat()
        System.arraycopy(root, 0, target, base + rootAt, ROOT_FLOATS)
        for (i in variableIds.indices) target[base + variablesAt + variableIds[i]] = variables[i]
        if (playAt >= 0) target[base + playAt] = NO_PLAY
        if (roleAt >= 0) target[base + roleAt] = NO_PLAY
    }

    private fun draw(
        source: FloatArray,
        at: Int,
        clock: Double,
        pose: Pose,
        buffer: VertexConsumer,
        light: Int,
        overlay: Int,
        base: Matrix4f,
    ) {
        val frames = asset.frames
        val vScale = 1f / frames
        val animated = frames > 1 && PolyPlusCosmeticsConfig.cosmeticMotion != CosmeticMotion.OFF
        val strip = if (animated) (floor(clock / STRIP_FRAME_SECONDS).toLong() % frames).toFloat() / frames else 0f
        val offsets = at + runsAt
        var current = -1
        var b = 0
        for (run in 0 until runs.size / 4) {
            val vOffset = source[offsets + run] * vScale + strip
            val start = runs[run * 4 + 1] * 4
            val end = start + runs[run * 4 + 2] * 4
            for (i in start until end) {
                if (bones[i] != current) {
                    current = bones[i]
                    b = at + current * 12
                    BONE.set(
                        source[b], source[b + 4], source[b + 8], 0f,
                        source[b + 1], source[b + 5], source[b + 9], 0f,
                        source[b + 2], source[b + 6], source[b + 10], 0f,
                        source[b + 3], source[b + 7], source[b + 11], 1f,
                    )
                    PLACED.set(base).mul(BONE)
                    MATRIX.set(pose.pose()).mul(PLACED)
                }
                MATRIX.transformPosition(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2], POSITION)
                PLACED.transformDirection(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2], DIRECTION)
                val length = DIRECTION.length()
                val inverse = if (length > 0f) 1f / length else 0f
                pose.transformNormal(DIRECTION.x * inverse, DIRECTION.y * inverse, DIRECTION.z * inverse, NORMAL)
                buffer.addVertex(
                    POSITION.x, POSITION.y, POSITION.z, WHITE,
                    uvs[i * 2], uvs[i * 2 + 1] * vScale + vOffset,
                    overlay, emissive(light, glow[i].toInt()), NORMAL.x, NORMAL.y, NORMAL.z,
                )
            }
        }
    }

    private fun syncCombat(wearer: Long, combat: Boolean) {
        if (combatSent[wearer] == combat) return
        try {
            renderer.setSignalOverride(wearer, PlayerSignals.IS_IN_COMBAT, if (combat) 1f else 0f)
        } catch (ex: IllegalStateException) {
            LOGGER.debug("Could not hand a pet its owner's combat state", ex)
            return
        }
        combatSent.put(wearer, combat)
    }

    private fun fail(ex: RuntimeException) {
        failed = true
        LOGGER.error("Stopped drawing pet cosmetic {} after plusrenderer failed to pose it", asset.id, ex)
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

    private fun ensureRows(needed: Int) {
        if (wearers.size >= needed) return
        val capacity = max(needed, wearers.size * 2)
        wearers = wearers.copyOf(capacity)
        input = input.copyOf(capacity * stride)
        out = out.copyOf(capacity * outStride)
        placedRoots = placedRoots.copyOf(capacity * ROOT_FLOATS)
    }

    private fun emissive(base: Int, level: Int): Int {
        if (level == 0) return base
        val block = maxOf((base shr 4) and 0xF, level)
        val sky = maxOf((base shr 20) and 0xF, level)
        return (block shl 4) or (sky shl 20)
    }

    companion object {
        const val ROOT_FLOATS = Renderer.ROOT_FLOATS
        const val ANCHOR_FLOATS = 4
        //? if >= 1.21.10 {
        const val QUEUE_EFFECTS = true
        //?} else {
        /*const val QUEUE_EFFECTS = false
        *///?}
        private const val NO_PLAY = -1f
        private const val SLOT = 0
        private const val INITIAL_ROWS = 4
        private const val STRIP_FRAME_SECONDS = 0.2
        private const val WHITE = -1
        private const val PREVIEW_BODY_BLOCKS = 0.5f
        private val PREVIEW_WEARERS = HashMap<Long, Long>()
        private val PREVIEW_VARIABLES = FloatArray(PetVariables.NAMES.size)
        private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pets")
        private val MATRIX = Matrix4f()
        private val BONE = Matrix4f()
        private val PLACED = Matrix4f()
        private val SHIFT = Matrix4f()
        private val UNPLACED = Matrix4f()
        private val DIRECTION = Vector3f()
        private val POSITION = Vector3f()
        private val NORMAL = Vector3f()

        fun root(out: FloatArray, dx: Float, dy: Float, dz: Float, yawRadians: Float, scale: Float) {
            PetPose.root(out, dx, dy, dz, yawRadians, 0f, scale / PIXELS_PER_BLOCK)
        }

        fun guiRoot(out: FloatArray, yawRadians: Float, scale: Float) {
            val k = scale / PIXELS_PER_BLOCK
            val c = cos(yawRadians)
            val s = sin(yawRadians)
            out[0] = c * k
            out[1] = 0f
            out[2] = -s * k
            out[3] = 0f
            out[4] = 0f
            out[5] = -k
            out[6] = 0f
            out[7] = 0f
            out[8] = -s * k
            out[9] = 0f
            out[10] = -c * k
            out[11] = 0f
        }

        private const val PIXELS_PER_BLOCK = 16f
    }
}

internal object PetVariables {
    val NAMES = arrayOf(
        "pet_mode",
        "pet_speed",
        "pet_distance",
        "pet_on_ground",
        "pet_in_water",
        "pet_mood",
        "pet_interaction",
        "pet_role",
        "pet_interaction_time",
        "pet_action",
        "pet_look_yaw",
        "pet_look_pitch",
    )
    const val MODE = 0
    const val SPEED = 1
    const val DISTANCE = 2
    const val ON_GROUND = 3
    const val IN_WATER = 4
    const val MOOD = 5
    const val INTERACTION = 6
    const val ROLE = 7
    const val INTERACTION_TIME = 8
    const val ACTION = 9
    const val LOOK_YAW = 10
    const val LOOK_PITCH = 11

    const val IDLE = 0f
    const val FOLLOWING = 1f
    const val CATCHING_UP = 2f
    const val SITTING = 3f
    const val RIDING = 4f
    const val SLEEPING = 5f
    const val WANDERING = 6f
    const val PLAYING = 7f
    const val IDLE_ACTION = 8f
}
