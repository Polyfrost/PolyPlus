package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Depth
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Faces
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object BlockHighlightRenderer {
    fun faces(preset: String, load: (String) -> PolyBitmap? = VanillaTextures::load): List<ImageBitmap?> =
        (presetTextures(preset)?.map(load) ?: grassBlock(load)).map { it?.asComposeImageBitmap() }

    private fun presetTextures(preset: String): List<String>? {
        //? if > 1.8.9 {
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
            else -> null
        }
        //?} else {
        /*return null
        *///?}
    }

    private fun grassBlock(load: (String) -> PolyBitmap?): List<PolyBitmap?> {
        //? if > 1.8.9 {
        fun grass(name: String) = "textures/block/grass_block_$name.png"
        val dirt = "textures/block/dirt.png"
        //?} else {
        /*fun grass(name: String) = "textures/blocks/grass_$name.png"
        val dirt = "textures/blocks/dirt.png"
        *///?}
        val side = layered(load, grass("side") to WHITE, grass("side_overlay") to GRASS_TINT)
        return listOf(layered(load, grass("top") to GRASS_TINT), load(dirt)) + List(4) { side }
    }

    private fun layered(load: (String) -> PolyBitmap?, vararg layers: Pair<String, Int>): PolyBitmap? {
        val images = layers.map { (path, tint) -> (load(path) ?: return null) to tint }
        val size = images.first().first.width
        val out = FloatArray(size * size * 4)
        images.forEach { (image, tint) ->
            val frame = image.width
            val px = image.pixels
            for (y in 0 until size) for (x in 0 until size) {
                val si = ((y * frame / size) * image.width + x * frame / size) * 4
                val sa = (px[si + 3].toInt() and 0xFF) / 255f * ((tint ushr 24) and 0xFF) / 255f
                if (sa <= 0f) continue
                val di = (y * size + x) * 4
                for (c in 0..2) {
                    val sc = (px[si + c].toInt() and 0xFF) / 255f * ((tint shr (16 - c * 8)) and 0xFF) / 255f
                    out[di + c] = sc * sa + out[di + c] * (1f - sa)
                }
                out[di + 3] = sa + out[di + 3] * (1f - sa)
            }
        }
        val bytes = ByteArray(out.size)
        for (i in 0 until size * size) {
            val a = out[i * 4 + 3]
            if (a <= 0f) continue
            for (c in 0..2) bytes[i * 4 + c] = (out[i * 4 + c] / a * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            bytes[i * 4 + 3] = (a * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        return PolyBitmap(size, size, bytes)
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

        fun project(corner: Int, scale: Float): Offset {
            val h = scale / 2f
            val (x, y, _) = rotate(
                if (bit(corner, 0) == 1) h else -h,
                if (bit(corner, 1) == 1) h else -h,
                if (bit(corner, 2) == 1) h else -h,
            )
            return Offset(cx + x * unit, cy - y * unit)
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
        canvas: DrawScope,
        width: Float,
        height: Float,
        style: BlockHighlightStyle,
        textures: List<ImageBitmap?>,
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

    private fun drawBlockFace(canvas: DrawScope, view: View, face: Face, texture: ImageBitmap?) {
        val tl = view.project(face.tl, 1f)
        val tr = view.project(face.tr, 1f)
        val bl = view.project(face.bl, 1f)
        val shade = (face.shade * 255).toInt()
        val tint = (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        if (texture == null) {
            canvas.drawPath(parallelogram(tl, tr, bl), Color(tint and 0xFF7F7F7F.toInt()))
            return
        }
        val size = texture.width
        canvas.inFace(tl, tr, bl, size.toFloat()) {
            drawImage(
                texture,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(size, size),
                dstSize = IntSize(size, size),
                colorFilter = ColorFilter.tint(Color(tint), BlendMode.Modulate),
                filterQuality = FilterQuality.None,
            )
        }
    }

    private fun drawFillFace(canvas: DrawScope, view: View, face: Face, scale: Float, c1: Int, c2: Int, alpha: Int) {
        if (alpha <= 0) return
        val s = scale + 0.0002f
        val tl = view.project(face.tl, s)
        val tr = view.project(face.tr, s)
        val bl = view.project(face.bl, s)
        val corners = listOf(face.tl, face.tr, face.bl, face.tl xor face.tr xor face.bl)
        val low = corners.minBy(::gradientT)
        val high = corners.maxBy(::gradientT)
        val a = view.project(low, s)
        val b = view.project(high, s)
        val ca = lerp(c1, c2, gradientT(low))
        val cb = lerp(c1, c2, gradientT(high))
        val m00 = (tr.x - tl.x) / FILL_UNIT
        val m01 = (bl.x - tl.x) / FILL_UNIT
        val m10 = (tr.y - tl.y) / FILL_UNIT
        val m11 = (bl.y - tl.y) / FILL_UNIT
        val det = m00 * m11 - m01 * m10
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d2 = dx * dx + dy * dy
        if (det == 0f) return
        val ax = a.x - tl.x
        val ay = a.y - tl.y
        val start = Offset((m11 * ax - m01 * ay) / det, (-m10 * ax + m00 * ay) / det)
        val gx = if (d2 == 0f) 0f else (m00 * dx + m10 * dy) / d2
        val gy = if (d2 == 0f) 0f else (m01 * dx + m11 * dy) / d2
        val g2 = gx * gx + gy * gy
        val end = if (g2 == 0f) start else Offset(start.x + gx / g2, start.y + gy / g2)
        val brush = gradient(start, end, ca, cb, alpha)
        canvas.inFace(tl, tr, bl, FILL_UNIT) { drawRect(brush, Offset.Zero, Size(FILL_UNIT, FILL_UNIT)) }
    }

    private inline fun DrawScope.inFace(tl: Offset, tr: Offset, bl: Offset, size: Float, block: DrawScope.() -> Unit) {
        val m00 = (tr.x - tl.x) / size
        val m01 = (bl.x - tl.x) / size
        val m10 = (tr.y - tl.y) / size
        val m11 = (bl.y - tl.y) / size
        val e = (m00 + m11) / 2f
        val f = (m00 - m11) / 2f
        val g = (m10 + m01) / 2f
        val h = (m10 - m01) / 2f
        val q = hypot(e, h)
        val r = hypot(f, g)
        val a1 = atan2(g, f)
        val a2 = atan2(h, e)
        val theta = Math.toDegrees(((a2 + a1) / 2f).toDouble()).toFloat()
        val phi = Math.toDegrees(((a2 - a1) / 2f).toDouble()).toFloat()
        withTransform({
            translate(tl.x, tl.y)
            rotate(theta, Offset.Zero)
            scale(q + r, q - r, Offset.Zero)
            rotate(phi, Offset.Zero)
        }, block)
    }

    private fun drawEdge(
        canvas: DrawScope,
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

    private fun segment(canvas: DrawScope, a: Offset, b: Offset, wa: Float, wb: Float, ca: Int, cb: Int, alpha: Int) {
        val length = distance(a, b)
        if (length < 0.01f || (wa <= 0f && wb <= 0f)) return
        val core = minOf(wa, wb)
        val brush = gradient(Offset.Zero, Offset(length, 0f), ca, cb, alpha)
        val angle = Math.toDegrees(atan2(b.y - a.y, b.x - a.x).toDouble()).toFloat()
        canvas.withTransform({
            translate(a.x, a.y)
            rotate(angle, Offset.Zero)
        }) {
            if (core > 0f) drawRect(brush, Offset(0f, -core / 2f), Size(length, core))
            if (wa == wb) return@withTransform
            val wide = if (wa > wb) 0f else length
            val sliver = (maxOf(wa, wb) - core) / 2f
            val color = Color((alpha shl 24) or (lerp(ca, cb, if (wa > wb) 1f / 3f else 2f / 3f) and 0xFFFFFF))
            for (side in intArrayOf(-1, 1)) {
                val edge = side * core / 2f
                drawPath(
                    Path().apply {
                        moveTo(0f, edge)
                        lineTo(length, edge)
                        lineTo(wide, edge + side * sliver)
                        close()
                    },
                    color,
                )
            }
        }
    }

    private fun gradient(a: Offset, b: Offset, ca: Int, cb: Int, alpha: Int): Brush {
        val argbA = (alpha shl 24) or (ca and 0xFFFFFF)
        val argbB = (alpha shl 24) or (cb and 0xFFFFFF)
        if (ca == cb || distance(a, b) < 0.01f) return SolidColor(Color(argbA))
        return Brush.linearGradient(listOf(Color(argbA), Color(argbB)), a, b)
    }

    private fun parallelogram(tl: Offset, tr: Offset, bl: Offset) = Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tr.x, tr.y)
        lineTo(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
        lineTo(bl.x, bl.y)
        close()
    }

    private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun distance(a: Offset, b: Offset) = hypot(b.x - a.x, b.y - a.y)

    private fun lerp(c1: Int, c2: Int, t: Float): Int {
        fun channel(shift: Int) = ((c1 shr shift and 0xFF) + ((c2 shr shift and 0xFF) - (c1 shr shift and 0xFF)) * t).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val LINE_WIDTH_UNIT = 230f
    private const val FILL_UNIT = 16f
    private const val WHITE = -1
    private const val GRASS_TINT = 0xFF91BD59.toInt() // plains grass colour
}
