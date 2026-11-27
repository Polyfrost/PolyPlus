package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import org.polyfrost.polyplus.client.cosmetics.render.PooledGeometry
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.polyfrost.polyplus.client.nameplate.engine.NameplateAtlas
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BADGE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BEHIND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_TEXT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_FLAGS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_U1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_V1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_RUN_AT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MAX_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_ARGB
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_ADDITIVE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.RUN_RECT
import kotlin.math.roundToInt
import org.polyfrost.polyplus.client.nameplate.material.NameplateMaterialBackend as MaterialDraws
//? if >= 26.3 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} elif >= 1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
*///?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}

internal class NameTagCanvasEntry : PooledGeometry {
    val matrix = Matrix4f()
    var ops = IntArray(OP_WORDS * 8)
        private set
    var opCount = 0
        private set
    var lightCoords = 0
    var alpha = 1f

    fun copyOps(list: NameplateDrawList, from: Int, to: Int) {
        val words = (to - from) * OP_WORDS
        if (ops.size < words) ops = IntArray(maxOf(words, ops.size * 2))
        System.arraycopy(list.ops, from * OP_WORDS, ops, 0, words)
        opCount = to - from
    }

    override fun render(pose: PoseStack.Pose, buffer: VertexConsumer) {
        NameTagCanvas.emit(pose.pose(), this, buffer)
    }
}

internal class NameTagImageEntry : PooledGeometry {
    val matrix = Matrix4f()
    var images = IntArray(IMAGE_WORDS * 2)
        private set
    var imageCount = 0
        private set
    var lightCoords = 0
    var alpha = 1f

    fun copyImages(list: NameplateDrawList, from: Int, to: Int) {
        val words = (to - from) * IMAGE_WORDS
        if (images.size < words) images = IntArray(maxOf(words, images.size * 2))
        System.arraycopy(list.images, from * IMAGE_WORDS, images, 0, words)
        imageCount = to - from
    }

    override fun render(pose: PoseStack.Pose, buffer: VertexConsumer) {
        NameTagCanvas.emitImages(pose.pose(), this, buffer)
    }
}

object NameTagCanvas {
    const val ORDER: Int = -1
    private const val DEPTH_ORDER = -1_000_000

    private const val LAYER_STEP = 0.0001f
    private const val BEHIND = -0.02f
    private const val BACKGROUND = -0.01f
    private const val OVER_BACKGROUND = -0.005f
    private const val OVER_TEXT = 0.04f
    private const val BADGE = 0.03f

    private val entries = ArrayList<NameTagCanvasEntry>()
    private var used = 0
    private val imageEntries = ArrayList<NameTagImageEntry>()
    private var imagesUsed = 0
    private val pose = PoseStack()

    private var frameCollector: SubmitNodeCollector? = null
    //? if >= 26.3 {
    private const val EFFECT_DEPTH = -0.01f
    private const val EFFECT_SLOTS = 512
    private const val EFFECT_KEY_WORDS = 5

    private val batches = ArrayList<Batch>()
    private var batchesUsed = 0
    private var batch: Batch? = null
    private val emitMatrix = Matrix4f()
    private var frameType: RenderType? = null
    private val effectKeys = IntArray(EFFECT_SLOTS * EFFECT_KEY_WORDS)
    private val effects = arrayOfNulls<TextRenderable>(EFFECT_SLOTS)
    private var effectProbe: TextRenderable? = null
    //?}

    @JvmStatic
    fun begin(collector: SubmitNodeCollector) {
        frameCollector = collector
        //? if >= 26.3 {
        batch = null
        frameType = null
        //?}
    }

    @JvmStatic
    fun flush() {
        NameTags.flushTags()
        val collector = frameCollector
        frameCollector = null
        //? if >= 26.3 {
        val pending = batch
        batch = null
        if (collector != null && pending != null && pending.size > 0) {
            pose.setIdentity()
            collector.order(ORDER).submitCustomGeometry(pose, renderType(), pending)
        }
        //?}
    }

    @JvmStatic
    fun endFrame() {
        NameTags.endFrame()
        MaterialDraws.endFrame()
        NameplateDrawSources.current.endFrame()
        frameCollector = null
        used = 0
        imagesUsed = 0
        //? if >= 26.3 {
        for (i in 0 until batchesUsed) batches[i].clear()
        batchesUsed = 0
        batch = null
        frameType = null
        //?}
    }

    internal fun inFrame(collector: SubmitNodeCollector): Boolean = frameCollector != null

    internal fun orderSpan(list: NameplateDrawList): Int = maxOf(1, list.runCount + list.materialCount)

