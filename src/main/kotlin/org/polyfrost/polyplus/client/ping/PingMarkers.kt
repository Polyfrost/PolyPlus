@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.gui.isMinecraftStyle
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

//? if > 1.8.9 {
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
//?} else {
/*import net.minecraft.block.Block
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
*///?}

object PingMarkers {
    private const val SQRT2 = 1.4142135f
    private const val FADE_IN_MS = 150L
    private const val FADE_OUT_MS = 500L
    internal const val EDGE_MARGIN = 6
    internal const val PIN_HALF = 10f
    internal const val MIN_PIN_HALF = 8
    private const val RING_WIDTH = 2
    internal const val CHEVRON_GAP = 2
    internal const val CHEVRON_LENGTH = 5f
    internal const val CHEVRON_HALF_WIDTH = 4.5f
    internal const val LABEL_GAP = 3
    internal const val LABEL_FLIP = 0.6f
    internal const val PLATE_PAD_X = 3
    internal const val PLATE_PAD_Y = 2
    private const val HEADING_EASE_MS = 70f
    internal const val NEAR_SCALE = 1.15f
    internal const val FAR_SCALE = 0.75f
    internal const val SCALE_FALLOFF = 160.0
    private const val HOVER_ENTER = 24f
    private const val HOVER_EXIT = 30f
    private const val EXPAND_MS = 100f
    internal const val CARD_START_SCALE = 0.9f
    private const val CARD_PAD_X = 4
    private const val CARD_PAD_Y = 3
    internal const val CARD_MARGIN = 4
    private const val ACCENT_WIDTH = 2
    internal const val ICON_SIZE = 16
    internal const val ICON_GAP = 4
    private const val MIN_HEIGHT_DELTA = 2.0
    private const val SURFACE = 0xE6141418.toInt()
    private const val PLATE = 0xB0101014.toInt()
    private const val CARD = 0xD8101014.toInt()
    private const val SHADOW = 0x66000000
    private const val ICON_SHADOW = 0xA0000000.toInt()
    private const val CORNER_ROUNDING = 0.2f
    internal const val WHITE = 0xFFFFFF
    private const val DETAIL = 0xC8C8CC
    internal const val HINT = 0x8E8E96
    internal const val MAX_HEADS = 3
    internal const val ROW_GAP = 2
    internal const val HEAD_CELL = PingFaces.SIDE + 2
    internal const val HEAD_GAP = 2
    internal const val GROUP_GAP = 4
    private const val ROW_PAD_X = 2
    private const val ROW_PAD_Y = 1
    private const val REPLY_ROW_HEIGHT = HEAD_CELL + ROW_PAD_Y * 2
    private const val MAX_REPLY_LINES = 4
    private const val NETWORK_MATCH = 24.0

    private val lastPositions = HashMap<String, DoubleArray>()
    private val requestedNames = HashSet<String>()
    private val headings = HashMap<String, Heading>()
    private val expansion = HashMap<String, Float>()
    private val blockStacks = HashMap<String, ItemStack?>()
    private var hoveredId: String? = null
    private var lastFrameAt = 0L

    @JvmStatic
    fun render(graphics: PingGraphics?, partialTicks: Float) {
        PingInput.ensureStarted()
        val pings = visiblePings()
        if (pings.isEmpty()) {
            if (lastPositions.isNotEmpty()) lastPositions.clear()
            if (headings.isNotEmpty()) headings.clear()
            if (expansion.isNotEmpty()) expansion.clear()
            hoveredId = null
            lastFrameAt = 0L
            return
        }
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        if (mc.gui.hud.isHidden) return
        //?} else {
        /*if (mc.options.hideGui) return
        *///?}
        val canvas = PingCanvas(graphics)
        val entities = trackedEntities(pings)
        val now = System.currentTimeMillis()
        val viewer = viewerPosition(partialTicks) ?: return
        val ids = pings.mapTo(HashSet()) { it.id }
        lastPositions.keys.retainAll(ids)
        headings.keys.retainAll(ids)
        expansion.keys.retainAll(ids)
        val frames = ArrayList<Frame>(pings.size)
        for (ping in pings) {
            val pos = position(ping, entities, partialTicks)
            val point = PingProjection.project(pos[0], pos[1], pos[2], partialTicks) ?: continue
            val dx = pos[0] - viewer[0]
            val dy = pos[1] - viewer[1]
            val dz = pos[2] - viewer[2]
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            val entity = entities[ping.id]
            frames += Frame(ping, point, distance, pos[3] - viewer[1], entity, alpha(ping, now))
        }
        val hovered = pickHovered(frames)
        hoveredId = hovered?.ping?.id
        advanceExpansion(frames, now)
        val ordered = frames.sortedBy { if (it === hovered) 2f else expansion[it.ping.id] ?: 0f }
        if (OneConfigTheme.active?.isMinecraftStyle != true && PingMarkersCompose.draw(canvas, graphics, ordered, now)) return
        for (frame in ordered) drawMarker(canvas, frame, now)
    }

