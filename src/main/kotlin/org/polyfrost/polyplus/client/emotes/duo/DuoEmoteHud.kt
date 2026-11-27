package org.polyfrost.polyplus.client.emotes.duo

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.emotes.context.ContextEmotes
import org.polyfrost.polyplus.client.ping.PingCanvas
import org.polyfrost.polyplus.client.ping.PingGraphics
import org.polyfrost.polyplus.client.ping.PingProjection
import org.polyfrost.polyplus.client.ping.ScreenPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import org.polyfrost.polyplus.client.emotes.EmoteServerClock

//? if = 1.8.9 {
/*import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import org.lwjgl.opengl.GL11
*///?}

internal class PixelRuns {
    private var rects = IntArray(INITIAL * 4)

    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(x: Int, y: Int) {
        if (size > 0) {
            val i = (size - 1) * 4
            val x0 = rects[i]
            val y0 = rects[i + 1]
            val x1 = rects[i + 2]
            val y1 = rects[i + 3]
            if (x in x0 until x1 && y in y0 until y1) return
            if (y1 - y0 == 1 && y == y0) {
                if (x == x1) {
                    rects[i + 2] = x + 1
                    return
                }
                if (x == x0 - 1) {
                    rects[i] = x
                    return
                }
            }
            if (x1 - x0 == 1 && x == x0) {
                if (y == y1) {
                    rects[i + 3] = y + 1
                    return
                }
                if (y == y0 - 1) {
                    rects[i + 1] = y
                    return
                }
            }
        }
        if ((size + 1) * 4 > rects.size) rects = rects.copyOf(rects.size * 2)
        val i = size * 4
        rects[i] = x
        rects[i + 1] = y
        rects[i + 2] = x + 1
        rects[i + 3] = y + 1
        size++
    }

    fun forEach(draw: (Int, Int, Int, Int) -> Unit) {
        for (r in 0 until size) {
            val i = r * 4
            draw(rects[i], rects[i + 1], rects[i + 2], rects[i + 3])
        }
    }

    private companion object {
        const val INITIAL = 256
    }
}

object DuoEmoteHud {
    private class Line(val text: String, val color: Int)

    private val runs = PixelRuns()
    private val ring = arrayOfNulls<ScreenPoint>(RING_SEGMENTS)
    private val ringX = DoubleArray(RING_SEGMENTS) { cos(Math.PI * 2 * it / RING_SEGMENTS) * RING_RADIUS }
    private val ringZ = DoubleArray(RING_SEGMENTS) { sin(Math.PI * 2 * it / RING_SEGMENTS) * RING_RADIUS }
    private var spotX = 0.0
    private var spotY = 0.0
    private var spotZ = 0.0
    private var spotCos = 1.0
    private var spotSin = 0.0
    private var markerTicks = 0f

    @JvmStatic
    fun render(graphics: PingGraphics?, partialTicks: Float) {
        DuoEmotes.initialize()
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        //? if >= 26.2 {
        if (mc.gui.hud.isHidden) return
        //?} else {
        /*if (mc.options.hideGui) return
        *///?}
        val approach = DuoEmotes.approach()
        val context = ContextEmotes.target
        val lines = lines(approach, context)
        if (approach?.spot == null && context == null && lines.isEmpty()) {
            MarkerGhost.clear()
            return
        }
        val canvas = PingCanvas(graphics)
        if (approach?.spot != null) {
            MarkerGhost.markDuo(approach)
            val label = if (approach.leading) "${DuoEmotes.nameOf(approach.request.partner)}'s spot" else STAND_HERE
            marker(canvas, graphics, approach.spot, approach.ready, label, partialTicks)
        } else if (context != null) {
            MarkerGhost.markContext(context)
            marker(canvas, graphics, context.spot, false, STAND_HERE, partialTicks)
        } else {
            MarkerGhost.clear()
        }
        if (lines.isNotEmpty()) card(canvas, lines)
    }