    internal fun submit(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        lightCoords: Int,
        alpha: Float,
        list: NameplateDrawList,
        end: Int = ORDER,
    ) {
        if (alpha <= 0f) return
        var order = end - orderSpan(list) + 1
        var run = 0
        for (k in 0 until list.materialCount) {
            val runTo = list.materialWord(k, MATERIAL_RUN_AT)
            while (run < runTo) submitRun(collector, matrix, x, y, lightCoords, alpha, list, run++, order++)
            submitMaterial(collector, matrix, x, y, lightCoords, alpha, list, k, order++)
        }
        while (run < list.runCount) submitRun(collector, matrix, x, y, lightCoords, alpha, list, run++, order++)
    }

    private fun submitRun(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        lightCoords: Int,
        alpha: Float,
        list: NameplateDrawList,
        run: Int,
        order: Int,
    ) {
        val from = list.runFrom(run)
        val to = list.runTo(run)
        when (list.runKind(run)) {
            RUN_RECT -> submitRects(collector, matrix, x, y, lightCoords, alpha, list, from, to, order)
            RUN_ADDITIVE -> submitImages(collector, matrix, x, y, lightCoords, alpha, list, from, to, true, order)
            else -> submitImages(collector, matrix, x, y, lightCoords, alpha, list, from, to, false, order)
        }
    }

    private fun submitRects(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        lightCoords: Int,
        alpha: Float,
        list: NameplateDrawList,
        opFrom: Int,
        opTo: Int,
        order: Int,
    ) {
        if (opTo <= opFrom) return
        val entry = entry()
        entry.matrix.set(matrix).translate(x, y, 0f)
        entry.copyOps(list, opFrom, opTo)
        entry.lightCoords = lightCoords
        entry.alpha = alpha

        //? if >= 26.3 {
        if (collector === frameCollector && order == ORDER) {
            val pending = batch ?: nextBatch().also { batch = it }
            pending.add(entry)
            return
        }
        //?}
        pose.last().pose().set(entry.matrix)
        collector.order(order).submitCustomGeometry(pose, renderType(), entry)
    }

    private fun submitImages(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        lightCoords: Int,
        alpha: Float,
        list: NameplateDrawList,
        from: Int,
        to: Int,
        additive: Boolean,
        order: Int,
    ) {
        if (to <= from) return
        if (imagesUsed == imageEntries.size) imageEntries += NameTagImageEntry()
        val entry = imageEntries[imagesUsed++]
        entry.copyImages(list, from, to)
        entry.matrix.set(matrix).translate(x, y, 0f)
        entry.lightCoords = lightCoords
        entry.alpha = alpha
        pose.last().pose().set(entry.matrix)
        collector.order(order).submitCustomGeometry(pose, if (additive) additiveImageRenderType() else imageRenderType(), entry)
    }

    private fun submitMaterial(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        lightCoords: Int,
        alpha: Float,
        list: NameplateDrawList,
        index: Int,
        order: Int,
    ) {
        pose.last().pose().set(matrix)
        MaterialDraws.submit(collector, order, DEPTH_ORDER, pose, list, index, x, y, depth(list.materialWord(index, MATERIAL_BAND_LAYER)), lightCoords, alpha)
    }

    internal fun emit(matrix: Matrix4f, entry: NameTagCanvasEntry, buffer: VertexConsumer) {
        val ops = entry.ops
        for (op in 0 until entry.opCount) {
            val at = op * OP_WORDS
            val word = ops[at + OP_BAND_LAYER]
            val argb = fade(ops[at + OP_ARGB], entry.alpha)
            if (argb ushr 24 == 0) continue
            quad(
                matrix,
                buffer,
                ops[at + OP_X0].toFloat(),
                ops[at + OP_Y0].toFloat(),
                ops[at + OP_X1].toFloat(),
                ops[at + OP_Y1].toFloat(),
                depth(word),
                argb,
                entry.lightCoords,
            )
        }
    }

    internal fun emitImages(matrix: Matrix4f, entry: NameTagImageEntry, buffer: VertexConsumer) {
        val images = entry.images
        val size = NameplateAtlas.size.toFloat()
        for (i in 0 until entry.imageCount) {
            val at = i * IMAGE_WORDS
            val argb = NameplateDrawList.imageColor(images[at + IMAGE_FLAGS], images[at + OP_ARGB], entry.alpha)
            if (argb ushr 24 == 0) continue
            val x0 = images[at + OP_X0].toFloat()
            val y0 = images[at + OP_Y0].toFloat()
            val x1 = images[at + OP_X1].toFloat()
            val y1 = images[at + OP_Y1].toFloat()
            val u0 = images[at + IMAGE_U0] / size
            val v0 = images[at + IMAGE_V0] / size
            val u1 = images[at + IMAGE_U1] / size
            val v1 = images[at + IMAGE_V1] / size
            val z = depth(images[at + OP_BAND_LAYER])
            val light = entry.lightCoords
            buffer.addVertex(matrix, x0, y1, z).setColor(argb).setUv(u0, v1).setLight(light)
            buffer.addVertex(matrix, x1, y1, z).setColor(argb).setUv(u1, v1).setLight(light)
            buffer.addVertex(matrix, x1, y0, z).setColor(argb).setUv(u1, v0).setLight(light)
            buffer.addVertex(matrix, x0, y0, z).setColor(argb).setUv(u0, v0).setLight(light)
        }
    }

