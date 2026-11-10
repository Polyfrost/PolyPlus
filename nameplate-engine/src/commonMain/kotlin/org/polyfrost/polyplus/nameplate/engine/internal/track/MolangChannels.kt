package org.polyfrost.polyplus.nameplate.engine.internal.track

import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangRuntime
import org.polyfrost.polyplus.molang.MolangValue
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.query.MolangUse
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import kotlin.math.abs

internal object ChannelValue {
    const val FRAME = 0
    const val POSITION = 1
    const val OPACITY = 2
    const val VISIBLE = 3

    private const val OPACITY_UNITS = 255.0

    fun convert(kind: Int, value: Double): Int = when (kind) {
        POSITION -> MolangValue.snap(value).coerceIn(-BundleLimits.MAX_OFFSET, BundleLimits.MAX_OFFSET)
        OPACITY -> MolangValue.snap(value * OPACITY_UNITS).coerceIn(0, LayerPose.OPAQUE)
        VISIBLE -> if (value != 0.0 && !value.isNaN()) 1 else 0
        else -> MolangValue.snap(value).coerceIn(0, BundleLimits.MAX_SPRITE_FRAMES - 1)
    }

    fun positionReach(value: Double): Double = abs(MolangValue.snap(value).toDouble())

    fun neutral(kind: Int): Int = when (kind) {
        OPACITY -> LayerPose.OPAQUE
        VISIBLE -> 1
        else -> 0
    }
}

internal class BundleMolang(params: Collection<String>) {
    val channelEnvironment: MolangEnvironment = NameplateQueries.environment(MolangUse.CHANNEL, params)
    val slotEnvironment: MolangEnvironment = NameplateQueries.environment(MolangUse.SLOT_COLOR, params)

    private val channels = ArrayList<MolangValue>()
    private val kinds = ArrayList<Int>()
    private val slots = ArrayList<MolangValue>()

    val channelCount: Int
        get() = channels.size

    val slotCount: Int
        get() = slots.size

    val isEmpty: Boolean
        get() = channels.isEmpty() && slots.isEmpty()

    val cost: Int
        get() = channels.sumOf { it.cost } + slots.sumOf { it.cost }

    fun addChannel(value: MolangValue, kind: Int): Int {
        channels.add(value)
        kinds.add(kind)
        return channels.size - 1
    }

    fun addSlot(value: MolangValue): Int {
        slots.add(value)
        return slots.size - 1
    }

    fun channel(index: Int): MolangValue = channels[index]

    fun kind(index: Int): Int = kinds[index]

    fun slot(index: Int): MolangValue = slots[index]

    companion object {
        fun of(manifest: ManifestJson): BundleMolang = BundleMolang(paramNames(manifest))

        fun channelEnvironment(manifest: ManifestJson): MolangEnvironment =
            NameplateQueries.environment(MolangUse.CHANNEL, paramNames(manifest))

        fun slotEnvironment(manifest: ManifestJson): MolangEnvironment =
            NameplateQueries.environment(MolangUse.SLOT_COLOR, paramNames(manifest))

        private fun paramNames(manifest: ManifestJson): Set<String> {
            val names = LinkedHashSet(manifest.params.keys)
            for (variant in manifest.variants.values) {
                names.addAll(variant.params.keys)
            }
            return names
        }
    }
}

internal class MolangFrame(private val molang: BundleMolang, private val loop: Int, private val fps: Int) {
    private val channelRuntime = MolangRuntime(molang.channelEnvironment)
    private val slotRuntime = MolangRuntime(molang.slotEnvironment)
    private val channelValues = DoubleArray(molang.channelCount)
    private val channelStamps = IntArray(molang.channelCount)
    private val slotValues = DoubleArray(molang.slotCount)
    private val slotStamps = IntArray(molang.slotCount)
    private var generation = 0
    private var params: Map<String, Double>? = null

    val failed: Boolean
        get() = channelRuntime.failed || slotRuntime.failed

    fun bind(params: Map<String, Double>, block: TagBlock?, frame: Int) {
        if (generation == Int.MAX_VALUE) {
            channelStamps.fill(0)
            slotStamps.fill(0)
            generation = 0
        }
        generation++
        channelRuntime.startFrame()
        slotRuntime.startFrame()
        if (params !== this.params) {
            NameplateQueries.bindParams(channelRuntime, params)
            NameplateQueries.bindParams(slotRuntime, params)
            this.params = params
        }
        val shown = block ?: NO_BLOCK
        NameplateQueries.bindBlock(channelRuntime, shown)
        NameplateQueries.bindBlock(slotRuntime, shown)
        val local = frame.mod(loop)
        NameplateQueries.bindFrame(channelRuntime, local, loop, fps)
        NameplateQueries.bindFrame(slotRuntime, local, loop, fps)
    }

    fun channel(index: Int): Double {
        if (channelStamps[index] != generation) {
            channelValues[index] = molang.channel(index).evaluate(channelRuntime)
            channelStamps[index] = generation
        }
        return channelValues[index]
    }

    fun slot(index: Int): Double {
        if (slotStamps[index] != generation) {
            slotValues[index] = molang.slot(index).evaluate(slotRuntime)
            slotStamps[index] = generation
        }
        return slotValues[index]
    }

    private companion object {
        val NO_BLOCK: TagBlock = TagBlock.line(0, 0, 0)
    }
}

internal class MolangBounds private constructor(private val low: IntArray, private val high: IntArray) {
    fun min(index: Int): Int = low[index]

    fun max(index: Int): Int = high[index]

    companion object {
        fun of(bundleMolang: BundleMolang?, loop: Int, fps: Int, params: Map<String, Double>, block: TagBlock): MolangBounds? {
            val molang = bundleMolang?.takeIf { it.channelCount > 0 } ?: return null
            val count = molang.channelCount
            val low = IntArray(count) { Int.MAX_VALUE }
            val high = IntArray(count) { Int.MIN_VALUE }
            val frame = MolangFrame(molang, loop, fps)
            for (at in 0 until loop) {
                frame.bind(params, block, at)
                for (i in 0 until count) {
                    val value = ChannelValue.convert(molang.kind(i), frame.channel(i))
                    low[i] = minOf(low[i], value)
                    high[i] = maxOf(high[i], value)
                }
            }
            return MolangBounds(low, high)
        }
    }
}