    private fun lines(approach: DuoEmotes.Approach?, context: ContextEmotes.Target?): List<Line> {
        val now = System.currentTimeMillis()
        val serverNow = EmoteServerClock.now()
        val prompts = DuoEmotes.incomingPrompts()
        prompts.firstOrNull()?.let { request ->
            DuoEmotes.promptShown(request)
            val name = DuoEmotes.nameOf(request.partner)
            val accept = PolyPlusConfig.acceptDuoEmoteKeybind
            val decline = PolyPlusConfig.declineDuoEmoteKeybind
            val keys = when {
                !accept.isBound -> "Set a key for Accept Duo Emote in the PolyPlus settings to join"
                decline.isBound -> "Press ${key(accept)} to join or ${key(decline)} to say no"
                else -> "Press ${key(accept)} to join"
            }
            val others = prompts.drop(1)
            val waiting = others.firstOrNull()?.let { next ->
                othersWaiting(others.map { DuoEmotes.nameOf(it.partner) }, secondsLeft(next.expiresAt, serverNow))
            }
            return listOfNotNull(
                Line("$name asked you to do a duo emote", TEXT),
                Line(DuoEmotes.emoteName(request.emoteId), ACCENT),
                Line("$keys (${secondsLeft(request.expiresAt, serverNow)}s)", DETAIL),
                waiting?.let { Line(it, DETAIL) },
            )
        }
        if (approach != null) {
            val name = DuoEmotes.nameOf(approach.request.partner)
            val left = secondsLeft(approach.request.expiresAt, serverNow)
            val status = when {
                approach.spot == null || approach.follower == null -> Line("$name is too far away (${left}s)", DETAIL)
                approach.ready && approach.leading -> Line("Starting", READY)
                approach.ready -> Line("Hold still", READY)
                approach.leading -> Line("Waiting for $name to stand on the marker (${left}s)", TEXT)
                else -> Line("Stand on the marker and face $name (${left}s)", TEXT)
            }
            return listOfNotNull(Line(DuoEmotes.emoteName(approach.request.emoteId), ACCENT), status, cancelLine())
        }
        if (context != null) {
            return listOfNotNull(
                Line(DuoEmotes.emoteName(context.emoteId), ACCENT),
                Line("Stand still on the marker to start (${secondsLeft(context.until, now)}s)", TEXT),
                cancelLine(),
            )
        }
        DuoEmotes.waiting()?.let { request ->
            val name = DuoEmotes.nameOf(request.partner)
            return listOfNotNull(
                Line("Waiting for $name to answer (${secondsLeft(request.expiresAt, serverNow)}s)", TEXT),
                cancelLine(),
            )
        }
        DuoEmotes.visibleNotice(now)?.let { return listOf(Line(it, TEXT)) }
        return emptyList()
    }

    private fun cancelLine(): Line? {
        val stop = PolyPlusConfig.stopEmoteKeybind
        return if (stop.isBound) Line("Press ${key(stop)} to cancel", DETAIL) else null
    }

    private fun key(keybind: OneConfigKeybind): String = runCatching { keybind.displayName() }.getOrDefault("?")

    private fun secondsLeft(until: Long, now: Long): Int = max(0, ceil((until - now) / MS_PER_SECOND).toInt())

    private fun card(canvas: PingCanvas, lines: List<Line>) {
        val width = lines.maxOf { canvas.width(it.text) } + PAD_X * 2
        val height = lines.size * canvas.lineHeight + (lines.size - 1) * LINE_GAP + PAD_Y * 2
        val cx = (PingProjection.guiWidth() / 2f).roundToInt()
        val top = (PingProjection.guiHeight() * TOP_FRACTION).roundToInt()
        canvas.fill(cx - width / 2, top, cx + width - width / 2, top + height, PLATE)
        var y = top + PAD_Y + 1
        for (line in lines) {
            canvas.text(line.text, cx - canvas.width(line.text) / 2, y, line.color)
            y += canvas.lineHeight + LINE_GAP
        }
    }

