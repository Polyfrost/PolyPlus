@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.ping

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.gui.HudComposeHost
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

//? if > 1.8.9 {
import net.minecraft.world.item.ItemStack
//?} else {
/*import net.minecraft.item.ItemStack
*///?}

internal object PingMarkersCompose {
    private var frames: List<PingMarkers.Frame> = emptyList()
    private var now = 0L
    private var tick by mutableIntStateOf(0)

    private var itemStack: ItemStack? = null
    private var itemX = 0f
    private var itemY = 0f
    private var itemScale = 1f

    private val host = HudComposeHost("ping_markers") { Content() }

    fun draw(canvas: PingCanvas, graphics: PingGraphics?, ordered: List<PingMarkers.Frame>, now: Long): Boolean {
        frames = ordered
        this.now = now
        itemStack = null
        tick++
        Snapshot.sendApplyNotifications()
        val drawn = host.draw(graphics)
        frames = emptyList()
        val stack = itemStack
        itemStack = null
        if (!drawn) return false
        if (stack != null) {
            canvas.raise()
            canvas.push(itemX, itemY, itemScale)
            canvas.item(stack, 0, 0)
            canvas.pop()
        }
        return true
    }

    internal fun placeItem(stack: ItemStack, x: Float, y: Float, scale: Float) {
        itemStack = stack
        itemX = x
        itemY = y
        itemScale = scale
    }

    @Composable
    private fun Content() {
        ProvideOneConfigTheme { Layer() }
    }

    @Composable
    private fun Layer() {
        val theme = OneConfigTheme.current
        val measurer = rememberTextMeasurer(0)
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val renderer = MarkerRenderer(theme, measurer, themeRadius(theme, this, layoutDirection))
                onDrawBehind {
                    if (tick < 0) return@onDrawBehind
                    renderer.drawAll(this, frames, now)
                }
            },
        )
    }

    private fun themeRadius(theme: OneConfigUITheme, density: Density, direction: LayoutDirection): Float {
        val reference = Size(REFERENCE_SIDE, REFERENCE_SIDE)
        return when (val outline = runCatching { theme.buttonShape.createOutline(reference, direction, density) }.getOrNull()) {
            is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
            is Outline.Rectangle -> 0f
            else -> REFERENCE_SIDE * FALLBACK_CORNER
        }
    }

    private const val REFERENCE_SIDE = 200f
    private const val FALLBACK_CORNER = 0.2f
}