    internal fun visiblePings(): List<Ping> {
        if (!PolyPlusPartyConfig.pingsEnabled) return emptyList()
        val all = PartyPings.pings.value
        return if (PolyPlusPartyConfig.pingsShowOthers) all else all.filter { it.own }
    }

    internal fun screenPosition(ping: Ping, partialTicks: Float): ScreenPoint? {
        val pos = position(ping, trackedEntities(listOf(ping)), partialTicks)
        return PingProjection.project(pos[0], pos[1], pos[2], partialTicks)
    }

    internal class Frame(
        val ping: Ping,
        val point: ScreenPoint,
        val distance: Double,
        val heightDelta: Double,
        val entity: Entity?,
        val alpha: Float,
    )

    internal fun expansionOf(id: String): Float = expansion[id] ?: 0f

    internal fun forgetHeading(id: String) {
        headings.remove(id)
    }

    private fun alpha(ping: Ping, now: Long): Float {
        if (Motion.reduced) return 1f
        val start = ping.expiresAt - PartyPings.LIFETIME_MS
        val fadeIn = ((now - start).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        val fadeOut = ((ping.expiresAt - now).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }

    private fun hoverDistance(frame: Frame, centerX: Float, centerY: Float): Float {
        if (!frame.point.onScreen || frame.alpha < 0.05f) return Float.MAX_VALUE
        val dx = frame.point.x.roundToInt() - centerX
        val dy = frame.point.y.roundToInt() - centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun pickHovered(frames: List<Frame>): Frame? {
        val centerX = PingProjection.guiWidth() / 2f
        val centerY = PingProjection.guiHeight() / 2f
        val userScale = PolyPlusPartyConfig.pingMarkerScale / 100f
        val current = hoveredId?.let { id -> frames.firstOrNull { it.ping.id == id } }
        if (current != null && hoverDistance(current, centerX, centerY) <= HOVER_EXIT * userScale) return current
        val nearest = frames.minByOrNull { hoverDistance(it, centerX, centerY) } ?: return null
        return nearest.takeIf { hoverDistance(it, centerX, centerY) <= HOVER_ENTER * userScale }
    }

    private fun advanceExpansion(frames: List<Frame>, now: Long) {
        val step = if (lastFrameAt == 0L) 0f else (now - lastFrameAt).coerceIn(0L, 100L).toFloat() / EXPAND_MS
        lastFrameAt = now
        for (frame in frames) {
            val id = frame.ping.id
            val target = if (id == hoveredId) 1f else 0f
            val current = expansion[id] ?: 0f
            val next = when {
                Motion.reduced -> target
                target > current -> minOf(target, current + step)
                else -> maxOf(target, current - step)
            }
            if (next <= 0f) expansion.remove(id) else expansion[id] = next
        }
    }

    private fun trackedEntities(pings: List<Ping>): Map<String, Entity> {
        val wanted = pings.mapNotNullTo(HashSet()) { it.target?.entity }
        if (wanted.isEmpty() && pings.none { it.target?.entityId != null }) return emptyMap()
        val byUuid = HashMap<String, Entity>()
        val found = HashMap<String, Entity>()
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val level = mc.level ?: return found
        if (wanted.isNotEmpty()) for (entity in level.entitiesForRendering()) {
            if (entity.isRemoved) continue
        //?} else {
        /*val world = mc.world ?: return found
        if (wanted.isNotEmpty()) for (entity in world.entities) {
            if (entity.removed) continue
        *///?}
            val id = entity.uuid.toString()
            if (id in wanted) byUuid[id] = entity
        }
        for (ping in pings) {
            val target = ping.target ?: continue
            val entity = target.entity?.let { byUuid[it] } ?: target.entityId?.let { networkEntity(ping, it) } ?: continue
            found[ping.id] = entity
        }
        return found
    }

    private fun networkEntity(ping: Ping, id: Int): Entity? {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val raw = mc.level?.getEntity(id)?.takeUnless { it.isRemoved } ?: return null
        val entity = PingTargeting.owner(raw)
        val top = entity.y + entity.bbHeight + 0.25
        //?} else {
        /*val raw = mc.world?.getEntity(id)?.takeUnless { it.removed } ?: return null
        val entity = PingTargeting.owner(raw)
        val top = entity.y + entity.height + 0.25
        *///?}
        val last = lastPositions[ping.id]
        val dx = entity.x - (last?.get(0) ?: ping.x)
        val dy = top - (last?.get(1) ?: ping.y)
        val dz = entity.z - (last?.get(2) ?: ping.z)
        return entity.takeIf { dx * dx + dy * dy + dz * dz <= NETWORK_MATCH * NETWORK_MATCH }
    }

    private fun position(ping: Ping, entities: Map<String, Entity>, partialTicks: Float): DoubleArray {
        val entity = entities[ping.id]
        if (entity != null) {
            //? if > 1.8.9 {
            val at = entity.getPosition(partialTicks)
            val pos = doubleArrayOf(at.x, at.y + entity.bbHeight + 0.25, at.z, at.y + entity.bbHeight / 2.0)
            //?} else {
            /*val feet = entity.lastY + (entity.y - entity.lastY) * partialTicks
            val pos = doubleArrayOf(
                entity.lastX + (entity.x - entity.lastX) * partialTicks,
                feet + entity.height + 0.25,
                entity.lastZ + (entity.z - entity.lastZ) * partialTicks,
                feet + entity.height / 2.0,
            )
            *///?}
            lastPositions[ping.id] = pos
            return pos
        }
        return lastPositions[ping.id] ?: doubleArrayOf(ping.x, ping.y, ping.z, ping.y)
    }

    private fun viewerPosition(partialTicks: Float): DoubleArray? {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val camera = mc.cameraEntity ?: mc.player ?: return null
        val eye = camera.getEyePosition(partialTicks)
        //?} else {
        /*val camera = mc.camera ?: mc.player ?: return null
        val eye = camera.getEyePosition(partialTicks)
        *///?}
        return doubleArrayOf(eye.x, eye.y, eye.z)
    }

    private fun senderName(ping: Ping): String? = if (ping.own) null else nameOf(ping.from)

    private fun nameOf(from: String): String? {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val players = mc.level?.players().orEmpty()
        for (player in players) {
            if (PartyRepository.samePlayer(player.uuid.toString(), from)) return player.name.string
        }
        //?} else {
        /*val players = mc.world?.players.orEmpty()
        for (player in players) {
            if (PartyRepository.samePlayer(player.uuid.toString(), from)) return player.name
        }
        *///?}
        PlayerNamesRepository.names.value[from]?.let { return it }
        if (requestedNames.add(from)) PlayerNamesRepository.resolve(listOf(from))
        return null
    }

    //? if > 1.8.9 {
    internal fun itemOf(entity: Entity?): ItemStack? = (entity as? ItemEntity)?.item?.takeUnless { it.isEmpty }

    private fun stackName(stack: ItemStack): String = stack.hoverName.string

    private fun stackCount(stack: ItemStack): Int = stack.count

    internal fun blockStack(id: String): ItemStack? {
        if (blockStacks.containsKey(id)) return blockStacks[id]
        val block = BuiltInRegistries.BLOCK.firstOrNull { BuiltInRegistries.BLOCK.getKey(it).toString() == id }
        val stack = block?.let { ItemStack(it.asItem()) }?.takeUnless { it.isEmpty }
        blockStacks[id] = stack
        return stack
    }
    //?} else {
    /*internal fun itemOf(entity: Entity?): ItemStack? = (entity as? ItemEntity)?.item

    private fun stackName(stack: ItemStack): String = stack.hoverName

    private fun stackCount(stack: ItemStack): Int = stack.size

    internal fun blockStack(id: String): ItemStack? {
        if (blockStacks.containsKey(id)) return blockStacks[id]
        val item: Item? = Block.byKey(id)?.let { Item.byBlock(it) }
        val stack = item?.let { ItemStack(it) }
        blockStacks[id] = stack
        return stack
    }
    *///?}

    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    private fun scaled(argb: Int, alpha: Float): Int {
        val base = (argb ushr 24) and 0xFF
        return ((base * alpha.coerceIn(0f, 1f)).roundToInt() shl 24) or (argb and 0xFFFFFF)
    }

    internal fun distanceText(distance: Double): String = "${distance.roundToInt()}m"

    private fun ageText(ping: Ping, now: Long): String {
        val seconds = ((now - (ping.expiresAt - PartyPings.LIFETIME_MS)) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 1L -> "just now"
            seconds < 60L -> "${seconds}s ago"
            else -> "${seconds / 60L}m ago"
        }
    }

    private fun heightText(delta: Double): String? {
        if (abs(delta) < MIN_HEIGHT_DELTA) return null
        val arrow = if (delta > 0) "↑" else "↓"
        return "$arrow${abs(delta).roundToInt()}"
    }

    internal fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun drawMarker(canvas: PingCanvas, frame: Frame, now: Long) {
        val ping = frame.ping
        val point = frame.point
        val distance = frame.distance
        val alpha = frame.alpha
        if (alpha < 0.05f) {
            headings.remove(ping.id)
            return
        }
        val userScale = PolyPlusPartyConfig.pingMarkerScale / 100f
        val falloff = (NEAR_SCALE - (distance / SCALE_FALLOFF).toFloat()).coerceIn(FAR_SCALE, NEAR_SCALE)
        val scale = falloff * userScale
        val ring = withAlpha(ping.kind.color, alpha)

        if (point.onScreen) {
            headings.remove(ping.id)
            val cx = point.x.roundToInt()
            val cy = point.y.roundToInt()
            val half = (PIN_HALF * scale).roundToInt().coerceAtLeast(MIN_PIN_HALF)
            diamond(canvas, cx, cy + 1, half + 1, scaled(SHADOW, alpha))
            diamond(canvas, cx, cy, half, ring)
            diamond(canvas, cx, cy, half - RING_WIDTH - 1, scaled(SURFACE, alpha))
            drawIcon(canvas, ping.kind, cx, cy, half, alpha)
            val t = ease(expansion[ping.id] ?: 0f)
            val labelAlpha = alpha * (1f - t)
            if (labelAlpha >= 0.05f) {
                val plateTop = cy + half + LABEL_GAP
                drawPlate(canvas, cx, plateTop, listOf(distanceText(distance) to WHITE), labelAlpha)
                if (ping.replies.isNotEmpty()) {
                    drawReplies(canvas, ping.replies, cx, plateTop + canvas.lineHeight + PLATE_PAD_Y * 2 + 1 + ROW_GAP, labelAlpha)
                }
            }
            if (alpha * t >= 0.05f) drawCard(canvas, frame, cx, cy, half, t, now)
            return
        }

        val width = PingProjection.guiWidth()
        val height = PingProjection.guiHeight()
        val angle = heading(ping.id, atan2(point.dirY, point.dirX), now)
        val dirX = cos(angle)
        val dirY = sin(angle)
        val radius = (PIN_HALF * scale).roundToInt().coerceAtLeast(MIN_PIN_HALF)
        val reach = radius + CHEVRON_GAP + (CHEVRON_LENGTH * scale).roundToInt()
        val label = distanceText(distance)
        val labelHeight = canvas.lineHeight + PLATE_PAD_Y * 2
        val labelHalf = canvas.width(label) / 2 + PLATE_PAD_X
        val marginX = EDGE_MARGIN + maxOf(reach, labelHalf)
        val rowSpace = if (ping.replies.isEmpty()) 0 else ROW_GAP + 1 + REPLY_ROW_HEIGHT
        val marginY = EDGE_MARGIN + reach + labelHeight + rowSpace
        val (edgeX, edgeY) = clampToEdge(dirX, dirY, width, height, marginX.toFloat(), marginY.toFloat())
        val cx = edgeX.roundToInt()
        val cy = edgeY.roundToInt()

        diamond(canvas, cx, cy + 1, radius + 1, scaled(SHADOW, alpha))
        diamond(canvas, cx, cy, radius, ring)
        diamond(canvas, cx, cy, radius - RING_WIDTH - 1, scaled(SURFACE, alpha))
        drawIcon(canvas, ping.kind, cx, cy, radius, alpha)
        val edge = radius / (abs(dirX) + abs(dirY))
        drawChevron(canvas, cx.toFloat(), cy.toFloat(), dirX, dirY, edge, scale, ring, alpha)

        val labelTop = if (dirY > LABEL_FLIP) cy - radius - LABEL_GAP - labelHeight else cy + radius + LABEL_GAP
        drawPlate(canvas, cx, labelTop, listOf(label to WHITE), alpha)
        if (ping.replies.isNotEmpty()) {
            val rowTop = if (dirY > LABEL_FLIP) labelTop - 1 - ROW_GAP - REPLY_ROW_HEIGHT else labelTop + labelHeight + 1 + ROW_GAP
            drawReplies(canvas, ping.replies, cx, rowTop, alpha)
        }
    }

    internal fun replyGroups(replies: List<PingResponse>): List<Pair<PingReply, Int>> =
        WHEEL_REPLIES.mapNotNull { reply -> replies.count { it.reply == reply }.takeIf { it > 0 }?.let { reply to it } }

    private fun drawReplies(canvas: PingCanvas, replies: List<PingResponse>, cx: Int, top: Int, alpha: Float) {
        val heads = replies.size <= MAX_HEADS
        val groups = if (heads) emptyList() else replyGroups(replies)
        val content = if (heads) {
            replies.size * HEAD_CELL + (replies.size - 1) * HEAD_GAP
        } else {
            groups.sumOf { (_, count) -> PingFaces.SIDE + 1 + canvas.width(count.toString()) } + (groups.size - 1) * GROUP_GAP
        }
        val half = content / 2 + ROW_PAD_X
        val bottom = top + REPLY_ROW_HEIGHT
        val plate = scaled(PLATE, alpha)
        canvas.fill(cx - half, top + 1, cx + half, bottom - 1, plate)
        canvas.fill(cx - half + 1, top, cx + half - 1, top + 1, plate)
        canvas.fill(cx - half + 1, bottom - 1, cx + half - 1, bottom, plate)
        var x = cx - content / 2
        val y = top + ROW_PAD_Y
        if (heads) {
            for (response in replies) {
                drawHead(canvas, response, x, y, alpha)
                x += HEAD_CELL + HEAD_GAP
            }
            return
        }
        for ((reply, count) in groups) {
            val color = withAlpha(reply.color, alpha)
            val iconX = x + PingFaces.SIDE / 2
            val iconY = y + HEAD_CELL / 2
            replyIconRects(reply) { x0, y0, x1, y1 -> canvas.fill(iconX + x0, iconY + y0, iconX + x1, iconY + y1, color) }
            val text = count.toString()
            canvas.text(text, x + PingFaces.SIDE + 1, y + 1, withAlpha(WHITE, alpha))
            x += PingFaces.SIDE + 1 + canvas.width(text) + GROUP_GAP
        }
    }

    private fun drawHead(canvas: PingCanvas, response: PingResponse, x: Int, y: Int, alpha: Float) {
        canvas.fill(x, y, x + HEAD_CELL, y + HEAD_CELL, withAlpha(response.reply.color, alpha))
        val face = PingFaces.pixels(response.from)
        if (face == null) {
            canvas.fill(x + 1, y + 1, x + HEAD_CELL - 1, y + HEAD_CELL - 1, scaled(PingFaces.placeholder(response.from), alpha))
            return
        }
        for (i in face.indices) {
            val argb = face[i]
            if ((argb ushr 24) == 0) continue
            val px = x + 1 + i % PingFaces.SIDE
            val py = y + 1 + i / PingFaces.SIDE
            canvas.fill(px, py, px + 1, py + 1, scaled(argb, alpha))
        }
    }

    internal fun replierName(player: String): String =
        if (PartyRepository.isSelf(player)) "You" else nameOf(player) ?: "Party member"

    internal fun cardLines(frame: Frame, live: ItemStack?, now: Long): List<Pair<String, Int>> {
        val ping = frame.ping
        val target = if (live != null) {
            val name = stackName(live)
            val count = stackCount(live)
            if (count > 1) "$name ×$count" else name
        } else {
            PingTargeting.localLabel(ping.label)
        }
        val title = if (target.isNullOrBlank()) ping.kind.title else "${ping.kind.title} · $target"
        val sender = if (ping.own) "You" else senderName(ping)
        val lines = ArrayList<Pair<String, Int>>(4)
        lines += title to WHITE
        lines += listOfNotNull(sender, ageText(ping, now)).joinToString(" · ") to DETAIL
        lines += listOfNotNull(distanceText(frame.distance), heightText(frame.heightDelta)).joinToString(" · ") to DETAIL
        for (response in ping.replies.take(MAX_REPLY_LINES)) lines += "${replierName(response.from)}: ${response.reply.title}" to DETAIL
        if (ping.replies.size > MAX_REPLY_LINES) lines += "+${ping.replies.size - MAX_REPLY_LINES} more" to DETAIL
        if (ping.own) {
            lines += "Tap Ping to remove" to HINT
        } else if (PartyPings.canReply(ping)) {
            val undo = ping.replies.any { PartyRepository.isSelf(it.from) && it.reply == PingReply.COMING }
            lines += (if (undo) "Tap Ping to take back your reply" else "Tap Ping to reply, hold for more") to HINT
        }
        return lines
    }

    private fun drawCard(canvas: PingCanvas, frame: Frame, cx: Int, cy: Int, half: Int, t: Float, now: Long) {
        val ping = frame.ping
        val live = if (ping.target?.type == PingTargetType.ITEM) itemOf(frame.entity) else null
        val icon = live ?: ping.target?.takeIf { it.type == PingTargetType.BLOCK }?.id?.let(::blockStack)
        val lines = cardLines(frame, live, now)
        val lineStep = canvas.lineHeight + 1
        val iconSpace = if (icon != null) ICON_SIZE + ICON_GAP else 0
        val textWidth = lines.maxOf { canvas.width(it.first) }
        val textHeight = lines.size * lineStep - 1
        val contentHeight = if (icon != null) maxOf(textHeight, ICON_SIZE) else textHeight
        val cardWidth = CARD_PAD_X * 2 + ACCENT_WIDTH + iconSpace + textWidth
        val cardHeight = CARD_PAD_Y * 2 + contentHeight
        val screenWidth = PingProjection.guiWidth().toInt()
        val screenHeight = PingProjection.guiHeight().toInt()

        var top = cy + half + LABEL_GAP
        if (top + cardHeight > screenHeight - CARD_MARGIN) top = cy - half - LABEL_GAP - cardHeight
        top = top.coerceIn(CARD_MARGIN, (screenHeight - CARD_MARGIN - cardHeight).coerceAtLeast(CARD_MARGIN))
        val left = (cx - cardWidth / 2).coerceIn(CARD_MARGIN, (screenWidth - CARD_MARGIN - cardWidth).coerceAtLeast(CARD_MARGIN))

        val alpha = frame.alpha * t
        val grow = CARD_START_SCALE + (1f - CARD_START_SCALE) * t
        val animated = grow < 0.999f
        val anchorX = if (animated) cx.coerceIn(left, left + cardWidth) else 0
        val anchorY = if (animated) (if (top > cy) top else top + cardHeight) else 0
        if (animated) canvas.push(anchorX.toFloat(), anchorY.toFloat(), grow)

        val l = left - anchorX
        val tp = top - anchorY
        val r = l + cardWidth
        val b = tp + cardHeight
        val plate = scaled(CARD, alpha)
        canvas.fill(l + 1, b, r - 1, b + 1, scaled(SHADOW, alpha))
        canvas.fill(l + 1, tp, r - 1, tp + 1, plate)
        canvas.fill(l, tp + 1, r, b - 1, plate)
        canvas.fill(l + 1, b - 1, r - 1, b, plate)
        canvas.fill(l + 1, tp + 2, l + 1 + ACCENT_WIDTH, b - 2, withAlpha(ping.kind.color, alpha))

        val contentX = l + CARD_PAD_X + ACCENT_WIDTH
        val contentY = tp + CARD_PAD_Y
        if (icon != null && alpha >= 0.5f) canvas.item(icon, contentX, contentY + (contentHeight - ICON_SIZE) / 2)
        val textX = contentX + iconSpace
        val textY = contentY + (contentHeight - textHeight) / 2
        lines.forEachIndexed { i, (text, color) ->
            canvas.text(text, textX, textY + i * lineStep, withAlpha(color, alpha))
        }

        if (animated) canvas.pop()
    }

    internal fun heading(id: String, target: Float, now: Long): Float {
        val previous = headings[id]
        if (previous == null || Motion.reduced) {
            headings[id] = Heading(target, now)
            return target
        }
        val elapsed = (now - previous.at).coerceIn(0L, 250L).toFloat()
        val delta = PingProjection.wrap(target - previous.angle)
        previous.angle = PingProjection.wrap(previous.angle + delta * (1f - exp(-elapsed / HEADING_EASE_MS)))
        previous.at = now
        return previous.angle
    }

    private class Heading(var angle: Float, var at: Long)

    private fun drawIcon(canvas: PingCanvas, kind: PingKind, cx: Int, cy: Int, half: Int, alpha: Float) {
        val hole = half - RING_WIDTH - 1
        val u = (hole / 8).coerceAtLeast(1)
        val small = hole < 7
        val shadow = scaled(ICON_SHADOW, alpha)
        val color = withAlpha(WHITE, alpha)
        pingIconRects(kind, small) { x0, y0, x1, y1 ->
            canvas.fill(cx + x0 * u + 1, cy + y0 * u + 1, cx + x1 * u + 1, cy + y1 * u + 1, shadow)
        }
        pingIconRects(kind, small) { x0, y0, x1, y1 ->
            canvas.fill(cx + x0 * u, cy + y0 * u, cx + x1 * u, cy + y1 * u, color)
        }
    }

    private fun drawPlate(canvas: PingCanvas, cx: Int, top: Int, lines: List<Pair<String, Int>>, alpha: Float) {
        val visible = lines.filter { it.first.isNotEmpty() }
        if (visible.isEmpty()) return
        val half = visible.maxOf { canvas.width(it.first) } / 2 + PLATE_PAD_X
        val bottom = top + visible.size * (canvas.lineHeight + 1) + PLATE_PAD_Y * 2 - 1
        val plate = scaled(PLATE, alpha)
        canvas.fill(cx - half, top, cx + half, bottom, plate)
        canvas.fill(cx - half + 1, top - 1, cx + half - 1, top, plate)
        canvas.fill(cx - half + 1, bottom, cx + half - 1, bottom + 1, plate)
        visible.forEachIndexed { i, (text, color) ->
            canvas.text(text, cx - canvas.width(text) / 2, top + PLATE_PAD_Y + i * (canvas.lineHeight + 1), withAlpha(color, alpha))
        }
    }

    internal fun clampToEdge(dx: Float, dy: Float, width: Float, height: Float, marginX: Float, marginY: Float): Pair<Float, Float> {
        val halfW = (width / 2f - marginX).coerceAtLeast(1f)
        val halfH = (height / 2f - marginY).coerceAtLeast(1f)
        val reachX = if (abs(dx) > 1e-4f) halfW / abs(dx) else Float.MAX_VALUE
        val reachY = if (abs(dy) > 1e-4f) halfH / abs(dy) else Float.MAX_VALUE
        val reach = minOf(reachX, reachY)
        return (width / 2f + dx * reach) to (height / 2f + dy * reach)
    }

    private fun drawChevron(canvas: PingCanvas, cx: Float, cy: Float, dx: Float, dy: Float, edge: Float, scale: Float, color: Int, alpha: Float) {
        val base = edge + CHEVRON_GAP
        val length = CHEVRON_LENGTH * scale
        val halfWidth = CHEVRON_HALF_WIDTH * scale
        val px = -dy
        val py = dx
        val tipX = cx + dx * (base + length)
        val tipY = cy + dy * (base + length)
        val backX = cx + dx * base
        val backY = cy + dy * base
        val leftX = backX + px * halfWidth
        val leftY = backY + py * halfWidth
        val rightX = backX - px * halfWidth
        val rightY = backY - py * halfWidth
        triangle(canvas, tipX, tipY + 1f, leftX, leftY + 1f, rightX, rightY + 1f, scaled(SHADOW, alpha))
        triangle(canvas, tipX, tipY, leftX, leftY, rightX, rightY, color)
    }

    private fun diamond(canvas: PingCanvas, cx: Int, cy: Int, half: Int, color: Int) {
        if (half <= 0) return
        val squareTheme = OneConfigTheme.active?.isMinecraftStyle == true
        if (squareTheme) sharpDiamond(canvas, cx, cy, half, color) else roundedDiamond(canvas, cx, cy, half, color)
    }

    private fun sharpDiamond(canvas: PingCanvas, cx: Int, cy: Int, half: Int, color: Int) {
        for (row in -half until half) {
            val span = half - if (row < 0) -row - 1 else row
            if (span > 0) canvas.fill(cx - span, cy + row, cx + span, cy + row + 1, color)
        }
    }

    private fun roundedDiamond(canvas: PingCanvas, cx: Int, cy: Int, half: Int, color: Int) {
        val corner = half * CORNER_ROUNDING
        val core = half - corner * SQRT2
        for (row in -half until half) {
            val y = abs(row + 0.5f)
            var solid = 0
            while (solid < half && roundedCoverage(solid + 0.5f, y, core, corner) >= 0.999f) solid++
            if (solid > 0) canvas.fill(cx - solid, cy + row, cx + solid, cy + row + 1, color)
            var i = solid
            while (i < half) {
                val coverage = roundedCoverage(i + 0.5f, y, core, corner)
                if (coverage < 0.02f) break
                val shade = scaled(color, coverage)
                canvas.fill(cx + i, cy + row, cx + i + 1, cy + row + 1, shade)
                canvas.fill(cx - i - 1, cy + row, cx - i, cy + row + 1, shade)
                i++
            }
        }
    }

    private fun roundedCoverage(x: Float, y: Float, core: Float, corner: Float): Float {
        val sum = x + y
        val signed = if (sum <= core) {
            (sum - core) / SQRT2
        } else {
            val along = ((sum - core) / 2f).let { x - it }.coerceIn(0f, core)
            val nearX = along
            val nearY = core - along
            val dx = x - nearX
            val dy = y - nearY
            sqrt(dx * dx + dy * dy)
        }
        return (0.5f - (signed - corner)).coerceIn(0f, 1f)
    }

    private fun triangle(canvas: PingCanvas, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        val xs = floatArrayOf(x0, x1, x2)
        val ys = floatArrayOf(y0, y1, y2)
        val top = floor(minOf(y0, y1, y2)).toInt()
        val bottom = ceil(maxOf(y0, y1, y2)).toInt()
        for (row in top until bottom) {
            val y = row + 0.5f
            var left = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            for (i in 0 until 3) {
                val j = (i + 1) % 3
                val ya = ys[i]
                val yb = ys[j]
                if ((y < ya) == (y < yb)) continue
                val x = xs[i] + (y - ya) / (yb - ya) * (xs[j] - xs[i])
                left = minOf(left, x)
                right = maxOf(right, x)
            }
            if (left > right) continue
            val l = left.roundToInt()
            val r = right.roundToInt()
            if (r > l) canvas.fill(l, row, r, row + 1, color)
        }
    }
}