    private fun marker(canvas: PingCanvas, graphics: PingGraphics?, spot: Spot, ready: Boolean, label: String, partialTicks: Float) {
        val color = if (ready) READY_MARK else MARK
        val radians = Math.toRadians(spot.yaw.toDouble())
        spotX = spot.x
        spotY = spot.y
        spotZ = spot.z
        spotCos = cos(radians)
        spotSin = sin(radians)
        markerTicks = partialTicks
        runs.clear()
        for (i in 0 until RING_SEGMENTS) ring[i] = at(ringX[i], GROUND_LIFT, ringZ[i])
        for (i in 0 until RING_SEGMENTS) line(ring[i], ring[(i + 1) % RING_SEGMENTS])
        val tip = at(0.0, GROUND_LIFT, ARROW_LENGTH)
        line(at(0.0, GROUND_LIFT, 0.0), tip)
        line(tip, at(ARROW_WING, GROUND_LIFT, ARROW_LENGTH - ARROW_WING))
        line(tip, at(-ARROW_WING, GROUND_LIFT, ARROW_LENGTH - ARROW_WING))
        drawRuns(canvas, graphics, color)
        val above = at(0.0, LABEL_HEIGHT, 0.0)
        if (above != null && above.onScreen) {
            val x = above.x.roundToInt() - canvas.width(label) / 2
            val y = above.y.roundToInt() - canvas.lineHeight
            canvas.fill(x - 2, y - 2, x + canvas.width(label) + 2, y + canvas.lineHeight, PLATE)
            canvas.text(label, x, y, if (ready) READY else TEXT)
        }
    }

    private fun at(x: Double, y: Double, z: Double): ScreenPoint? =
        PingProjection.project(spotX + x * spotCos - z * spotSin, spotY + y, spotZ + x * spotSin + z * spotCos, markerTicks)

    private fun line(a: ScreenPoint?, b: ScreenPoint?) {
        if (a == null || b == null || !a.onScreen || !b.onScreen) return
        val dx = b.x - a.x
        val dy = b.y - a.y
        val steps = max(abs(dx), abs(dy)).roundToInt().coerceIn(1, MAX_LINE_STEPS)
        for (i in 0..steps) {
            runs.add((a.x + dx * i / steps).roundToInt(), (a.y + dy * i / steps).roundToInt())
        }
    }

    private fun drawRuns(canvas: PingCanvas, graphics: PingGraphics?, color: Int) {
        if (runs.size == 0) return
        //? if = 1.8.9 {
        /*val tesselator = Tesselator.getInstance()
        val buffer = tesselator.buffer
        GlStateManager.enableBlend()
        GlStateManager.disableTexture()
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GlStateManager.color4f(
            (color shr 16 and 0xFF) / 255f,
            (color shr 8 and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            (color ushr 24) / 255f,
        )
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION)
        runs.forEach { x0, y0, x1, y1 ->
            buffer.vertex(x0.toDouble(), y1.toDouble(), 0.0).nextVertex()
            buffer.vertex(x1.toDouble(), y1.toDouble(), 0.0).nextVertex()
            buffer.vertex(x1.toDouble(), y0.toDouble(), 0.0).nextVertex()
            buffer.vertex(x0.toDouble(), y0.toDouble(), 0.0).nextVertex()
        }
        tesselator.end()
        GlStateManager.enableTexture()
        GlStateManager.disableBlend()
        *///?} elif = 1.21.1 {
        /*graphics?.drawManaged { runs.forEach { x0, y0, x1, y1 -> canvas.fill(x0, y0, x1, y1, color) } }
        *///?} else {
        runs.forEach { x0, y0, x1, y1 -> canvas.fill(x0, y0, x1, y1, color) }
        //?}
        runs.clear()
    }

    private const val STAND_HERE = "Stand here"
    private const val MS_PER_SECOND = 1000.0
    private const val TOP_FRACTION = 0.14f
    private const val PAD_X = 6
    private const val PAD_Y = 4
    private const val LINE_GAP = 2
    private const val PLATE = 0xB0101014.toInt()
    private const val TEXT = 0xFFFFFFFF.toInt()
    private const val DETAIL = 0xFFB4B4BE.toInt()
    private const val ACCENT = 0xFF8AD4FF.toInt()
    private const val READY = 0xFF7BE495.toInt()
    private const val MARK = 0xC8E6F0FF.toInt()
    private const val READY_MARK = 0xE07BE495.toInt()
    private const val MAX_LINE_STEPS = 400
    private const val RING_SEGMENTS = 24
    private const val RING_RADIUS = 0.4
    private const val GROUND_LIFT = 0.03
    private const val ARROW_LENGTH = 0.65
    private const val ARROW_WING = 0.15
    private const val LABEL_HEIGHT = 2.35
}
