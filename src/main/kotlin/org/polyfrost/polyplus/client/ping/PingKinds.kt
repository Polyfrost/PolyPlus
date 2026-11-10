package org.polyfrost.polyplus.client.ping

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal val WHEEL_KINDS = listOf(PingKind.GO, PingKind.ENEMY, PingKind.LOOK, PingKind.LOOT, PingKind.DANGER)

internal val WHEEL_REPLIES = listOf(PingReply.SEEN, PingReply.COMING, PingReply.CANT)

internal val PingReply.title: String
    get() = when (this) {
        PingReply.SEEN -> "Seen"
        PingReply.COMING -> "On my way"
        PingReply.CANT -> "Can't make it"
        PingReply.NONE -> ""
    }

internal val PingReply.hint: String
    get() = when (this) {
        PingReply.SEEN -> "Got it"
        PingReply.COMING -> "Heading there"
        PingReply.CANT -> "Not this time"
        PingReply.NONE -> ""
    }

internal val PingReply.color: Int
    get() = when (this) {
        PingReply.SEEN -> 0x7CD992
        PingReply.COMING -> 0x4FC3F7
        PingReply.CANT -> 0xFF6B6B
        PingReply.NONE -> 0x8E8E96
    }

internal val PingKind.title: String
    get() = when (this) {
        PingKind.GO -> "Go here"
        PingKind.ENEMY -> "Enemy"
        PingKind.LOOK -> "Look"
        PingKind.LOOT -> "Loot"
        PingKind.DANGER -> "Danger"
    }

internal val PingKind.hint: String
    get() = when (this) {
        PingKind.GO -> "Mark a spot"
        PingKind.ENEMY -> "Enemy spotted"
        PingKind.LOOK -> "Check this out"
        PingKind.LOOT -> "Loot here"
        PingKind.DANGER -> "Watch out"
    }

internal val PingKind.color: Int
    get() = when (this) {
        PingKind.GO -> 0x4FC3F7
        PingKind.ENEMY -> 0xFF5A5A
        PingKind.LOOK -> 0xFFD54F
        PingKind.LOOT -> 0xB388FF
        PingKind.DANGER -> 0xFF9800
    }

internal const val PING_ICON_GRID = 8

internal fun DrawScope.drawPingIcon(kind: PingKind, size: Size, color: Color, pixel: Boolean) {
    if (pixel) drawPixelPingIcon(kind, size, color) else drawSmoothPingIcon(kind, size, color)
}

private const val BOLD = 0.26f
private const val OUTLINE = 0.16f
private const val CORNER = 0.16f

private class Glyph(val center: Offset, val r: Float) {
    fun at(x: Float, y: Float) = Offset(center.x + x * r, center.y + y * r)

    fun path(closed: Boolean, vararg points: Float) = Path().apply {
        moveTo(center.x + points[0] * r, center.y + points[1] * r)
        for (i in 2 until points.size step 2) lineTo(center.x + points[i] * r, center.y + points[i + 1] * r)
        if (closed) close()
    }

