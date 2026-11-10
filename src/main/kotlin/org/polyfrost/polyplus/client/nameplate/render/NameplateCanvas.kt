package org.polyfrost.polyplus.client.nameplate.render

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialFallbacks
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import java.util.WeakHashMap
//? if >= 1.21.1 {
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialAssets
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialDraw
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialPipelines
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateShaderProgram
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateShaderStatus
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialOp
import kotlin.math.floor
import kotlin.math.ln
//?}
//? if >= 26.3 {
//?}
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyMaterialTexture
*///?}

object NameplateCanvas {
    //? if >= 1.21.1 {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    //?}

    const val ORDER: Int = -1
    const val CPU_PROPERTY: String = "polyplus.nameplate.cpuMaterials"

    val WHITE: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/nameplate/white.png")

    private const val BEHIND = -0.02f * Z_TOWARD_CAMERA
    private const val BACKGROUND = -0.01f * Z_TOWARD_CAMERA
    private const val OVER_BACKGROUND = -0.005f * Z_TOWARD_CAMERA
    private const val OVER_TEXT = 0.04f * Z_TOWARD_CAMERA
    private const val INDEX_STEP = 0.0001f * Z_TOWARD_CAMERA
    private const val MAX_INDEX = 31
    private const val STRIDE = 5
    private const val OPAQUE = 255
    //? if >= 1.21.1 {
    private const val GPU = "GPU"
    private const val FORCED = "forced"
    private const val SHADER_PACK = "shader pack"
    private const val NO_TEXTURES = "textures not uploaded"
    private const val RUN_STRIDE = BundleLimits.MAX_LAYERS * 2 + 1
    private const val BANDS_PER_DOUBLING = 4.0
    private const val MAX_BAND = 31

    private val forceCpu: Boolean = java.lang.Boolean.getBoolean(CPU_PROPERTY)
    private val LN_2 = ln(2.0)
    //?}

    private const val CENTER = 0.5f
    private const val WHITE_ARGB = -1

    private val geometry = WeakHashMap<PixelImage, IntArray>()
    private val stripGeometry = WeakHashMap<PixelImage, IntArray>()
    //? if >= 1.21.1 {
    private val clips = WeakHashMap<PixelImage, IntArray>()
    //?}
    private val texels = IntArray(PixelImage.MAX_STRIP_PERIOD)
    private val colors = ColorSink()
    //? if >= 1.21.1 {
    private val modes = WeakHashMap<MaterialLayer, String>()

    private sealed interface Run

    private class CanvasRun(val ops: List<LayerPainter.Painted>) : Run

    private class MaterialRun(
        val op: LayerPainter.Painted,
        val material: MaterialOp,
        val program: NameplateShaderProgram,
        val textures: List<Identifier>,
    ) : Run
    //?}

    fun submit(
        frame: NameplateFrame,
        bundle: NameplateBundle,
        ops: List<LayerPainter.Painted>,
        palette: ResolvedPalette,
        onError: (Throwable) -> Unit,
    ) {
        if (ops.none { it.material != null }) {
            submitCanvas(frame, ops, palette, ORDER, onError)
            return
        }

        //? if >= 1.21.1 {
        val drawn = ArrayList(ops)
        val runs = ArrayList<Run>()
        var start = 0
        for ((i, op) in ops.withIndex()) {
            val material = op.material ?: continue
            val run = materialRun(bundle, op, material)
            if (run == null) {
                drawn[i] = NameplateMaterialFallbacks.painted(bundle, op, material)
                continue
            }
            if (i > start) {
                runs += CanvasRun(drawn.subList(start, i))
            }
            runs += run
            start = i + 1
        }
        if (start < ops.size) {
            runs += CanvasRun(drawn.subList(start, ops.size))
        }

        val deferred = runs.size > 1 && NameplateOrdering.defer(frame, runs.size) { target, last ->
            try {
                submitRuns(target, bundle, runs, palette, last, onError)
            } catch (e: RuntimeException) {
                onError(e)
            }
        }
        if (!deferred) {
            val last = if (runs.size > 1) ORDER - RUN_STRIDE * (1 + band(frame.cameraDistance)) else ORDER
            submitRuns(frame, bundle, runs, palette, last, onError)
        }
        //?} elif > 1.8.9 {
        /*submitCanvas(frame, ops.map { op -> op.material?.let { NameplateMaterialFallbacks.painted(bundle, op, it) } ?: op }, palette, ORDER, onError)
        *///?} else {
        /*LegacyMaterialTexture.submit(frame, bundle, ops, palette) { run -> submitCanvas(frame, run, palette, ORDER, onError) }
        *///?}
    }

