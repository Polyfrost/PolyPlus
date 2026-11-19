package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.minecraft.core.BlockPos
import org.joml.Matrix4fc
import org.joml.Vector3fc
import org.joml.Vector4f
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Depth
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Faces
import kotlin.math.hypot
import kotlin.math.sqrt

internal object LiveWorldOutline {
    private class Face(val axis: Int, val sign: Int, val corners: IntArray)

    private class Edge(val a: Int, val b: Int, val faces: List<Int>)

    private fun bit(corner: Int, axis: Int) = (corner shr axis) and 1

    private val FACES: List<Face> = (0 until AXES).flatMap { axis ->
        (0..1).map { sign ->
            val others = (0 until AXES).filter { it != axis }
            val base = sign shl axis
            val u = 1 shl others[0]
            val v = 1 shl others[1]
            Face(axis, sign, intArrayOf(base, base or u, base or u or v, base or v))
        }
    }

    private val EDGES: List<Edge> = (0 until CORNERS).flatMap { a ->
        (0 until AXES).filter { bit(a, it) == 0 }.map { axis ->
            val b = a or (1 shl axis)
            Edge(a, b, (0 until AXES).filter { it != axis }.map { other -> faceIndex(other, bit(a, other)) })
        }
    }

    private fun coord(block: BlockPos, axis: Int): Int = when (axis) {
        0 -> block.x
        1 -> block.y
        else -> block.z
    }

    private fun faceIndex(axis: Int, sign: Int) = FACES.indexOfFirst { it.axis == axis && it.sign == sign }

    private fun gradientT(corner: Int) = sqrt((bit(corner, 0) + bit(corner, 1) + bit(corner, 2)).toFloat() / AXES)

    fun draw(
        canvas: DrawScope,
        style: BlockHighlightStyle,
        viewProjection: Matrix4fc,
        eye: Vector3fc,
        block: BlockPos,
        millis: Long,
        pixelScale: Float,
        isEmpty: (BlockPos) -> Boolean,
    ) {
        val w = canvas.size.width
        val h = canvas.size.height
        if (w <= 0f || h <= 0f) return
        val facing = FACES.map { face ->
            val center = coord(block, face.axis) + face.sign.toFloat()
            val toEye = eye.get(face.axis) - center
            if (face.sign == 1) toEye else -toEye
        }
        val front = facing.map { it > 0f }
        val lookedAt = facing.indices.maxBy { facing[it] }
        val open = FACES.indices.filter { isEmpty(neighbor(block, FACES[it])) }.toSet()
        val corners = { scale: Float -> project(viewProjection, block, scale, w, h) }

        style.fill?.let { fill ->
            val points = corners(fill.scale + FILL_BIAS)
            val (c1, c2) = fill.colors.at(millis)
            for (back in BACK_FIRST) {
                if (!depthDraws(fill.depth, back)) continue
                faceSet(fill.faces, lookedAt, open).forEach { i ->
                    if (front[i] != back) fillFace(canvas, FACES[i], points, c1, c2, fill.colors.alpha)
                }
            }
        }

        style.layers.forEach { layer ->
            val points = corners(layer.scale)
            val faces = faceSet(layer.faces, lookedAt, open)
            val (c1, c2) = layer.colors.at(millis)
            val width = (layer.width * pixelScale).coerceAtLeast(MIN_WIDTH)
            for (back in BACK_FIRST) {
                if (!depthDraws(layer.depth, back)) continue
                EDGES.forEach { edge ->
                    if (edge.faces.none { it in faces }) return@forEach
                    if (edge.faces.any { front[it] } == back) return@forEach
                    edge(canvas, edge, points, layer, c1, c2, width)
                }
            }
        }
    }

    private fun project(viewProjection: Matrix4fc, block: BlockPos, scale: Float, w: Float, h: Float): List<Offset> {
        val v = Vector4f()
        return (0 until CORNERS).map { corner ->
            val half = scale / 2f
            v.set(
                block.x + HALF + if (bit(corner, 0) == 1) half else -half,
                block.y + HALF + if (bit(corner, 1) == 1) half else -half,
                block.z + HALF + if (bit(corner, 2) == 1) half else -half,
                1f,
            )
            viewProjection.transform(v)
            val invW = if (v.w == 0f) 0f else 1f / v.w
            Offset((v.x * invW + 1f) / 2f * w, (1f - v.y * invW) / 2f * h)
        }
    }

