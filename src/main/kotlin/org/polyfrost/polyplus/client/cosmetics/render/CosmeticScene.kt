package org.polyfrost.polyplus.client.cosmetics.render

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.CosmeticMotion
import org.polyfrost.polyplus.client.CosmeticPhysics
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.cosmetics.assets.CosmeticBundles
import org.polyfrost.polyplus.client.cosmetics.moments.Moment
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.VertexConsumer
import org.polyfrost.polyplus.client.utils.CosmeticClock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ref.Cleaner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
//?} else {
/*import net.minecraft.client.render.model.ModelPart
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess
import org.polyfrost.polyplus.compat.OverflowAnimationsCompat
*///?}

/**
 * One cosmetic in its own plusrenderer scene, which attaches, animates and meshes it the same way every
 * plusrenderer host does. Each frame follows the vanilla player model's posed parts.
 *
 * Built on any thread, then used from the render thread only. Its native memory goes once it is unreachable,
 * since equipment may still be wearing a scene the asset cache has let go of.
 */
class CosmeticScene private constructor(
    private val geometry: String?,
    val asset: CosmeticAsset?,
    meta: JsonObject?,
    slot: BodySlot,
    anchor: String?,
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val scale: Float,
) {
    constructor(geometry: String, slot: BodySlot, anchor: String?, imageWidth: Int, imageHeight: Int, scale: Float) :
        this(geometry, null, null, slot, anchor, imageWidth, imageHeight, scale)

    internal constructor(parsed: CosmeticBundles.Parsed, slot: BodySlot, scale: Float) :
        this(null, parsed.asset, parsed.meta, slot, null, 0, 0, scale)

    private val renderer = Renderer()
    private val cleanable = CLEANER.register(this, closer(renderer, asset))
    private val bodySlot = bodySlotName(slot)
    private val anchorName = anchor.orEmpty()
    private val partIds: IntArray
    private val queryIds: IntArray
    private val queryCount: Int
    private val sections = IntArray(Renderer.SECTION_INTS)
    private val inputStride: Int
    private val signalsOffset: Int
    private val eventsOffset: Int
    private val detailOffset: Int
    private val rootOffset: Int
    private val playOffset: Int
    private val skin: CosmeticSkin
    private val outputStride: Int
    private val colorwayAt: Int
    private val probe: FloatArray

    val hasEffects: Boolean
    internal val sounds: Array<CosmeticSoundRef?>
    private val vanillaEmitters: Array<String?>
    private val colorways: List<String>
    internal val paletteSlots: List<String>
    private val paints = HashMap<Long, Paint>()
    private val reactions = HashMap<Long, Int>()
    private val previews = HashMap<Long, PreviewQuads>()
    private val previewFired = HashMap<Long, Int>()
    private val previewOverrides = HashMap<Long, Int>()

    internal val clipSeconds: Float
    private val clipNames: List<String>
    private val clipLengths: FloatArray
    private val momentPlans: Map<String, MomentPlan>?
    private val plays = HashMap<Long, ClipPlay>()

    var effectTextures: List<Identifier> = emptyList()
        private set

    internal var bonus = false

    @Volatile
    internal var closed = false
        private set

    private var motion: CosmeticMotion? = null
    private var physicsMode = -1
    private var physicsWearer = 0L
    private var batchFrame = -1
    private var rows = 0
    private var computed = 0
    private var failed = false
    private var anyCollected = false
    private var wearerIds = LongArray(INITIAL_ROWS)
    private var rowWearers = arrayOfNulls<CosmeticWearers.Wearer>(INITIAL_ROWS)
    private var collectedRows = BooleanArray(INITIAL_ROWS)
    private var unconfirmedRows = BooleanArray(INITIAL_ROWS)
    private var echoRows = BooleanArray(INITIAL_ROWS)
    private var heldInts = IntArray(HELD_INTS * 8)
    private var heldFloats = FloatArray(HELD_FLOATS * 8)
    private var heldCount = 0
    private var input: FloatArray
    private var out: FloatArray
    private var scratchIds = LongArray(0)
    private var scratchInput = FloatArray(0)
    private var scratchOut = FloatArray(0)
    private var eventInts = IntArray(Renderer.EFFECT_EVENT_INTS * 8)
    private var eventFloats = FloatArray(Renderer.EFFECT_EVENT_FLOATS * 8)
    private val view = FloatArray(Renderer.VIEW_FLOATS)
    private var geometryOut = FloatArray(0)
    private var viewEpoch = -1
    private var viewX = 0.0
    private var viewY = 0.0
    private var viewZ = 0.0

    internal var quads = FloatArray(0)
        private set
    internal var quadCount = 0
        private set

    internal val effectSource: EffectSource = object : EffectSource {
        override val effectTextures: List<Identifier> get() = this@CosmeticScene.effectTextures
        override val quads: FloatArray get() = this@CosmeticScene.quads
        override val quadCount: Int get() = this@CosmeticScene.quadCount

        override fun computeFor(frame: Int): Boolean {
            if (!this@CosmeticScene.computeFor(frame)) return false
            heldCount = 0
            return true
        }
    }

    private var soundCleanable: Cleaner.Cleanable? = null

    private var animationJson: String? = null
    private var definition: CosmeticScene? = null
    private val slotScenes = HashMap<String, CosmeticScene>()
    private var rest: Renderer? = null
    private var restCleanable: Cleaner.Cleanable? = null

    /** Seconds per loop of the cosmetic's animation, or 0 if it has none or it is unknown. */
    var animationSeconds = 0f
        private set

    /** Whether the bundle's animation file loaded. */
    var hasAnimation = false
        private set

    /** Whether the geometry has no cubes to draw. */
    val isEmpty: Boolean

    private val hasSheet: Boolean

    /** Whether its pose or texture changes over time. */
    val isAnimated: Boolean get() = hasAnimation || hasSheet

    init {
        try {
            load(renderer)
            partIds = IntArray(PARTS.size) { renderer.partId(PARTS[it]) }
            queryIds = IntArray(QUERIES.size) { renderer.queryId(QUERIES[it]) }
            queryCount = (queryIds.maxOrNull() ?: -1) + 1
            colorways = namesOf(meta, "palette", "colorways")
            hasEffects = asset != null && renderer.hasEffects()
            sections[Renderer.SECTION_PARTS] = PART_COUNT
            sections[Renderer.SECTION_QUERIES] = queryCount
            sections[Renderer.SECTION_ROOT] = 1
            sections[Renderer.SECTION_COLORWAY] = if (asset != null && colorways.isNotEmpty()) 1 else 0
            sections[Renderer.SECTION_PLAY] = if (hasEffects) 1 else 0
            val layout = IntArray(Renderer.INPUT_LAYOUT_INTS)
            Renderer.inputLayout(sections, layout)
            inputStride = layout[Renderer.INPUT_STRIDE]
            signalsOffset = layout[Renderer.INPUT_SIGNALS_AT]
            eventsOffset = layout[Renderer.INPUT_EVENTS_AT]
            detailOffset = layout[Renderer.INPUT_DETAIL_AT]
            rootOffset = layout[Renderer.INPUT_ROOT_AT]
            playOffset = if (hasEffects) layout[Renderer.INPUT_PLAY_AT] else -1
            isEmpty = renderer.buildSkin() == 0
            skin = CosmeticSkin(
                renderer.skinPositions(),
                renderer.skinNormals(),
                renderer.skinUvs(),
                renderer.skinGlow(),
                renderer.skinBones(),
                renderer.skinRuns(),
                renderer.skinBoneCount(),
            )
            val output = IntArray(Renderer.LAYOUT_INTS)
            renderer.outputLayout(sections, output)
            outputStride = output[Renderer.LAYOUT_STRIDE]
            colorwayAt = if (output[Renderer.LAYOUT_COLORWAY] > 0) output[Renderer.LAYOUT_COLORWAY_AT] else -1
            probe = FloatArray(max(outputStride, skin.boneCount * 12 + skin.runs.size / 4))
            input = FloatArray(inputStride * INITIAL_ROWS)
            out = FloatArray(outputStride * INITIAL_ROWS)
            hasSheet = asset == null && !isEmpty && sheetOffsetAt(SECOND_SHEET_FRAME_SECONDS) != 0f
            sounds = EffectMedia.sounds(asset, meta)
            if (sounds.isNotEmpty()) soundCleanable = EffectMedia.CLEANER.register(this, EffectMedia.releaser(sounds))
            vanillaEmitters = EffectMedia.vanilla(meta)
            paletteSlots = slotIdsOf(meta)
            clipSeconds = clipSecondsOf(meta)
            val clipEntries = meta?.let { CosmeticBundles.array(it, "clips") }
                ?.mapNotNull { it.takeIf { entry -> entry.isJsonObject }?.asJsonObject }
                .orEmpty()
            clipNames = clipEntries.map { it.get("name")?.takeIf { name -> name.isJsonPrimitive }?.asString.orEmpty() }
            clipLengths = FloatArray(clipEntries.size) {
                clipEntries[it].get("length")?.takeIf { length -> length.isJsonPrimitive && length.asJsonPrimitive.isNumber }?.asFloat ?: 0f
            }
            momentPlans = momentPlansOf(meta)
            if (meta != null) {
                hasAnimation = (CosmeticBundles.array(meta, "animations")?.size() ?: 0) > 0 ||
                    (CosmeticBundles.array(meta, "controllers")?.size() ?: 0) > 0
            }
            renderer.play(SLOT, CosmeticClock.seconds())
        } catch (ex: RuntimeException) {
            close()
            throw ex
        }
    }

    /** Plays the bundle's animation file from when it loads, shared by every wearer. Throws [IllegalArgumentException] if it doesn't parse. */
    fun animate(json: String, seconds: Float) {
        if (asset != null) return
        renderer.loadAnimation(SLOT, json)
        renderer.play(SLOT, CosmeticClock.seconds())
        animationJson = json
        hasAnimation = true
        animationSeconds = seconds
    }

    /** The cosmetic's vertices off the player, [seconds] into its animation, in a render layer's space. */
    fun restPositions(seconds: Float): FloatArray {
        val rest = rest ?: restRenderer()
        rest.clearGameParts()
        rest.pose(seconds)
        rest.buildMesh(Renderer.JAVA_MODEL_SPACE)
        return rest.meshPositions()
    }

    internal fun forSlot(slot: BodySlot): CosmeticScene {
        definition?.let { return it.forSlot(slot) }
        val source = geometry
        if (asset != null || source == null) return this
        val body = bodySlotName(slot)
        if (body == bodySlot) return this
        synchronized(slotScenes) {
            slotScenes[body]?.let { return it }
            val scene = try {
                CosmeticScene(source, slot, anchorName.ifEmpty { null }, imageWidth, imageHeight, scale)
            } catch (ex: RuntimeException) {
                LOGGER.warn("Could not attach a cosmetic to the {} slot", body, ex)
                return this
            }
            scene.definition = this
            animationJson?.let {
                try {
                    scene.animate(it, animationSeconds)
                } catch (ex: RuntimeException) {
                    LOGGER.debug("Could not animate a cosmetic in the {} slot", body, ex)
                }
            }
            slotScenes[body] = scene
            return scene
        }
    }

    internal fun attachEffectTextures(textures: List<Identifier>) {
        effectTextures = textures
    }

    internal fun add(
        wearer: CosmeticWearers.Wearer,
        model: PlayerModel,
        context: PlayerRenderContext,
        backOffset: Float,
        events: Int,
        root: FloatArray,
        collect: Boolean = false,
    ): Int {
        if ((isEmpty && !hasEffects) || failed) return -1
        val base = nextRow() * inputStride
        feedParts(model, backOffset, base)
        feedQueries(context, base + PART_COUNT * 12)
        context.signals.copyInto(input, base + signalsOffset)
        input[base + eventsOffset] = events.toFloat()
        input[base + detailOffset] = Renderer.DETAIL_FULL.toFloat()
        System.arraycopy(root, 0, input, base + rootOffset, Renderer.ROOT_FLOATS)
        var echoOf = -1
        if (!collect && !wearer.preview) {
            if (CosmeticView.rebased) adoptCollectedRoot(wearer.id, base)
            writePlay(base, wearer.id)
            val collected = collectedRow(wearer.id, base)
            if (collected >= 0) {
                rowWearers[collected] = wearer
                confirm(collected, collected)
                CosmeticWearers.drawn(wearer, this)
                if (hasEffects) CosmeticEffects.pending(effectSource)
                return collected
            }
            val stale = staleRow(wearer.id)
            if (stale >= computed && unconfirmedRows[stale]) {
                val collectedEvents = input[stale * inputStride + eventsOffset].toInt()
                input[base + eventsOffset] = (collectedEvents or input[base + eventsOffset].toInt()).toFloat()
                System.arraycopy(input, base, input, stale * inputStride, inputStride)
                rowWearers[stale] = wearer
                collectedRows[stale] = false
                unconfirmedRows[stale] = false
                CosmeticWearers.drawn(wearer, this)
                if (hasEffects) CosmeticEffects.pending(effectSource)
                return stale
            }
            echoOf = stale
        }
        wearerIds[rows] = wearer.id
        rowWearers[rows] = wearer
        collectedRows[rows] = collect
        unconfirmedRows[rows] = collect
        if (collect) anyCollected = true
        echoRows[rows] = echoOf >= 0
        if (echoOf >= 0) confirm(echoOf, rows)
        CosmeticWearers.drawn(wearer, this)
        if (wearer.preview) {
            syncOverrides(wearer.id, PreviewTryOn.overrideMask())
            if (hasEffects) {
                previews.getOrPut(wearer.id) { PreviewQuads() }
                firePreview(wearer.id)
            }
        } else {
            if (previewOverrides.isNotEmpty() || PreviewTryOn.inLobbyScope()) syncOverrides(wearer.id, PreviewTryOn.lobbyOverrideMask())
            if (hasEffects) CosmeticEffects.pending(effectSource)
        }
        writePlay(base, wearer.id)
        return rows++
    }

    private fun collectedRow(wearer: Long, base: Int): Int {
        if (!anyCollected) return -1
        for (row in 0 until rows) {
            if (!collectedRows[row] || wearerIds[row] != wearer) continue
            if (CosmeticRows.same(input, row * inputStride, base, inputStride, eventsOffset, detailOffset)) return row
        }
        return -1
    }

    private fun adoptCollectedRoot(wearer: Long, base: Int) {
        val collected = staleRow(wearer)
        if (collected < 0) return
        val from = collected * inputStride + rootOffset
        val to = base + rootOffset
        if (CosmeticRows.nearRoot(input, from, to, Renderer.ROOT_FLOATS)) System.arraycopy(input, from, input, to, Renderer.ROOT_FLOATS)
    }

    private fun staleRow(wearer: Long): Int {
        if (!anyCollected) return -1
        for (row in 0 until rows) {
            if (collectedRows[row] && wearerIds[row] == wearer) return row
        }
        return -1
    }

    private fun confirm(row: Int, anchor: Int) {
        unconfirmedRows[row] = false
        if (heldCount == 0) return
        var kept = 0
        for (i in 0 until heldCount) {
            val ints = i * HELD_INTS
            val floats = i * HELD_FLOATS
            if (heldInts[ints] == row) {
                play(anchor, heldInts[ints + 1], heldInts[ints + 2], heldFloats, floats)
                continue
            }
            if (kept != i) {
                System.arraycopy(heldInts, ints, heldInts, kept * HELD_INTS, HELD_INTS)
                System.arraycopy(heldFloats, floats, heldFloats, kept * HELD_FLOATS, HELD_FLOATS)
            }
            kept++
        }
        heldCount = kept
    }

    private fun hold(row: Int, kind: Int, effect: Int, floats: FloatArray, at: Int) {
        if ((heldCount + 1) * HELD_INTS > heldInts.size) {
            heldInts = heldInts.copyOf(heldInts.size * 2)
            heldFloats = heldFloats.copyOf(heldFloats.size * 2)
        }
        val ints = heldCount * HELD_INTS
        heldInts[ints] = row
        heldInts[ints + 1] = kind
        heldInts[ints + 2] = effect
        System.arraycopy(floats, at, heldFloats, heldCount * HELD_FLOATS, HELD_FLOATS)
        heldCount++
    }

    private fun play(row: Int, kind: Int, effect: Int, floats: FloatArray, f: Int) {
        val wearer = rowWearers.getOrNull(row) ?: return
        val root = row * inputStride + rootOffset
        val x = CosmeticOrigin.x + input[root + 3] + floats[f]
        val y = CosmeticOrigin.y + input[root + 7] + floats[f + 1]
        val z = CosmeticOrigin.z + input[root + 11] + floats[f + 2]
        when (kind) {
            Renderer.EFFECT_SOUND -> sounds.getOrNull(effect)?.let {
                CosmeticSoundPlayer.play(it, x, y, z, floats[f + 3], floats[f + 4], wearer.local)
            }
            Renderer.EFFECT_VANILLA_PARTICLE -> vanillaEmitters.getOrNull(effect)?.let {
                CosmeticSoundPlayer.particle(it, x, y, z)
            }
        }
    }

    internal fun previewSource(wearer: Long): EffectSource? {
        if (!hasEffects || failed) return null
        return previews[wearer]
    }

    internal fun addBare(wearer: CosmeticWearers.Wearer, signals: PlayerSignalRecord, events: Int, root: FloatArray): Int {
        if (!hasEffects || failed) return -1
        val row = nextRow()
        val base = row * inputStride
        input.fill(0f, base, base + inputStride)
        restParts(base)
        val queries = base + PART_COUNT * 12
        query(queries, DELTA_TIME, 1f / BedrockConstants.TICKS_PER_SECOND)
        for (i in 0 until PlayerSignals.QUERY_COUNT) query(queries, FIRST_SIGNAL + i, signals.query(i))
        signals.copyInto(input, base + signalsOffset)
        input[base + eventsOffset] = events.toFloat()
        input[base + detailOffset] = Renderer.DETAIL_FULL.toFloat()
        System.arraycopy(root, 0, input, base + rootOffset, Renderer.ROOT_FLOATS)
        writePlay(base, wearer.id)
        wearerIds[row] = wearer.id
        rowWearers[row] = wearer
        collectedRows[row] = false
        unconfirmedRows[row] = false
        echoRows[row] = false
        CosmeticWearers.drawn(wearer, this)
        if (!wearer.preview) CosmeticEffects.pending(effectSource)
        return rows++
    }

    internal fun addArm(
        wearer: CosmeticWearers.Wearer,
        arm: ModelPart,
        right: Boolean,
        signals: PlayerSignalRecord,
        events: Int,
        ageInTicks: Float,
        root: FloatArray,
    ): Int {
        if (isEmpty || failed) return -1
        val row = nextRow()
        val base = row * inputStride
        input.fill(0f, base, base + inputStride)
        restParts(base)
        if (right) feed(base, RIGHT_ARM, arm, -ARM_X, ARM_Y) else feed(base, LEFT_ARM, arm, ARM_X, ARM_Y)
        val queries = base + PART_COUNT * 12
        query(queries, LIFE_TIME, ageInTicks / BedrockConstants.TICKS_PER_SECOND)
        query(queries, DELTA_TIME, 1f / BedrockConstants.TICKS_PER_SECOND)
        for (i in 0 until PlayerSignals.QUERY_COUNT) query(queries, FIRST_SIGNAL + i, signals.query(i))
        signals.copyInto(input, base + signalsOffset)
        input[base + eventsOffset] = events.toFloat()
        input[base + detailOffset] = Renderer.DETAIL_FULL.toFloat()
        System.arraycopy(root, 0, input, base + rootOffset, Renderer.ROOT_FLOATS)
        writePlay(base, wearer.id)
        wearerIds[row] = wearer.id
        rowWearers[row] = wearer
        collectedRows[row] = false
        unconfirmedRows[row] = false
        echoRows[row] = false
        CosmeticWearers.drawn(wearer, this)
        return rows++
    }

    internal fun claimRow(wearer: Long): Boolean {
        if (batchFrame != CosmeticWearers.frame) return false
        for (row in 0 until rows) {
            if (wearerIds[row] != wearer || rowWearers[row] == null) continue
            if (unconfirmedRows[row]) confirm(row, row)
            return true
        }
        return false
    }

    internal fun fireEffect(wearer: Long, name: String): Boolean {
        if (!hasEffects || failed) return false
        return try {
            renderer.fireEffect(wearer, name)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped an effect of a freed cosmetic", closed)
            false
        }
    }

    internal fun setSeedWearer(wearer: Long, seedWearer: Long) {
        if (!hasEffects || failed) return
        try {
            renderer.setSeedWearer(wearer, seedWearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped seeding a wearer of a freed cosmetic", closed)
        }
    }

    internal fun startClip(wearer: Long, name: String, at: Double) {
        if (playOffset < 0) return
        if (plays.size >= MAX_PLAYS) plays.values.removeIf { at > it.until }
        val plan = momentPlans?.get(name)
        val clip = plan?.clip ?: momentClip(name)
        val seconds = plan?.seconds ?: max(Renderer.EVENT_EFFECT_SECONDS, if (clip >= 0) clipLengths[clip] else 0f)
        plays[wearer] = ClipPlay(clip, at, at + seconds)
    }

    internal fun momentSeconds(name: String): Float? = momentPlans?.get(name)?.seconds

    private fun momentClip(moment: String): Int {
        val own = clipNames.indexOfFirst { playsMoment(it, moment) }
        if (own >= 0) return own
        val authored = Moment.entries.any { m -> clipNames.any { playsMoment(it, m.effectName) } }
        return if (authored || clipNames.isEmpty()) NO_CLIP else 0
    }

    private fun playsMoment(clip: String, moment: String): Boolean =
        clip == moment || clip.startsWith(BEDROCK_CLIP_PREFIX) && clip.removePrefix(BEDROCK_CLIP_PREFIX).endsWith(".$moment")

    internal fun showsModel(wearer: CosmeticWearers.Wearer, slot: BodySlot): Boolean {
        if (bonus && bonusDropped(wearer, CosmeticWearers.detail(wearer))) return false
        if (slot != BodySlot.EffectB) return true
        if (livePlay(wearer.id) == null) return false
        return effectsShown(wearer)
    }

    private fun bonusDropped(wearer: CosmeticWearers.Wearer, level: Int): Boolean =
        bonus && level != Renderer.DETAIL_FULL && !wearer.firstPerson

    private fun effectsShown(wearer: CosmeticWearers.Wearer): Boolean =
        PolyPlusCosmeticsConfig.cosmeticMotion != CosmeticMotion.OFF &&
            CosmeticWearers.detail(wearer) != Renderer.DETAIL_MINIMAL

    private fun livePlay(wearer: Long): ClipPlay? {
        if (plays.isEmpty()) return null
        val play = plays[wearer] ?: return null
        if (CosmeticWearers.clock <= play.until) return play
        plays.remove(wearer)
        return null
    }

    private fun writePlay(base: Int, wearer: Long) {
        if (playOffset < 0) return
        val play = livePlay(wearer)
        input[base + playOffset + Renderer.PLAY_CLIP] = (play?.clip ?: NO_CLIP).toFloat()
        input[base + playOffset + Renderer.PLAY_AGE] = play?.let { (CosmeticWearers.clock - it.started).toFloat() } ?: 0f
    }

    private val colorwayIndices: Map<String, Int> by lazy(LazyThreadSafetyMode.NONE) {
        val indices = HashMap<String, Int>(colorways.size * 2)
        for (at in colorways.indices.reversed()) indices[colorways[at]] = at
        indices
    }

    internal fun colorwayIndex(name: String?): Int =
        if (name == null || colorways.isEmpty()) CosmeticAsset.NO_COLORWAY else colorwayIndices[name] ?: CosmeticAsset.NO_COLORWAY

    internal fun reactionColorway(wearer: Long): Int =
        if (reactions.isEmpty()) CosmeticAsset.NO_COLORWAY else reactions[wearer] ?: CosmeticAsset.NO_COLORWAY

    internal fun custom(colors: Map<String, Int>?): CustomColors? = CustomColors.of(paletteSlots, colors)

    internal fun paint(wearer: Long, colorway: String?, colors: Map<String, Int>?) {
        paint(wearer, colorwayIndex(colorway), colors)
    }

    internal fun paint(wearer: Long, index: Int, colors: Map<String, Int>?) {
        if (!hasEffects || failed) return
        val last = paints[wearer]
        if (last != null && last.colorway == index && last.colors == colors) return
        val custom = custom(colors)
        try {
            renderer.setEffectColors(wearer, index, custom?.slots, custom?.rgb)
        } catch (ex: RuntimeException) {
            LOGGER.debug("Could not colour the effects of a cosmetic", ex)
        }
        paints[wearer] = Paint(index, colors)
    }

    internal fun computeFor(frame: Int): Boolean {
        if (frame != batchFrame) return false
        compute()
        return true
    }

    internal fun emit(
        frame: Int,
        row: Int,
        pose: Pose,
        buffer: VertexConsumer,
        lightCoords: Int,
        overlayCoords: Int,
        color: Int,
    ) {
        if (row < 0 || frame != batchFrame || row >= rows) return
        if (row >= computed) compute()
        if (row >= computed) return
        val skin = skin
        val out = out
        val runs = skin.runs
        val bones = skin.bones
        val positions = skin.positions
        val normals = skin.normals
        val uvs = skin.uvs
        val glow = skin.glow
        val at = row * outputStride
        val offsets = at + skin.boneCount * 12
        var current = -1
        var b = 0
        for (run in 0 until runs.size / 4) {
            val vOffset = out[offsets + run]
            val start = runs[run * 4 + 1] * 4
            val end = start + runs[run * 4 + 2] * 4
            for (i in start until end) {
                if (bones[i] != current) {
                    current = bones[i]
                    b = at + current * 12
                    BONE.set(
                        out[b], out[b + 4], out[b + 8], 0f,
                        out[b + 1], out[b + 5], out[b + 9], 0f,
                        out[b + 2], out[b + 6], out[b + 10], 0f,
                        out[b + 3], out[b + 7], out[b + 11], 1f,
                    )
                    MATRIX.set(pose.pose()).mul(BONE)
                }
                val px = positions[i * 3]
                val py = positions[i * 3 + 1]
                val pz = positions[i * 3 + 2]
                MATRIX.transformPosition(px, py, pz, POSITION)
                val nx = normals[i * 3]
                val ny = normals[i * 3 + 1]
                val nz = normals[i * 3 + 2]
                val tx = out[b] * nx + out[b + 1] * ny + out[b + 2] * nz
                val ty = out[b + 4] * nx + out[b + 5] * ny + out[b + 6] * nz
                val tz = out[b + 8] * nx + out[b + 9] * ny + out[b + 10] * nz
                val length = sqrt(tx * tx + ty * ty + tz * tz)
                val inverse = if (length > 0f) 1f / length else 0f
                pose.transformNormal(tx * inverse, ty * inverse, tz * inverse, NORMAL)
                val light = emissive(lightCoords, glow[i].toInt())
                buffer.addVertex(
                    POSITION.x, POSITION.y, POSITION.z, color,
                    uvs[i * 2], uvs[i * 2 + 1] + vOffset,
                    overlayCoords, light, NORMAL.x, NORMAL.y, NORMAL.z,
                )
            }
        }
    }

    internal fun forget(wearer: Long) {
        dropRows(wearer)
        plays.remove(wearer)
        paints.remove(wearer)
        reactions.remove(wearer)
        previews.remove(wearer)
        previewFired.remove(wearer)
        previewOverrides.remove(wearer)
        try {
            renderer.forgetWearer(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped forgetting a wearer of a freed cosmetic", closed)
        }
    }

    internal fun breakRings(wearer: Long) {
        try {
            renderer.breakRings(wearer)
        } catch (closed: IllegalStateException) {
            LOGGER.debug("Skipped breaking the rings of a freed cosmetic", closed)
        }
    }

    /** Frees the scene early. Only for one that was never handed out. */
    fun close() {
        closed = true
        soundCleanable?.clean()
        restCleanable?.clean()
        cleanable.clean()
        val scenes = synchronized(slotScenes) { slotScenes.values.toList().also { slotScenes.clear() } }
        for (scene in scenes) scene.close()
    }

    private fun compute() {
        if (failed || computed >= rows) return
        for (row in computed until rows) {
            val wearer = rowWearers[row] ?: continue
            val level = CosmeticWearers.detail(wearer)
            input[row * inputStride + detailOffset] = (if (bonusDropped(wearer, level)) Renderer.DETAIL_MINIMAL else level).toFloat()
        }
        var began = System.nanoTime()
        var preview = false
        try {
            syncMotion()
            var start = computed
            while (start < rows) {
                preview = rowWearers[start]?.preview == true
                val hand = rowWearers[start]?.hand == true
                val echo = echoRows[start]
                var end = start + 1
                while (end < rows && sameBatch(start, end, preview, hand) && echoRows[end] == echo) end++
                syncPhysics(preview || hand)
                frameRows(start, end)
                readColorways(start, end)
                if (hasEffects && !hand && !echo) {
                    if (preview) previewGeometry(start) else effects(start)
                }
                start = end
                began = chargeWorld(began, preview)
            }
            computed = rows
        } catch (ex: RuntimeException) {
            failed = true
            LOGGER.error("Stopped drawing a cosmetic after plusrenderer failed to pose it", ex)
        } finally {
            chargeWorld(began, preview)
        }
    }

    private fun chargeWorld(began: Long, preview: Boolean): Long {
        val now = System.nanoTime()
        if (!preview) CosmeticWearers.charge(now - began)
        return now
    }

    private fun frameRows(first: Int, end: Int) {
        val count = end - first
        if (first == 0) {
            renderer.frameWearersShaped(
                CosmeticWearers.clock, wearerIds, count, sections, input, Renderer.JAVA_MODEL_SPACE, out,
            )
            return
        }
        ensureScratch(count)
        System.arraycopy(wearerIds, first, scratchIds, 0, count)
        System.arraycopy(input, first * inputStride, scratchInput, 0, count * inputStride)
        renderer.frameWearersShaped(
            CosmeticWearers.clock, scratchIds, count, sections, scratchInput, Renderer.JAVA_MODEL_SPACE, scratchOut,
        )
        System.arraycopy(scratchOut, 0, out, first * outputStride, count * outputStride)
    }

    private fun readColorways(first: Int, end: Int) {
        if (colorwayAt < 0) return
        for (row in first until end) {
            val colorway = out[row * outputStride + colorwayAt].toInt()
            val wearer = wearerIds[row]
            if (colorway in colorways.indices) {
                if (reactions[wearer] != colorway) reactions[wearer] = colorway
            } else if (reactions.isNotEmpty()) {
                reactions.remove(wearer)
            }
        }
    }

    private fun effects(first: Int) {
        var events = renderer.effectEvents(eventInts)
        if (events * Renderer.EFFECT_EVENT_INTS > eventInts.size) {
            eventInts = IntArray(events * Renderer.EFFECT_EVENT_INTS)
            events = renderer.effectEvents(eventInts)
        }
        if (events * Renderer.EFFECT_EVENT_FLOATS > eventFloats.size) {
            eventFloats = FloatArray(events * Renderer.EFFECT_EVENT_FLOATS)
        }
        renderer.effectEventFloats(eventFloats)
        for (i in 0 until events) {
            val at = i * Renderer.EFFECT_EVENT_INTS
            val row = first + eventInts[at]
            if (row >= rows) continue
            val f = i * Renderer.EFFECT_EVENT_FLOATS
            val kind = eventInts[at + 4]
            val effect = eventInts[at + 5]
            if (unconfirmedRows[row]) hold(row, kind, effect, eventFloats, f) else play(row, kind, effect, eventFloats, f)
        }
        geometry()
    }

    private fun sameBatch(first: Int, row: Int, preview: Boolean, hand: Boolean): Boolean {
        val wearer = rowWearers[row]
        if ((wearer?.preview == true) != preview || (wearer?.hand == true) != hand) return false
        return !preview || !hasEffects || wearer?.id == rowWearers[first]?.id
    }

    private fun previewGeometry(row: Int) {
        val wearer = rowWearers[row] ?: return
        val target = previews[wearer.id] ?: return
        target.frame = batchFrame
        target.count = 0
        if (!wearer.previewPlaced) return
        var drawn = quadsInto(wearer.previewView, target.data)
        if (drawn < 0) {
            val count = renderer.effectGeometry(wearer.previewView, null)
            if (count <= 0) return
            target.data = FloatArray(count * Renderer.QUAD_FLOATS)
            drawn = renderer.effectGeometry(wearer.previewView, target.data)
        }
        target.count = drawn
    }

    private fun firePreview(wearer: Long) {
        val serial = PreviewTryOn.firingSerial()
        if (serial == 0 || previewFired[wearer] == serial) return
        previewFired[wearer] = serial
        for (name in PreviewTryOn.firing()) {
            if (fireEffect(wearer, name)) startClip(wearer, name, CosmeticWearers.clock)
        }
    }

    private fun syncOverrides(wearer: Long, mask: Int) {
        val last = previewOverrides[wearer] ?: 0
        if (last == mask) return
        try {
            for ((bit, signal) in PreviewTryOn.OVERRIDDEN.withIndex()) {
                val on = mask and (1 shl bit) != 0
                if (on == (last and (1 shl bit) != 0)) continue
                renderer.setSignalOverride(wearer, signal, if (on) 1f else Float.NaN)
            }
        } catch (ex: IllegalStateException) {
            LOGGER.debug("Could not override a preview signal", ex)
            return
        }
        if (mask == 0) previewOverrides.remove(wearer) else previewOverrides[wearer] = mask
    }

    private inner class PreviewQuads : EffectSource {
        var frame = -1
        var count = 0
        var data = FloatArray(0)

        override val effectTextures: List<Identifier> get() = this@CosmeticScene.effectTextures
        override val quads: FloatArray get() = data
        override val quadCount: Int get() = count

        override fun computeFor(frame: Int): Boolean = this@CosmeticScene.computeFor(frame) && this.frame == frame
    }

    private fun geometry() {
        var shiftX = 0f
        var shiftY = 0f
        var shiftZ = 0f
        if (viewEpoch != CosmeticOrigin.epoch) {
            if (viewEpoch >= 0) {
                shiftX = (CosmeticOrigin.x - viewX).toFloat()
                shiftY = (CosmeticOrigin.y - viewY).toFloat()
                shiftZ = (CosmeticOrigin.z - viewZ).toFloat()
            }
            viewEpoch = CosmeticOrigin.epoch
            viewX = CosmeticOrigin.x
            viewY = CosmeticOrigin.y
            viewZ = CosmeticOrigin.z
        }
        CosmeticOrigin.view(view, shiftX, shiftY, shiftZ)
        var drawn = quadsInto(view, geometryOut)
        if (drawn < 0) {
            val count = renderer.effectGeometry(view, null)
            if (count <= 0) return
            val floats = count * Renderer.QUAD_FLOATS
            geometryOut = FloatArray(floats + floats / 4)
            drawn = renderer.effectGeometry(view, geometryOut)
        }
        if (drawn <= 0) return
        val needed = (quadCount + drawn) * Renderer.QUAD_FLOATS
        if (quads.size < needed) quads = quads.copyOf(max(needed, quads.size * 2))
        System.arraycopy(geometryOut, 0, quads, quadCount * Renderer.QUAD_FLOATS, drawn * Renderer.QUAD_FLOATS)
        quadCount += drawn
    }

    private fun quadsInto(view: FloatArray, buffer: FloatArray): Int =
        try {
            renderer.effectGeometry(view, buffer)
        } catch (short: IllegalArgumentException) {
            -1
        }

    private fun syncPhysics(owned: Boolean) {
        if (asset == null) return
        val mode = when (PolyPlusCosmeticsConfig.cosmeticPhysics) {
            CosmeticPhysics.ON -> Renderer.PHYSICS_ON
            CosmeticPhysics.OWN -> if (owned) Renderer.PHYSICS_ON else Renderer.PHYSICS_OWN
            CosmeticPhysics.OFF -> Renderer.PHYSICS_OFF
        }
        val local = CosmeticWearers.localWearer
        if (mode == physicsMode && local == physicsWearer) return
        renderer.setPhysics(mode, local)
        physicsMode = mode
        physicsWearer = local
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

    private fun dropRows(wearer: Long) {
        var kept = 0
        for (i in 0 until heldCount) {
            val ints = i * HELD_INTS
            val floats = i * HELD_FLOATS
            val row = heldInts[ints]
            if (row < rows && wearerIds[row] == wearer) continue
            if (kept != i) {
                System.arraycopy(heldInts, ints, heldInts, kept * HELD_INTS, HELD_INTS)
                System.arraycopy(heldFloats, floats, heldFloats, kept * HELD_FLOATS, HELD_FLOATS)
            }
            kept++
        }
        heldCount = kept
        for (row in 0 until rows) {
            if (wearerIds[row] == wearer) rowWearers[row] = null
        }
    }

    private fun nextRow(): Int {
        val frame = CosmeticWearers.frame
        if (batchFrame != frame) {
            heldCount = 0
            anyCollected = false
            rowWearers.fill(null, 0, rows)
            batchFrame = frame
            rows = 0
            computed = 0
            quadCount = 0
        }
        ensureRows(rows + 1)
        return rows
    }

    private fun restParts(base: Int) {
        for (part in 0 until PART_COUNT) {
            val x = REST_X[part]
            val y = REST_Y[part]
            put(base, part, x, y, 0f, x, y, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        }
    }

    private fun ensureRows(needed: Int) {
        if (wearerIds.size >= needed) return
        val capacity = max(needed, wearerIds.size * 2)
        wearerIds = wearerIds.copyOf(capacity)
        rowWearers = rowWearers.copyOf(capacity)
        collectedRows = collectedRows.copyOf(capacity)
        unconfirmedRows = unconfirmedRows.copyOf(capacity)
        echoRows = echoRows.copyOf(capacity)
        input = input.copyOf(capacity * inputStride)
        out = out.copyOf(capacity * outputStride)
    }

    private fun ensureScratch(count: Int) {
        if (scratchIds.size >= count) return
        val capacity = max(count, scratchIds.size * 2)
        scratchIds = LongArray(capacity)
        scratchInput = FloatArray(capacity * inputStride)
        scratchOut = FloatArray(capacity * outputStride)
    }

    private fun restRenderer(): Renderer {
        val created = Renderer()
        restCleanable = CLEANER.register(this, created::close)
        load(created)
        animationJson?.let { created.loadAnimation(SLOT, it) }
        if (asset != null || animationJson != null) created.play(SLOT, 0f)
        rest = created
        return created
    }

    private fun load(target: Renderer) {
        if (asset != null) {
            target.loadAsset(SLOT, asset)
        } else {
            target.loadCosmetic(SLOT, geometry.orEmpty(), bodySlot, anchorName, imageWidth, imageHeight)
        }
        target.setScale(SLOT, scale)
    }

    private fun namesOf(meta: JsonObject?, vararg path: String): List<String> {
        val list = meta?.let { CosmeticBundles.array(it, *path) } ?: return emptyList()
        return list.map { if (it.isJsonPrimitive) it.asString else "" }
    }

    private fun slotIdsOf(meta: JsonObject?): List<String> {
        val list = meta?.let { CosmeticBundles.array(it, "palette", "slots") } ?: return emptyList()
        return list.map { slot ->
            slot.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        }
    }

    private fun momentPlansOf(meta: JsonObject?): Map<String, MomentPlan>? {
        val list = meta?.let { CosmeticBundles.array(it, "effects", "moments") } ?: return null
        val plans = HashMap<String, MomentPlan>()
        for (item in list) {
            val entry = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val name = entry.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val seconds = entry.get("seconds")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: continue
            val clip = entry.get("clip")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            plans[name] = MomentPlan(clip?.takeIf { it in clipLengths.indices } ?: NO_CLIP, seconds)
        }
        return plans
    }

    private fun clipSecondsOf(meta: JsonObject?): Float {
        val clips = meta?.let { CosmeticBundles.array(it, "clips") } ?: return 0f
        var longest = 0f
        for (clip in clips) {
            val entry = clip.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val loop = entry.get("loop")?.takeIf { it.isJsonPrimitive }?.asString
            if (loop == "repeat") continue
            val length = entry.get("length")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: continue
            longest = max(longest, length)
        }
        return longest
    }

    private fun sheetOffsetAt(seconds: Float): Float {
        renderer.frame(seconds, input, 0, 0, Renderer.JAVA_MODEL_SPACE, probe)
        return probe[skin.boneCount * 12]
    }

    private fun feedQueries(context: PlayerRenderContext, base: Int) {
        query(base, LIFE_TIME, context.ageInTicks / BedrockConstants.TICKS_PER_SECOND)
        query(base, DELTA_TIME, 1f / BedrockConstants.TICKS_PER_SECOND)
        val signals = context.signals
        for (i in 0 until PlayerSignals.QUERY_COUNT) query(base, FIRST_SIGNAL + i, signals.query(i))
    }

    private fun query(base: Int, query: Int, value: Float) {
        input[base + queryIds[query]] = value
    }

    private fun put(
        base: Int,
        part: Int,
        restX: Float, restY: Float, restZ: Float,
        x: Float, y: Float, z: Float,
        xRot: Float, yRot: Float, zRot: Float,
        xScale: Float, yScale: Float, zScale: Float,
    ) {
        val i = base + partIds[part] * 12
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

    // rests are the core player's pivots on vanilla's axes, where slim arms sit as high as wide ones,
    // so cosmetics follow vanilla's lower slim arms down
    //? if > 1.8.9 {
    private fun feedParts(model: PlayerModel, backOffset: Float, base: Int) {
        //? if >= 1.21.4 {
        feed(base, ROOT, model.root())
        feed(base, HAT, model.hat)
        //?} else {
        /*// the renderer already applies root, and hat copies head's pose instead of hanging from it
        feedIdentity(base, HAT)
        *///?}
        feed(base, HEAD, model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed(base, BODY, model.body, restZ = -backOffset)
        feed(base, RIGHT_ARM, model.rightArm, -ARM_X, ARM_Y)
        feed(base, LEFT_ARM, model.leftArm, ARM_X, ARM_Y)
        feed(base, RIGHT_LEG, model.rightLeg, -LEG_X, LEG_Y)
        feed(base, LEFT_LEG, model.leftLeg, LEG_X, LEG_Y)
    }

    private fun feed(base: Int, index: Int, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        put(
            base, index, restX, restY, restZ,
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale,
        )
    }
    //?} else {
    /*private fun feedParts(model: PlayerModel, backOffset: Float, base: Int) {
        val sneakOffset = if (model.sneaking && !OverflowAnimationsCompat.dropsSneakOffset()) SNEAK_OFFSET else 0f
        put(base, ROOT, 0f, 0f, 0f, 0f, sneakOffset, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        // hat copies head's pose instead of hanging from it
        feedIdentity(base, HAT)
        feed(base, HEAD, model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed(base, BODY, model.body, restZ = -backOffset)
        feed(base, RIGHT_ARM, model.rightArm, -ARM_X, ARM_Y)
        feed(base, LEFT_ARM, model.leftArm, ARM_X, ARM_Y)
        feed(base, RIGHT_LEG, model.rightLeg, -LEG_X, LEG_Y)
        feed(base, LEFT_LEG, model.leftLeg, LEG_X, LEG_Y)
    }

    private fun feed(base: Int, index: Int, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        val scale = part as ModelPartScaleAccess
        put(
            base, index, restX, restY, restZ,
            part.translateX * 16f + part.x, part.translateY * 16f + part.y, part.translateZ * 16f + part.z,
            part.rotationX, part.rotationY, part.rotationZ,
            scale.`polyplus$scaleX`(), scale.`polyplus$scaleY`(), scale.`polyplus$scaleZ`(),
        )
    }
    *///?}

    //? if <= 1.21.1 {
    /*private fun feedIdentity(base: Int, index: Int) {
        put(base, index, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
    }
    *///?}

    private fun emissive(base: Int, level: Int): Int {
        if (level == 0) return base
        val block = maxOf((base shr 4) and 0xF, level)
        val sky = maxOf((base shr 20) and 0xF, level)
        return (block shl 4) or (sky shl 20)
    }

    private class Paint(val colorway: Int, val colors: Map<String, Int>?)

    private class ClipPlay(val clip: Int, val started: Double, val until: Double)

    private class MomentPlan(val clip: Int, val seconds: Float)

    private companion object {
        const val SLOT = 0
        const val INITIAL_ROWS = 4
        const val NO_CLIP = -1
        const val BEDROCK_CLIP_PREFIX = "animation."
        const val MAX_PLAYS = 64
        const val HELD_INTS = 3
        const val HELD_FLOATS = 5
        // plusrenderer steps a sheet every 0.2 s, so this is mid second frame
        const val SECOND_SHEET_FRAME_SECONDS = 0.3f
        const val ARM_X = 5f
        const val ARM_Y = 2f
        const val LEG_X = 1.9f
        const val LEG_Y = 12f
        val CLEANER: Cleaner = Cleaner.create()

        fun closer(renderer: Renderer, asset: CosmeticAsset?): Runnable = Runnable {
            renderer.close()
            asset?.close()
        }
        val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")
        val MATRIX = Matrix4f()
        val BONE = Matrix4f()
        val POSITION = Vector3f()
        val NORMAL = Vector3f()
        //? if = 1.8.9
        //const val SNEAK_OFFSET = 3.2f

        val PARTS = arrayOf("hat", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "root")
        val REST_X = floatArrayOf(0f, 0f, 0f, -ARM_X, ARM_X, -LEG_X, LEG_X, 0f)
        val REST_Y = floatArrayOf(0f, 0f, 0f, ARM_Y, ARM_Y, LEG_Y, LEG_Y, 0f)
        const val HAT = 0
        const val HEAD = 1
        const val BODY = 2
        const val RIGHT_ARM = 3
        const val LEFT_ARM = 4
        const val RIGHT_LEG = 5
        const val LEFT_LEG = 6
        const val ROOT = 7
        //? if > 1.8.9 && <= 1.21.1 {
        /*const val PART_COUNT = ROOT
        *///?} else {
        const val PART_COUNT = ROOT + 1
        //?}

        val QUERIES = arrayOf("life_time", "delta_time") + PlayerSignals.QUERY_NAMES
        const val LIFE_TIME = 0
        const val DELTA_TIME = 1
        const val FIRST_SIGNAL = 2

        init {
            // antivirus heuristics distrust native libraries loaded from the temp dir
            if (System.getProperty("plusrenderer.natives.dir") == null) {
                System.setProperty("plusrenderer.natives.dir", File("${PolyPlusConstants.NAME}/natives").absolutePath)
            }
        }
    }
}

internal object CosmeticRows {
    fun same(input: FloatArray, first: Int, second: Int, stride: Int, eventsAt: Int, detailAt: Int): Boolean {
        for (i in 0 until stride) {
            if (i == eventsAt || i == detailAt) continue
            if (input[first + i].toRawBits() != input[second + i].toRawBits()) return false
        }
        return true
    }

    fun nearRoot(input: FloatArray, first: Int, second: Int, count: Int): Boolean {
        for (i in 0 until count) {
            val limit = if (i % 4 == 3) ROOT_SHIFT_TOLERANCE else ROOT_TURN_TOLERANCE
            if (!(abs(input[first + i] - input[second + i]) <= limit)) return false
        }
        return true
    }

    private const val ROOT_TURN_TOLERANCE = 1e-5f
    private const val ROOT_SHIFT_TOLERANCE = 1e-3f
}

private fun bodySlotName(slot: BodySlot): String = (if (slot == BodySlot.Tail) BodySlot.Backpack else slot).serializedName

internal class CustomColors(val slots: IntArray, val rgb: IntArray) {
    companion object {
        fun of(paletteSlots: List<String>, colors: Map<String, Int>?): CustomColors? {
            if (colors.isNullOrEmpty()) return null
            val slots = IntArray(colors.size)
            val rgb = IntArray(colors.size)
            var count = 0
            for ((key, value) in colors) {
                val slot = paletteSlots.indexOf(key).takeIf { it >= 0 }
                    ?: key.toIntOrNull()?.takeIf { it in paletteSlots.indices }
                    ?: continue
                slots[count] = slot
                rgb[count] = value
                count++
            }
            return if (count == 0) null else CustomColors(slots.copyOf(count), rgb.copyOf(count))
        }
    }
}

internal class CosmeticSkin(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val glow: ByteArray,
    val bones: IntArray,
    val runs: IntArray,
    val boneCount: Int,
)