private class MarkerRenderer(
    theme: OneConfigUITheme,
    private val measurer: TextMeasurer,
    private val themeRadius: Float,
) {
    private val fontFamily = theme.fontFamily
    private val surface = theme.popupBackground.copy(alpha = PIN_SURFACE_ALPHA)
    private val plateSurface = theme.popupBackground.copy(alpha = PLATE_SURFACE_ALPHA)
    private val cardSurface = theme.popupBackground.copy(alpha = CARD_SURFACE_ALPHA)
    private val textPrimary = theme.textColor
    private val textSecondary = theme.textColorSecondary
    private val iconTint = theme.textColor
    private val kindColors = PingKind.values().map { Color(0xFF000000.toInt() or it.color) }
    private val replyColors = PingReply.values().map { Color(0xFF000000.toInt() or it.color) }

    private val pins = PathCache()
    private val chevron = Path()
    private val plateTexts = HashMap<String, TextLayoutResult>()
    private val titleTexts = HashMap<String, TextLayoutResult>()
    private val detailTexts = HashMap<String, TextLayoutResult>()

    private var k = 0f
    private var ring = 0f
    private var ringStroke = Stroke(1f)
    private var chevronStroke = Stroke(1f)
    private var plateStyle = TextStyle.Default
    private var titleStyle = TextStyle.Default
    private var detailStyle = TextStyle.Default

    fun drawAll(scope: DrawScope, frames: List<PingMarkers.Frame>, now: Long) {
        val gw = PingProjection.guiWidth()
        val gh = PingProjection.guiHeight()
        if (gw <= 0f || gh <= 0f) return
        prepare(scope, scope.size.width / gw)
        for (i in frames.indices) scope.marker(frames[i], gw, gh, now)
    }

    private fun prepare(density: Density, scale: Float) {
        if (scale == k) return
        k = scale
        ring = RING_DP * density.density
        ringStroke = Stroke(ring)
        chevronStroke = Stroke(CHEVRON_STROKE * scale, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val textUnit = density.density * density.fontScale
        plateStyle = TextStyle(fontFamily = fontFamily, fontSize = (PLATE_FONT * scale / textUnit).sp, fontWeight = FontWeight.SemiBold)
        titleStyle = TextStyle(fontFamily = fontFamily, fontSize = (TITLE_FONT * scale / textUnit).sp, fontWeight = FontWeight.SemiBold)
        detailStyle = TextStyle(fontFamily = fontFamily, fontSize = (DETAIL_FONT * scale / textUnit).sp, fontWeight = FontWeight.Normal)
        plateTexts.clear()
        titleTexts.clear()
        detailTexts.clear()
        pins.clear()
    }

    private fun measure(cache: HashMap<String, TextLayoutResult>, text: String, style: TextStyle): TextLayoutResult {
        cache[text]?.let { return it }
        if (cache.size >= TEXT_CACHE_LIMIT) cache.clear()
        val result = measurer.measure(text, style, maxLines = 1, softWrap = false)
        cache[text] = result
        return result
    }

    private fun DrawScope.marker(frame: PingMarkers.Frame, gw: Float, gh: Float, now: Long) {
        val ping = frame.ping
        val point = frame.point
        val alpha = frame.alpha
        if (alpha < 0.05f) {
            PingMarkers.forgetHeading(ping.id)
            return
        }
        val userScale = PolyPlusPartyConfig.pingMarkerScale / 100f
        val falloff = (PingMarkers.NEAR_SCALE - (frame.distance / PingMarkers.SCALE_FALLOFF).toFloat())
            .coerceIn(PingMarkers.FAR_SCALE, PingMarkers.NEAR_SCALE)
        val scale = falloff * userScale
        val half = max(PingMarkers.PIN_HALF * scale, PingMarkers.MIN_PIN_HALF.toFloat())
        val color = kindColors[ping.kind.ordinal]

        if (point.onScreen) {
            PingMarkers.forgetHeading(ping.id)
            val cx = point.x * k
            val cy = point.y * k
            pin(cx, cy, half * k, ping.kind, color, alpha)
            val t = PingMarkers.ease(PingMarkers.expansionOf(ping.id))
            val labelAlpha = alpha * (1f - t)
            if (labelAlpha >= 0.05f) {
                val label = measure(plateTexts, PingMarkers.distanceText(frame.distance), plateStyle)
                val plateTop = cy + (half + PingMarkers.LABEL_GAP) * k
                plate(cx, plateTop, label, labelAlpha)
                if (ping.replies.isNotEmpty()) {
                    replies(ping.replies, cx, plateTop + label.size.height + (PLATE_PAD_Y * 2 + PingMarkers.ROW_GAP) * k, labelAlpha)
                }
            }
            if (alpha * t >= 0.05f) card(frame, cx, cy, half * k, t, now)
            return
        }

        val angle = PingMarkers.heading(ping.id, atan2(point.dirY, point.dirX), now)
        val dirX = cos(angle)
        val dirY = sin(angle)
        val reach = half + PingMarkers.CHEVRON_GAP + PingMarkers.CHEVRON_LENGTH * scale
        val label = measure(plateTexts, PingMarkers.distanceText(frame.distance), plateStyle)
        val labelHeight = label.size.height / k + PLATE_PAD_Y * 2
        val labelHalf = label.size.width / k / 2f + PLATE_PAD_X
        val marginX = PingMarkers.EDGE_MARGIN + max(reach, labelHalf)
        val rowSpace = if (ping.replies.isEmpty()) 0f else PingMarkers.ROW_GAP + REPLY_ROW_HEIGHT
        val marginY = PingMarkers.EDGE_MARGIN + reach + labelHeight + rowSpace
        val (edgeX, edgeY) = PingMarkers.clampToEdge(dirX, dirY, gw, gh, marginX, marginY)
        val cx = edgeX * k
        val cy = edgeY * k

        pin(cx, cy, half * k, ping.kind, color, alpha)
        val edge = half / (abs(dirX) + abs(dirY))
        chevron(cx, cy, dirX, dirY, edge * k, scale, color, alpha)
        val labelTop = if (dirY > PingMarkers.LABEL_FLIP) cy - (half + PingMarkers.LABEL_GAP + labelHeight) * k else cy + (half + PingMarkers.LABEL_GAP) * k
        plate(cx, labelTop, label, alpha)
        if (ping.replies.isNotEmpty()) {
            val rowTop = if (dirY > PingMarkers.LABEL_FLIP) labelTop - rowSpace * k else labelTop + (labelHeight + PingMarkers.ROW_GAP) * k
            replies(ping.replies, cx, rowTop, alpha)
        }
    }

    private fun DrawScope.replies(replies: List<PingResponse>, cx: Float, top: Float, alpha: Float) {
        val heads = replies.size <= PingMarkers.MAX_HEADS
        val groups = if (heads) emptyList() else PingMarkers.replyGroups(replies)
        val cell = PingMarkers.HEAD_CELL * k
        val iconSide = PingFaces.SIDE * k
        val iconGap = COUNT_GAP * k
        val counts = groups.map { (_, count) -> measure(plateTexts, count.toString(), plateStyle) }
        val content = if (heads) {
            replies.size * cell + (replies.size - 1) * PingMarkers.HEAD_GAP * k
        } else {
            var sum = (groups.size - 1) * PingMarkers.GROUP_GAP * k
            for (layout in counts) sum += iconSide + iconGap + layout.size.width
            sum
        }
        val padX = REPLY_PAD_X * k
        val h = REPLY_ROW_HEIGHT * k
        val w = content + padX * 2
        val left = cx - w / 2f
        val radius = CornerRadius(min(themeRadius, h / 2f))
        drawRoundRect(Color.Black, Offset(left, top + SHADOW_DROP * k), Size(w, h), radius, alpha = alpha * PLATE_SHADOW_ALPHA)
        drawRoundRect(plateSurface, Offset(left, top), Size(w, h), radius, alpha = alpha)
        var x = left + padX
        val y = top + (h - cell) / 2f
        if (heads) {
            val headRadius = CornerRadius(min(themeRadius, cell / 4f))
            val border = HEAD_BORDER * k
            for (response in replies) {
                drawRoundRect(replyColors[response.reply.ordinal], Offset(x, y), Size(cell, cell), headRadius, alpha = alpha)
                val inner = cell - border * 2
                val face = PingFaces.bitmap(response.from)
                if (face != null) {
                    drawImage(
                        face,
                        IntOffset.Zero,
                        IntSize(face.width, face.height),
                        IntOffset((x + border).roundToInt(), (y + border).roundToInt()),
                        IntSize(inner.roundToInt(), inner.roundToInt()),
                        alpha = alpha,
                        filterQuality = FilterQuality.None,
                    )
                } else {
                    drawRect(Color(PingFaces.placeholder(response.from)), Offset(x + border, y + border), Size(inner, inner), alpha = alpha)
                }
                x += cell + PingMarkers.HEAD_GAP * k
            }
            return
        }
        for (i in groups.indices) {
            val reply = groups[i].first
            val layout = counts[i]
            translate(x, top + (h - iconSide) / 2f) {
                drawReplyIcon(reply, Size(iconSide, iconSide), replyColors[reply.ordinal].copy(alpha = alpha), false)
            }
            x += iconSide + iconGap
            drawText(layout, textPrimary, Offset(x, top + (h - layout.size.height) / 2f), alpha)
            x += layout.size.width + PingMarkers.GROUP_GAP * k
        }
    }

    private fun cornerFor(half: Float): Float {
        val side = half * SQRT2
        return themeRadius.coerceIn(side * MIN_PIN_CORNER, side * MAX_PIN_CORNER)
    }

    private fun DrawScope.pin(cx: Float, cy: Float, half: Float, kind: PingKind, color: Color, alpha: Float) {
        val corner = cornerFor(half)
        val spread = SHADOW_SPREAD * k
        translate(cx, cy + SHADOW_DROP * k) {
            for (i in 1..SHADOW_LAYERS) {
                drawPath(pins.get(half + i * spread * SQRT2, corner + i * spread), Color.Black, alpha = alpha * SHADOW_ALPHA)
            }
        }
        translate(cx, cy) {
            drawPath(pins.get(half, corner), surface, alpha = alpha)
            val inset = ring / 2f
            drawPath(pins.get(half - inset * SQRT2, max(0f, corner - inset)), color, alpha = alpha, style = ringStroke)
            val side = half * ICON_SHARE
            translate(-side / 2f, -side / 2f) {
                drawPingIcon(kind, Size(side, side), iconTint.copy(alpha = iconTint.alpha * alpha), false)
            }
        }
    }

    private fun DrawScope.chevron(cx: Float, cy: Float, dx: Float, dy: Float, edge: Float, scale: Float, color: Color, alpha: Float) {
        val base = edge + PingMarkers.CHEVRON_GAP * k
        val length = PingMarkers.CHEVRON_LENGTH * scale * k
        val halfWidth = PingMarkers.CHEVRON_HALF_WIDTH * scale * k
        val px = -dy
        val py = dx
        val backX = cx + dx * base
        val backY = cy + dy * base
        chevron.reset()
        chevron.moveTo(cx + dx * (base + length), cy + dy * (base + length))
        chevron.lineTo(backX + px * halfWidth, backY + py * halfWidth)
        chevron.lineTo(backX - px * halfWidth, backY - py * halfWidth)
        chevron.close()
        translate(0f, SHADOW_DROP * k) {
            drawPath(chevron, Color.Black, alpha = alpha * CHEVRON_SHADOW_ALPHA)
        }
        drawPath(chevron, color, alpha = alpha)
        drawPath(chevron, color, alpha = alpha, style = chevronStroke)
    }

    private fun DrawScope.plate(cx: Float, top: Float, label: TextLayoutResult, alpha: Float) {
        val padX = PLATE_PAD_X * k
        val padY = PLATE_PAD_Y * k
        val w = label.size.width + padX * 2
        val h = label.size.height + padY * 2
        val left = cx - w / 2f
        val radius = CornerRadius(min(themeRadius, h / 2f))
        drawRoundRect(Color.Black, Offset(left, top + SHADOW_DROP * k), Size(w, h), radius, alpha = alpha * PLATE_SHADOW_ALPHA)
        drawRoundRect(plateSurface, Offset(left, top), Size(w, h), radius, alpha = alpha)
        drawText(label, textPrimary, Offset(left + padX, top + padY), alpha)
    }

    private fun DrawScope.card(frame: PingMarkers.Frame, cx: Float, cy: Float, half: Float, t: Float, now: Long) {
        val ping = frame.ping
        val live = if (ping.target?.type == PingTargetType.ITEM) PingMarkers.itemOf(frame.entity) else null
        val icon = live ?: ping.target?.takeIf { it.type == PingTargetType.BLOCK }?.id?.let(PingMarkers::blockStack)
        val lines = PingMarkers.cardLines(frame, live, now)
        val layouts = ArrayList<TextLayoutResult>(lines.size)
        var textWidth = 0f
        var textHeight = 0f
        val lineGap = CARD_LINE_GAP * k
        for (i in lines.indices) {
            val layout = if (i == 0) measure(titleTexts, lines[i].first, titleStyle) else measure(detailTexts, lines[i].first, detailStyle)
            layouts += layout
            textWidth = max(textWidth, layout.size.width.toFloat())
            textHeight += layout.size.height + if (i > 0) lineGap else 0f
        }
        val padX = CARD_PAD_X * k
        val padY = CARD_PAD_Y * k
        val accent = ACCENT_WIDTH * k
        val accentSpace = accent + ACCENT_GAP * k
        val iconSide = PingMarkers.ICON_SIZE * k
        val iconSpace = if (icon != null) iconSide + PingMarkers.ICON_GAP * k else 0f
        val contentHeight = if (icon != null) max(textHeight, iconSide) else textHeight
        val cardWidth = padX * 2 + accentSpace + iconSpace + textWidth
        val cardHeight = padY * 2 + contentHeight
        val margin = PingMarkers.CARD_MARGIN * k
        val gap = PingMarkers.LABEL_GAP * k

        var top = cy + half + gap
        if (top + cardHeight > size.height - margin) top = cy - half - gap - cardHeight
        top = top.coerceIn(margin, max(margin, size.height - margin - cardHeight))
        val left = (cx - cardWidth / 2f).coerceIn(margin, max(margin, size.width - margin - cardWidth))

        val alpha = frame.alpha * t
        val grow = PingMarkers.CARD_START_SCALE + (1f - PingMarkers.CARD_START_SCALE) * t
        val anchorX = cx.coerceIn(left, left + cardWidth)
        val anchorY = if (top > cy) top else top + cardHeight
        val radius = CornerRadius(min(themeRadius, cardHeight / 2f))
        val color = kindColors[ping.kind.ordinal]

        scale(grow, grow, Offset(anchorX, anchorY)) {
            drawRoundRect(Color.Black, Offset(left, top + SHADOW_DROP * k), Size(cardWidth, cardHeight), radius, alpha = alpha * CARD_SHADOW_ALPHA)
            drawRoundRect(cardSurface, Offset(left, top), Size(cardWidth, cardHeight), radius, alpha = alpha)
            drawRoundRect(
                color,
                Offset(left + padX, top + padY),
                Size(accent, cardHeight - padY * 2),
                CornerRadius(accent / 2f),
                alpha = alpha,
            )
            val contentX = left + padX + accentSpace
            val contentY = top + padY
            if (icon != null && alpha >= 0.5f) {
                val ix = contentX
                val iy = contentY + (contentHeight - iconSide) / 2f
                PingMarkersCompose.placeItem(
                    icon,
                    (anchorX + (ix - anchorX) * grow) / k,
                    (anchorY + (iy - anchorY) * grow) / k,
                    grow,
                )
            }
            val textX = contentX + iconSpace
            var y = contentY + (contentHeight - textHeight) / 2f
            for (i in layouts.indices) {
                val layout = layouts[i]
                val tint = when {
                    i == 0 -> textPrimary
                    lines[i].second == PingMarkers.HINT -> textSecondary.copy(alpha = textSecondary.alpha * HINT_ALPHA)
                    else -> textSecondary
                }
                drawText(layout, tint, Offset(textX, y), alpha)
                y += layout.size.height + lineGap
            }
        }
    }

    private companion object {
        const val SQRT2 = 1.4142135f
        const val PIN_SURFACE_ALPHA = 0.92f
        const val PLATE_SURFACE_ALPHA = 0.82f
        const val CARD_SURFACE_ALPHA = 0.9f
        const val RING_DP = 2f
        const val CHEVRON_STROKE = 1.2f
        const val PLATE_FONT = 7f
        const val TITLE_FONT = 7.5f
        const val DETAIL_FONT = 6.5f
        const val TEXT_CACHE_LIMIT = 64
        const val MIN_PIN_CORNER = 0.14f
        const val MAX_PIN_CORNER = 0.3f
        const val SHADOW_LAYERS = 3
        const val SHADOW_SPREAD = 0.6f
        const val SHADOW_DROP = 1f
        const val SHADOW_ALPHA = 0.1f
        const val CHEVRON_SHADOW_ALPHA = 0.3f
        const val PLATE_SHADOW_ALPHA = 0.18f
        const val CARD_SHADOW_ALPHA = 0.22f
        const val ICON_SHARE = 0.9f
        const val PLATE_PAD_X = 4.5f
        const val PLATE_PAD_Y = 2f
        const val CARD_PAD_X = 5f
        const val CARD_PAD_Y = 4f
        const val CARD_LINE_GAP = 1.5f
        const val ACCENT_WIDTH = 2f
        const val ACCENT_GAP = 4f
        const val HINT_ALPHA = 0.7f
        const val REPLY_PAD_X = 3f
        const val REPLY_ROW_HEIGHT = PingMarkers.HEAD_CELL + 3f
        const val HEAD_BORDER = 1f
        const val COUNT_GAP = 1.5f
    }
}

private class PathCache {
    private val keys = IntArray(SIZE) { EMPTY }
    private val paths = arrayOfNulls<Path>(SIZE)
    private var count = 0

    fun clear() {
        keys.fill(EMPTY)
        paths.fill(null)
        count = 0
    }

    fun get(half: Float, corner: Float): Path {
        val hq = (half * QUANT).roundToInt().coerceIn(0, 0xFFFF)
        val cq = (corner * QUANT).roundToInt().coerceIn(0, 0x7FFF)
        val key = (cq shl 16) or hq
        var i = slot(key)
        while (true) {
            val existing = keys[i]
            if (existing == key) return paths[i]!!
            if (existing == EMPTY) break
            i = (i + 1) and (SIZE - 1)
        }
        if (count >= SIZE * 3 / 4) {
            clear()
            i = slot(key)
        }
        val path = diamondPath(hq / QUANT, cq / QUANT)
        keys[i] = key
        paths[i] = path
        count++
        return path
    }

    private fun slot(key: Int): Int = (key * -0x61c88647) ushr (32 - BITS)

    private fun diamondPath(half: Float, corner: Float): Path {
        val path = Path()
        if (half <= 0f) return path
        val r = corner.coerceIn(0f, half * SQRT_HALF)
        val d = r
        val vx = floatArrayOf(0f, half, 0f, -half)
        val vy = floatArrayOf(-half, 0f, half, 0f)
        for (i in 0 until 4) {
            val prev = (i + 3) % 4
            val next = (i + 1) % 4
            val inX = (vx[prev] - vx[i]) / (half * SQRT2)
            val inY = (vy[prev] - vy[i]) / (half * SQRT2)
            val outX = (vx[next] - vx[i]) / (half * SQRT2)
            val outY = (vy[next] - vy[i]) / (half * SQRT2)
            val ax = vx[i] + inX * d
            val ay = vy[i] + inY * d
            val bx = vx[i] + outX * d
            val by = vy[i] + outY * d
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
            path.cubicTo(
                ax + (vx[i] - ax) * KAPPA, ay + (vy[i] - ay) * KAPPA,
                bx + (vx[i] - bx) * KAPPA, by + (vy[i] - by) * KAPPA,
                bx, by,
            )
        }
        path.close()
        return path
    }

    private companion object {
        const val BITS = 9
        const val SIZE = 1 shl BITS
        const val EMPTY = -1
        const val QUANT = 2f
        const val KAPPA = 0.5523f
        const val SQRT2 = 1.4142135f
        const val SQRT_HALF = 0.70710677f
    }
}