    //? if >= 1.21.1 {
    private fun submitRuns(
        frame: NameplateFrame,
        bundle: NameplateBundle,
        runs: List<Run>,
        palette: ResolvedPalette,
        last: Int,
        onError: (Throwable) -> Unit,
    ) {
        for ((k, run) in runs.withIndex()) {
            val order = last - (runs.size - 1 - k)
            when (run) {
                is CanvasRun -> submitCanvas(frame, run.ops, palette, order, onError)
                is MaterialRun -> if (!submitMaterial(frame, run, palette, order)) {
                    submitCanvas(frame, listOf(NameplateMaterialFallbacks.painted(bundle, run.op, run.material)), palette, order, onError)
                }
            }
        }
    }

    private fun band(distance: Float): Int =
        floor(ln(1.0 + distance) / LN_2 * BANDS_PER_DOUBLING).toInt().coerceIn(0, MAX_BAND)
    //?}

    internal fun depth(op: LayerPainter.Painted): Float = depth(op.depth) + minOf(op.index, MAX_INDEX) * INDEX_STEP

    //? if >= 1.21.1 {
    private fun materialRun(bundle: NameplateBundle, op: LayerPainter.Painted, material: MaterialOp): MaterialRun? {
        if (op.opacity <= 0) {
            return null
        }
        val layer = material.layer
        val program = NameplateMaterialAssets.program(layer)
        val status = NameplateMaterialPipelines.status(program.hash)
        val pack = !NameplateIris.gpuMaterials()
        val textures = if (!forceCpu && !pack && status == NameplateShaderStatus.READY) NameplateMaterialAssets.textures(bundle, layer) else null
        val mode = when {
            textures != null -> GPU
            forceCpu -> FORCED
            pack -> SHADER_PACK
            status != NameplateShaderStatus.READY -> status?.name ?: NameplateShaderStatus.PENDING.name
            else -> NO_TEXTURES
        }
        if (modes[layer] != mode) {
            modes[layer] = mode
            if (textures != null) {
                LOGGER.info("Nameplate {} layer {} draws on the GPU (pipeline {})", bundle.id, material.layerId, program.hash)
            } else {
                LOGGER.info("Nameplate {} layer {} draws on the CPU fallback (pipeline {}: {})", bundle.id, material.layerId, program.hash, mode)
            }
        }
        return textures?.let { MaterialRun(op, material, program, it) }
    }

    private fun submitMaterial(frame: NameplateFrame, run: MaterialRun, palette: ResolvedPalette, order: Int): Boolean {
        val op = run.op
        val material = run.material
        val clip = op.clip
        val area = material.area
        val local = if (clip == null) {
            area
        } else {
            PixelRect(maxOf(area.x0, clip.x0), maxOf(area.y0, clip.y0), minOf(area.x1, clip.x1), minOf(area.y1, clip.y1))
        }
        if (local.width <= 0 || local.height <= 0) {
            return true
        }
        val screen = if (op.mirrorX) {
            PixelRect(op.x + op.width - local.x1, op.y + local.y0, op.x + op.width - local.x0, op.y + local.y1)
        } else {
            PixelRect(op.x + local.x0, op.y + local.y0, op.x + local.x1, op.y + local.y1)
        }
        return NameplateMaterialDraw.submit(
            frame,
            run.program,
            screen,
            local,
            op.mirrorX,
            depth(op),
            material.clock,
            material.uniforms(palette, NameplateMaterialAssets.atlasRects(material.layer)),
            run.textures,
            op.opacity,
            order,
            material.clip?.let { region -> clips.getOrPut(region) { material.clipRects() } },
        )
    }
    //?}

