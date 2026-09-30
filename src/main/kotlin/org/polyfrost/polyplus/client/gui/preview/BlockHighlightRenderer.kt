package org.polyfrost.polyplus.client.gui.preview

import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Depth
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Faces
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object BlockHighlightRenderer {
    fun faceTextures(preset: String): List<String> {
        fun block(name: String) = "textures/block/$name.png"
        return when (preset) {
            "classic" -> List(6) { block("oak_planks") }
            "fancy" -> listOf("dark_oak_log_top", "dark_oak_log_top").map(::block) + List(4) { block("dark_oak_log") }
            "sweat" -> listOf(
                "smithing_table_top", "smithing_table_bottom",
                "smithing_table_front", "smithing_table_front",
                "smithing_table_side", "smithing_table_side",
            ).map(::block)
            "trans" -> List(6) { block("amethyst_block") }
            else -> List(6) { block("cobblestone") }
        }
    }

    private class Face(val axis: Int, val sign: Int, val shade: Float, val tl: Int, val tr: Int, val bl: Int)

    private fun corner(x: Int, y: Int, z: Int) = x or (y shl 1) or (z shl 2)

    private val FACES = listOf(
        Face(1, 1, 1.0f, corner(0, 1, 0), corner(1, 1, 0), corner(0, 1, 1)),
        Face(1, 0, 0.5f, corner(0, 0, 1), corner(1, 0, 1), corner(0, 0, 0)),
        Face(2, 0, 0.8f, corner(1, 1, 0), corner(0, 1, 0), corner(1, 0, 0)),
        Face(2, 1, 0.8f, corner(0, 1, 1), corner(1, 1, 1), corner(0, 0, 1)),
        Face(0, 0, 0.6f, corner(0, 1, 0), corner(0, 1, 1), corner(0, 0, 0)),
        Face(0, 1, 0.6f, corner(1, 1, 1), corner(1, 1, 0), corner(1, 0, 1)),
    )

    private fun faceIndex(axis: Int, sign: Int) = FACES.indexOfFirst { it.axis == axis && it.sign == sign }

    private fun bit(corner: Int, axis: Int) = (corner shr axis) and 1

    private class Edge(val a: Int, val b: Int, val faces: List<Int>)

    private val EDGES = (0 until 8).flatMap { a ->
        (0 until 3).filter { bit(a, it) == 0 }.map { axis ->
            val others = (0 until 3).filter { it != axis }
            Edge(a, a or (1 shl axis), others.map { faceIndex(it, bit(a, it)) })
        }
    }

    private fun gradientT(corner: Int) = sqrt((bit(corner, 0) + bit(corner, 1) + bit(corner, 2)).toFloat() / 3f)

    private class View(yaw: Float, pitch: Float, val cx: Float, val cy: Float, val unit: Float) {
        private val cy0 = cos(yaw)
        private val sy0 = sin(yaw)
        private val cp = cos(pitch)
        private val sp = sin(pitch)

        private fun rotate(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val x1 = x * cy0 + z * sy0
            val z1 = -x * sy0 + z * cy0
            return Triple(x1, y * cp - z1 * sp, y * sp + z1 * cp)
        }

        fun project(corner: Int, scale: Float): Point {
            val h = scale / 2f
            val (x, y, _) = rotate(
                if (bit(corner, 0) == 1) h else -h,
                if (bit(corner, 1) == 1) h else -h,
                if (bit(corner, 2) == 1) h else -h,
            )
            return Point(cx + x * unit, cy - y * unit)
        }

        fun facing(face: Face): Float {
            val s = if (face.sign == 1) 1f else -1f
            return when (face.axis) {
                0 -> rotate(s, 0f, 0f)
                1 -> rotate(0f, s, 0f)
                else -> rotate(0f, 0f, s)
            }.third
        }
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        style: BlockHighlightStyle,
        textures: List<Image?>,
        millis: Long,
        yaw: Float,
        pitch: Float = 0.5f,
    ) {
        val view = View(yaw, pitch, width / 2f, height / 2f, minOf(width, height) * 0.46f)
        val facing = FACES.map { view.facing(it) }
        val front = facing.map { it > 0f }

        FACES.forEachIndexed { i, face -> if (front[i]) drawBlockFace(canvas, view, face, textures.getOrNull(i)) }

        val lookedAt = facing.indices.maxBy { facing[it] }

        style.fill?.let { fill ->
            val faces = faceSet(fill.faces, lookedAt)
            val (c1, c2) = fill.colors.at(millis)
            for (back in listOf(true, false)) {
                if (!depthDraws(fill.depth, back)) continue
                FACES.forEachIndexed { i, face ->
                    if (i in faces && front[i] != back) drawFillFace(canvas, view, face, fill.scale, c1, c2, fill.colors.alpha)
                }
            }
        }

        val unitWidth = view.unit / LINE_WIDTH_UNIT
        style.layers.forEach { layer ->
            val faces = faceSet(layer.faces, lookedAt)
            val (c1, c2) = layer.colors.at(millis)
            for (back in listOf(true, false)) {
                if (!depthDraws(layer.depth, back)) continue
                EDGES.forEach { edge ->
                    if (edge.faces.none { it in faces }) return@forEach
                    if (edge.faces.any { front[it] } == back) return@forEach
                    drawEdge(canvas, view, edge, layer, c1, c2, layer.colors.alpha, unitWidth)
                }
            }
        }
    }

    private fun faceSet(faces: Faces, lookedAt: Int): Set<Int> = when (faces) {
        Faces.ALL, Faces.AIR_EXPOSED -> FACES.indices.toSet()
        Faces.LOOKAT -> setOf(lookedAt)
        Faces.CONCEALED -> emptySet()
    }

    private fun depthDraws(depth: Depth, back: Boolean) = when (depth) {
        Depth.NORMAL -> !back
        Depth.HIDDEN_ONLY -> back
        Depth.ALWAYS_PASS -> true
    }

    private fun drawBlockFace(canvas: Canvas, view: View, face: Face, texture: Image?) {
        val tl = view.project(face.tl, 1f)
        val tr = view.project(face.tr, 1f)
        val bl = view.project(face.bl, 1f)
        val shade = (face.shade * 255).toInt()
        val tint = (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        Paint().use { paint ->
            paint.isAntiAlias = true
            if (texture == null) {
                paint.color = tint and 0xFF7F7F7F.toInt()
                parallelogram(tl, tr, bl).use { canvas.drawPath(it, paint) }
                return
            }
            val size = texture.width.toFloat()
            val filter = ColorFilter.makeBlend(tint, BlendMode.MODULATE)
            paint.colorFilter = filter
            canvas.save()
            canvas.concat(
                Matrix33(
                    (tr.x - tl.x) / size, (bl.x - tl.x) / size, tl.x,
                    (tr.y - tl.y) / size, (bl.y - tl.y) / size, tl.y,
                    0f, 0f, 1f,
                ),
            )
            canvas.drawImageRect(texture, Rect.makeWH(size, size), Rect.makeWH(size, size), SamplingMode.DEFAULT, paint, true)
            canvas.restore()
            filter.close()
        }
    }

    private fun drawFillFace(canvas: Canvas, view: View, face: Face, scale: Float, c1: Int, c2: Int, alpha: Int) {
        if (alpha <= 0) return
        val s = scale + 0.0002f
        val tl = view.project(face.tl, s)
        val tr = view.project(face.tr, s)
        val bl = view.project(face.bl, s)
        val corners = listOf(face.tl, face.tr, face.bl, face.tl xor face.tr xor face.bl)
        val low = corners.minBy(::gradientT)
        val high = corners.maxBy(::gradientT)
        val shader = gradient(
            view.project(low, s), view.project(high, s),
            lerp(c1, c2, gradientT(low)), lerp(c1, c2, gradientT(high)), alpha,
        )
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.shader = shader
            parallelogram(tl, tr, bl).use { canvas.drawPath(it, paint) }
        }
        shader.close()
    }

    private fun drawEdge(
        canvas: Canvas,
        view: View,
        edge: Edge,
        layer: BlockHighlightStyle.Layer,
        c1: Int,
        c2: Int,
        alpha: Int,
        unitWidth: Float,
    ) {
        if (alpha < 1) return
        val a = view.project(edge.a, layer.scale)
        val b = view.project(edge.b, layer.scale)
        val ca = lerp(c1, c2, gradientT(edge.a))
        val cb = lerp(c1, c2, gradientT(edge.b))
        val width = layer.width * unitWidth
        val center = layer.cutFromCenter
        val corner = layer.cutFromCorner
        if (center == 0f && corner == 0f) {
            segment(canvas, a, b, width, width, ca, cb, alpha)
            return
        }
        val minOuter = lerp(a, b, corner / 2f)
        val maxOuter = lerp(b, a, corner / 2f)
        val outer = width * layer.outerMult
        if (center == 0f && layer.outerMult == 0f && layer.innerMult == 0f) {
            segment(canvas, minOuter, maxOuter, outer, outer, ca, cb, alpha)
            return
        }
        val mid = lerp(a, b, 0.5f)
        val minInner = lerp(mid, a, center)
        val maxInner = lerp(mid, b, center)
        val span = distance(minOuter, maxOuter)
        val t = if (span == 0f) 0f else (distance(minInner, minOuter) / span).coerceIn(0f, 1f)
        val inner = width * layer.innerMult
        segment(canvas, minOuter, minInner, outer, inner, ca, lerp(ca, cb, t), alpha)
        segment(canvas, maxInner, maxOuter, inner, outer, lerp(ca, cb, 1f - t), cb, alpha)
    }

    private fun segment(canvas: Canvas, a: Point, b: Point, wa: Float, wb: Float, ca: Int, cb: Int, alpha: Int) {
        val length = distance(a, b)
        if (length < 0.01f || (wa <= 0f && wb <= 0f)) return
        val nx = -(b.y - a.y) / length
        val ny = (b.x - a.x) / length
        val path = PathBuilder().use { it
            .moveTo(a.x + nx * wa / 2f, a.y + ny * wa / 2f)
            .lineTo(b.x + nx * wb / 2f, b.y + ny * wb / 2f)
            .lineTo(b.x - nx * wb / 2f, b.y - ny * wb / 2f)
            .lineTo(a.x - nx * wa / 2f, a.y - ny * wa / 2f)
            .closePath()
            .detach()
        }
        val shader = gradient(a, b, ca, cb, alpha)
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.shader = shader
            canvas.drawPath(path, paint)
        }
        shader.close()
        path.close()
    }

    private fun gradient(a: Point, b: Point, ca: Int, cb: Int, alpha: Int): Shader {
        val argbA = (alpha shl 24) or (ca and 0xFFFFFF)
        val argbB = (alpha shl 24) or (cb and 0xFFFFFF)
        if (ca == cb || distance(a, b) < 0.01f) return Shader.makeColor(argbA)
        return Shader.makeLinearGradient(a, b, Gradient(Gradient.Colors(arrayOf(Color4f(argbA), Color4f(argbB)), null, FilterTileMode.CLAMP)))
    }

    private fun parallelogram(tl: Point, tr: Point, bl: Point) = PathBuilder().use {
        it.moveTo(tl.x, tl.y)
            .lineTo(tr.x, tr.y)
            .lineTo(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
            .lineTo(bl.x, bl.y)
            .closePath()
            .detach()
    }

    private fun lerp(a: Point, b: Point, t: Float) = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun distance(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

    private fun lerp(c1: Int, c2: Int, t: Float): Int {
        fun channel(shift: Int) = ((c1 shr shift and 0xFF) + ((c2 shr shift and 0xFF) - (c1 shr shift and 0xFF)) * t).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val LINE_WIDTH_UNIT = 80f
}