    private fun entry(): NameTagCanvasEntry {
        if (used == entries.size) entries += NameTagCanvasEntry()
        return entries[used++]
    }

    private fun depth(word: Int): Float {
        val band = when (word and 0xFF) {
            BAND_BEHIND -> BEHIND
            BAND_BACKGROUND -> BACKGROUND
            BAND_OVER_BACKGROUND -> OVER_BACKGROUND
            BAND_OVER_TEXT -> OVER_TEXT
            BAND_BADGE -> BADGE
            else -> BACKGROUND
        }
        return band + minOf((word shr 8) and 0xFF, MAX_LAYER) * LAYER_STEP
    }

    private fun fade(argb: Int, alpha: Float): Int {
        if (alpha >= 1f) return argb
        val a = ((argb ushr 24) * alpha).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    //? if >= 1.21.11 {
    private fun imageRenderType(): RenderType = RenderTypes.text(NameplateAtlas.ID)
    //?} else {
    /*private fun imageRenderType(): RenderType = RenderType.text(NameplateAtlas.ID)
    *///?}

    private fun additiveImageRenderType(): RenderType = MaterialDraws.additiveImages

    //? if >= 26.3 {
    private fun renderType(): RenderType = frameType ?: probeRenderType().also { frameType = it }

    private fun probeRenderType(): RenderType {
        val probe = Minecraft.getInstance().font.prepareBackground(0f, 0f, 0f, 0f, 0)
        if (probe != effectProbe) {
            effects.fill(null)
            effectProbe = probe
        }
        return probe.renderType(Font.DisplayMode.NORMAL)
    }

    private fun quad(matrix: Matrix4f, buffer: VertexConsumer, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, argb: Int, light: Int) {
        emitMatrix.set(matrix).translate(0f, 0f, z - EFFECT_DEPTH)
        effect(x0, y0, x1, y1, argb).render(emitMatrix, buffer, light, false)
    }

    private fun effect(x0: Float, y0: Float, x1: Float, y1: Float, argb: Int): TextRenderable {
        val a = x0.toRawBits()
        val b = y0.toRawBits()
        val c = x1.toRawBits()
        val d = y1.toRawBits()
        var hash = (((a * 31 + b) * 31 + c) * 31 + d) * 31 + argb
        hash = hash xor (hash ushr 15)
        hash = (hash * -0x61c88647) ushr 23
        val slot = hash and (EFFECT_SLOTS - 1)
        val key = slot * EFFECT_KEY_WORDS
        val hit = effects[slot]
        if (hit != null && effectKeys[key] == a && effectKeys[key + 1] == b && effectKeys[key + 2] == c && effectKeys[key + 3] == d && effectKeys[key + 4] == argb) return hit
        val created = Minecraft.getInstance().font.prepareBackground(x0, y0, x1, y1, argb)
        effectKeys[key] = a
        effectKeys[key + 1] = b
        effectKeys[key + 2] = c
        effectKeys[key + 3] = d
        effectKeys[key + 4] = argb
        effects[slot] = created
        return created
    }

    private fun nextBatch(): Batch {
        if (batchesUsed == batches.size) batches += Batch()
        return batches[batchesUsed++]
    }

    private class Batch : PooledGeometry {
        private val members = ArrayList<NameTagCanvasEntry>()

        val size: Int
            get() = members.size

        fun add(entry: NameTagCanvasEntry) {
            members += entry
        }

        fun clear() {
            members.clear()
        }

        override fun render(pose: PoseStack.Pose, buffer: VertexConsumer) {
            for (i in members.indices) {
                val entry = members[i]
                emit(entry.matrix, entry, buffer)
            }
        }
    }
    //?} else {
    /*private fun renderType(): RenderType =
        //? if >= 1.21.11 {
        RenderTypes.textBackground()
        //?} else {
        /^RenderType.textBackground()
        ^///?}

    private fun quad(matrix: Matrix4f, buffer: VertexConsumer, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, argb: Int, light: Int) {
        buffer.addVertex(matrix, x0, y1, z).setColor(argb).setLight(light)
        buffer.addVertex(matrix, x1, y1, z).setColor(argb).setLight(light)
        buffer.addVertex(matrix, x1, y0, z).setColor(argb).setLight(light)
        buffer.addVertex(matrix, x0, y0, z).setColor(argb).setLight(light)
    }
    *///?}
}
//?}