    private fun submitCanvas(
        frame: NameplateFrame,
        ops: List<LayerPainter.Painted>,
        palette: ResolvedPalette,
        order: Int,
        onError: (Throwable) -> Unit,
    ) {
        if (ops.none { it.image.rectCount > 0 && it.opacity > 0 }) {
            return
        }

        val light = frame.lightCoords
        val alpha = frame.alpha
        val now = System.nanoTime()
        NameplateStrips.sweep(now)
        val rows = stripRows(ops, palette, now)
        val renderType = if (rows == null) NameplateLayers.text(WHITE) else NameplateStrips.renderType()
        val renderer = NameplateGeometry { pose, buffer ->
            try {
                for ((k, op) in ops.withIndex()) {
                    draw(buffer, pose, op, palette, light, alpha, rows, rows?.get(k))
                }
            } catch (e: RuntimeException) {
                onError(e)
            }
        }
        //? if >= 26.3 {
        if (order == ORDER && NameplateOrdering.share(frame, renderType, renderer)) {
            return
        }
        //?}
        frame.submitGeometry(order, renderType, renderer)
    }

    private fun stripRows(ops: List<LayerPainter.Painted>, palette: ResolvedPalette, now: Long): Array<IntArray?>? {
        if (ops.none { it.image.stripCount > 0 && it.opacity > 0 }) {
            return null
        }
        val rows = Array(ops.size) { k ->
            val image = ops[k].image
            if (image.stripCount == 0 || ops[k].opacity <= 0) {
                null
            } else {
                IntArray(image.stripCount) { strip ->
                    val period = if (image.usesPalette) image.stripColors(strip, palette, texels) else image.stripColors(strip, texels)
                    for (t in 0 until period) {
                        texels[t] = PortraitGuard.vertex(texels[t])
                    }
                    NameplateStrips.row(texels, period, now)
                }
            }
        }
        return rows.takeIf { all -> all.any { it != null && it.any { row -> row >= 0 } } }
    }

    private fun draw(
        buffer: NameplateVertexSink,
        pose: NameplatePose,
        op: LayerPainter.Painted,
        palette: ResolvedPalette,
        light: Int,
        alpha: Float,
        atlas: Array<IntArray?>?,
        rows: IntArray?,
    ) {
        val image = op.image
        if (image.rectCount == 0 || op.opacity <= 0) {
            return
        }
        val u = if (atlas == null) CENTER else NameplateStrips.WHITE_U
        val v = if (atlas == null) CENTER else NameplateStrips.WHITE_V

        val rects = geometry.getOrPut(image) { if (image.usesPalette) image.resolve(palette) else image.resolve() }
        colors.reset(image.rectCount)
        if (image.usesPalette) {
            image.sample(palette, Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, colors)
        } else {
            image.sample(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, colors)
        }

        val z = depth(op)
        val opacity = op.opacity
        val clip = op.clip
        for (r in 0 until image.rectCount) {
            if (rows != null) {
                val strip = image.stripOf(r)
                if (strip >= 0 && rows[strip] >= 0) {
                    continue
                }
            }
            var argb = colors.at(r)
            if (opacity < OPAQUE) {
                argb = NameplatePaint.scaleAlpha(argb, opacity)
            }
            if (alpha < 1f) {
                argb = NameplateFrame.fade(argb, alpha)
            }
            if (argb ushr 24 == 0) {
                continue
            }

            val i = r * STRIDE
            var left = rects[i]
            var top = rects[i + 1]
            var right = rects[i + 2]
            var bottom = rects[i + 3]
            if (clip != null) {
                left = maxOf(left, clip.x0)
                top = maxOf(top, clip.y0)
                right = minOf(right, clip.x1)
                bottom = minOf(bottom, clip.y1)
                if (right <= left || bottom <= top) {
                    continue
                }
            }
            val x0: Float
            val x1: Float
            if (op.mirrorX) {
                x0 = (op.x + op.width - right).toFloat()
                x1 = (op.x + op.width - left).toFloat()
            } else {
                x0 = (op.x + left).toFloat()
                x1 = (op.x + right).toFloat()
            }
            val y0 = (op.y + top).toFloat()
            val y1 = (op.y + bottom).toFloat()
            quad(buffer, pose, x0, y0, x1, y1, z, u, u, v, light, PortraitGuard.vertex(argb))
        }
        if (rows != null) {
            drawStrips(buffer, pose, op, rows, z, light, alpha)
        }
    }