    private fun faceSet(faces: Faces, lookedAt: Int, open: Set<Int>): Set<Int> = when (faces) {
        Faces.ALL -> FACES.indices.toSet()
        Faces.AIR_EXPOSED -> open
        Faces.LOOKAT -> setOf(lookedAt)
        Faces.CONCEALED -> FACES.indices.toSet() - open
    }

    private fun neighbor(block: BlockPos, face: Face): BlockPos {
        val step = if (face.sign == 1) 1 else -1
        return when (face.axis) {
            0 -> block.offset(step, 0, 0)
            1 -> block.offset(0, step, 0)
            else -> block.offset(0, 0, step)
        }
    }

    private fun depthDraws(depth: Depth, back: Boolean) = when (depth) {
        Depth.NORMAL -> !back
        Depth.HIDDEN_ONLY -> back
        Depth.ALWAYS_PASS -> true
    }

    private fun fillFace(canvas: DrawScope, face: Face, points: List<Offset>, c1: Int, c2: Int, alpha: Int) {
        if (alpha <= 0) return
        val path = Path()
        face.corners.forEachIndexed { i, corner ->
            val p = points[corner]
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        val low = face.corners.minBy(::gradientT)
        val high = face.corners.maxBy(::gradientT)
        canvas.drawPath(path, brush(points[low], points[high], mix(c1, c2, gradientT(low)), mix(c1, c2, gradientT(high)), alpha))
    }

    private fun edge(canvas: DrawScope, edge: Edge, points: List<Offset>, layer: BlockHighlightStyle.Layer, c1: Int, c2: Int, width: Float) {
        val alpha = layer.colors.alpha
        if (alpha < 1) return
        val a = points[edge.a]
        val b = points[edge.b]
        val ca = mix(c1, c2, gradientT(edge.a))
        val cb = mix(c1, c2, gradientT(edge.b))
        val corner = layer.cutFromCorner / 2f
        val center = layer.cutFromCenter / 2f
        val outer = width * layer.outerMult.coerceAtLeast(0f)
        if (center <= 0f) {
            segment(canvas, lerp(a, b, corner), lerp(a, b, 1f - corner), mix(ca, cb, corner), mix(ca, cb, 1f - corner), alpha, outer)
            return
        }
        val inner = width * layer.innerMult.coerceAtLeast(0f)
        val split = (HALF - center).coerceAtLeast(corner)
        val stroke = (outer + inner) / 2f
        segment(canvas, lerp(a, b, corner), lerp(a, b, split), mix(ca, cb, corner), mix(ca, cb, split), alpha, stroke)
        segment(canvas, lerp(a, b, 1f - split), lerp(a, b, 1f - corner), mix(ca, cb, 1f - split), mix(ca, cb, 1f - corner), alpha, stroke)
    }

    private fun segment(canvas: DrawScope, a: Offset, b: Offset, ca: Int, cb: Int, alpha: Int, width: Float) {
        if (width <= 0f || hypot(b.x - a.x, b.y - a.y) < MIN_LENGTH) return
        canvas.drawLine(brush(a, b, ca, cb, alpha), a, b, width, StrokeCap.Square)
    }

    private fun brush(a: Offset, b: Offset, ca: Int, cb: Int, alpha: Int): Brush {
        val first = Color((alpha shl ALPHA_SHIFT) or (ca and RGB))
        if (ca == cb || hypot(b.x - a.x, b.y - a.y) < MIN_LENGTH) return SolidColor(first)
        return Brush.linearGradient(listOf(first, Color((alpha shl ALPHA_SHIFT) or (cb and RGB))), a, b)
    }

    private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun mix(c1: Int, c2: Int, t: Float): Int {
        fun channel(shift: Int) = ((c1 shr shift and CHANNEL) + ((c2 shr shift and CHANNEL) - (c1 shr shift and CHANNEL)) * t).toInt()
        return (channel(RED) shl RED) or (channel(GREEN) shl GREEN) or channel(0)
    }

    private val BACK_FIRST = listOf(true, false)
    private const val AXES = 3
    private const val CORNERS = 8
    private const val HALF = 0.5f
    private const val FILL_BIAS = 0.002f
    private const val MIN_WIDTH = 1f
    private const val MIN_LENGTH = 0.01f
    private const val ALPHA_SHIFT = 24
    private const val RGB = 0xFFFFFF
    private const val CHANNEL = 0xFF
    private const val RED = 16
    private const val GREEN = 8
}
//?}