    fun stroke(width: Float) = Stroke(width * r, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

private fun glyph(size: Size): Glyph? {
    val r = min(size.width, size.height) / 2f
    return if (r <= 0f) null else Glyph(Offset(size.width / 2f, size.height / 2f), r)
}

private fun DrawScope.line(g: Glyph, color: Color, x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
    drawLine(color, g.at(x0, y0), g.at(x1, y1), width * g.r, StrokeCap.Round)
}

private fun DrawScope.solid(g: Glyph, color: Color, shape: Path) {
    drawPath(shape, color)
    drawPath(shape, color, style = g.stroke(CORNER))
}

internal fun DrawScope.drawReplyIcon(reply: PingReply, size: Size, color: Color, pixel: Boolean) {
    if (pixel) {
        val u = max(1f, floor(min(size.width, size.height) / PING_ICON_GRID))
        val cx = (size.width / 2f).roundToInt().toFloat()
        val cy = (size.height / 2f).roundToInt().toFloat()
        replyIconRects(reply) { x0, y0, x1, y1 ->
            drawRect(color, Offset(cx + x0 * u, cy + y0 * u), Size((x1 - x0) * u, (y1 - y0) * u))
        }
        return
    }
    val g = glyph(size) ?: return
    when (reply) {
        PingReply.SEEN -> drawPath(g.path(false, -0.6f, 0.02f, -0.18f, 0.44f, 0.62f, -0.4f), color, style = g.stroke(BOLD))
        PingReply.COMING -> {
            drawPath(g.path(false, -0.62f, -0.5f, -0.14f, 0f, -0.62f, 0.5f), color, style = g.stroke(BOLD))
            drawPath(g.path(false, 0.08f, -0.5f, 0.56f, 0f, 0.08f, 0.5f), color, style = g.stroke(BOLD))
        }
        PingReply.CANT -> {
            drawCircle(color, 0.74f * g.r, g.center, style = g.stroke(OUTLINE))
            line(g, color, 0.52f, -0.52f, -0.52f, 0.52f, OUTLINE)
        }
        PingReply.NONE -> Unit
    }
}

internal fun replyIconRects(reply: PingReply, rect: (Int, Int, Int, Int) -> Unit) {
    when (reply) {
        PingReply.SEEN -> {
            rect(2, -2, 4, -1)
            rect(1, -1, 3, 0)
            rect(-4, 0, -2, 1)
            rect(0, 0, 2, 1)
            rect(-3, 1, 1, 2)
            rect(-2, 2, 0, 3)
        }
        PingReply.COMING -> for (y in -2..2) {
            val x = 2 - abs(y)
            rect(-4 + x, y, -2 + x, y + 1)
            rect(x, y, x + 2, y + 1)
        }
        PingReply.CANT -> {
            rect(-2, -4, 2, -3)
            rect(-2, 3, 2, 4)
            rect(-3, -3, -2, -2)
            rect(2, -3, 3, -2)
            rect(-3, 2, -2, 3)
            rect(2, 2, 3, 3)
            rect(-4, -2, -3, 2)
            rect(3, -2, 4, 2)
            rect(1, -2, 2, -1)
            rect(0, -1, 1, 0)
            rect(-1, 0, 0, 1)
            rect(-2, 1, -1, 2)
        }
        PingReply.NONE -> Unit
    }
}

private fun DrawScope.drawSmoothPingIcon(kind: PingKind, size: Size, color: Color) {
    val g = glyph(size) ?: return
    when (kind) {
        PingKind.DANGER -> {
            line(g, color, 0f, -0.72f, 0f, 0.2f, BOLD)
            drawCircle(color, 0.16f * g.r, g.at(0f, 0.68f))
        }
        PingKind.ENEMY -> {
            line(g, color, -0.56f, -0.56f, 0.56f, 0.56f, BOLD)
            line(g, color, 0.56f, -0.56f, -0.56f, 0.56f, BOLD)
        }
        PingKind.LOOT -> solid(g, color, g.path(true, 0f, -0.6f, 0.6f, 0f, 0f, 0.6f, -0.6f, 0f))
        PingKind.GO -> solid(g, color, g.path(true, -0.66f, -0.5f, 0.66f, -0.5f, 0f, 0.56f))
        PingKind.LOOK -> {
            drawCircle(color, 0.78f * g.r, g.center, style = g.stroke(OUTLINE))
            drawCircle(color, 0.24f * g.r, g.center)
        }
    }
}

private fun DrawScope.drawPixelPingIcon(kind: PingKind, size: Size, color: Color) {
    val u = max(1f, floor(min(size.width, size.height) / PING_ICON_GRID))
    val cx = (size.width / 2f).roundToInt().toFloat()
    val cy = (size.height / 2f).roundToInt().toFloat()
    pingIconRects(kind, false) { x0, y0, x1, y1 ->
        drawRect(color, Offset(cx + x0 * u, cy + y0 * u), Size((x1 - x0) * u, (y1 - y0) * u))
    }
}

internal fun pingIconRects(kind: PingKind, small: Boolean, rect: (Int, Int, Int, Int) -> Unit) {
    when (kind) {
        PingKind.DANGER -> {
            rect(-1, -4, 1, 1)
            rect(-1, 2, 1, 4)
        }
        PingKind.ENEMY -> {
            val m = if (small) 2 else 3
            val pad = if (small) 0 else 1
            val w = pad * 2 + 1
            for (i in 0 until m * 2) {
                val left = i - m - pad
                rect(left, i - m, left + w, i - m + 1)
                rect(-(left + w), i - m, -left, i - m + 1)
            }
        }
        PingKind.GO -> for (r in 0..2) {
            val w = 3 - r
            rect(-w, -3 + r * 2, w, -1 + r * 2)
        }
        PingKind.LOOK -> {
            rect(-2, -4, 2, -3)
            rect(-2, 3, 2, 4)
            rect(-3, -3, -2, -2)
            rect(2, -3, 3, -2)
            rect(-3, 2, -2, 3)
            rect(2, 2, 3, 3)
            rect(-4, -2, -3, 2)
            rect(3, -2, 4, 2)
            rect(-1, -1, 1, 1)
        }
        PingKind.LOOT -> for (r in -3..2) {
            val w = 3 - abs(2 * r + 1) / 2
            rect(-w, r, w, r + 1)
        }
    }
}