    private fun drawStrips(buffer: NameplateVertexSink, pose: NameplatePose, op: LayerPainter.Painted, rows: IntArray, z: Float, light: Int, alpha: Float) {
        var color = NameplatePaint.scaleAlpha(WHITE_ARGB, op.opacity)
        if (alpha < 1f) {
            color = NameplateFrame.fade(color, alpha)
        }
        if (color ushr 24 == 0) {
            return
        }
        val strips = stripGeometry.getOrPut(op.image) { op.image.copyStrips() }
        val clip = op.clip
        for (s in rows.indices) {
            val row = rows[s]
            if (row < 0) {
                continue
            }
            val i = s * PixelImage.STRIP_STRIDE
            val start = strips[i]
            var left = start
            var top = strips[i + 1]
            var right = strips[i + 2]
            var bottom = strips[i + 3]
            if (clip != null) {
                left = maxOf(left, clip.x0)
                top = maxOf(top, clip.y0)
                right = minOf(right, clip.x1)
                bottom = minOf(bottom, clip.y1)
                if (right <= left || bottom <= top) {
                    continue
                }
            }
            val x0: Float
            val x1: Float
            val u0: Float
            val u1: Float
            if (op.mirrorX) {
                x0 = (op.x + op.width - right).toFloat()
                x1 = (op.x + op.width - left).toFloat()
                u0 = (right - start).toFloat() / NameplateStrips.WIDTH
                u1 = (left - start).toFloat() / NameplateStrips.WIDTH
            } else {
                x0 = (op.x + left).toFloat()
                x1 = (op.x + right).toFloat()
                u0 = (left - start).toFloat() / NameplateStrips.WIDTH
                u1 = (right - start).toFloat() / NameplateStrips.WIDTH
            }
            val y0 = (op.y + top).toFloat()
            val y1 = (op.y + bottom).toFloat()
            quad(buffer, pose, x0, y0, x1, y1, z, u0, u1, NameplateStrips.v(row), light, color)
        }
    }

    private fun depth(depth: NameplateBundle.Depth): Float = when (depth) {
        NameplateBundle.Depth.BEHIND -> BEHIND
        NameplateBundle.Depth.BACKGROUND -> BACKGROUND
        NameplateBundle.Depth.OVER_BACKGROUND -> OVER_BACKGROUND
        NameplateBundle.Depth.OVER_TEXT -> OVER_TEXT
    }

    private fun quad(
        buffer: NameplateVertexSink,
        pose: NameplatePose,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        z: Float,
        u0: Float,
        u1: Float,
        v: Float,
        light: Int,
        color: Int,
    ) {
        Vertices.add(buffer, pose, x0, y1, z).setUv(u0, v).setLight(light).setColor(color)
        Vertices.add(buffer, pose, x1, y1, z).setUv(u1, v).setLight(light).setColor(color)
        Vertices.add(buffer, pose, x1, y0, z).setUv(u1, v).setLight(light).setColor(color)
        Vertices.add(buffer, pose, x0, y0, z).setUv(u0, v).setLight(light).setColor(color)
    }

    private class ColorSink : (Int) -> Unit {
        private var values = IntArray(64)
        private var count = 0

        fun reset(capacity: Int) {
            if (values.size < capacity) {
                values = IntArray(maxOf(capacity, values.size * 2))
            }
            count = 0
        }

        fun at(index: Int): Int = values[index]

        override fun invoke(argb: Int) {
            values[count++] = argb
        }
    }
}
