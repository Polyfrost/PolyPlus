package org.polyfrost.polyplus.client.nameplate.render

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.polyfrost.plusrenderer.Nameplates
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.nameplate.NameplateEquip
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import org.polyfrost.polyplus.client.nameplate.engine.LongObjectMap
import org.polyfrost.polyplus.client.nameplate.engine.NameplateBundleInfo
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.UUID
//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.network.chat.FontDescription
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f
*///?}
//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState
//?}

interface DisplayTagState {
    fun `polyplus$displayTag`(): DisplayTagBinding?

    fun `polyplus$setDisplayTag`(binding: DisplayTagBinding?)
}

class DisplayTagBinding(
    val wearer: UUID,
    val profileName: String,
    val equip: NameplateEquip,
    val nameLine: Int,
    val own: Boolean,
    val signals: PlayerSignalRecord,
    val lines: List<Display.TextDisplay.CachedLine>,
) {
    var distanceSq: Double = 0.0
}

fun interface DisplayLineDraw {
    //? if >= 1.21.10 {
    fun submit(x: Float, y: Float, text: FormattedCharSequence, dropShadow: Boolean, color: Int, outlineColor: Int)
    //?} else {
    /*fun draw(text: FormattedCharSequence, x: Float, y: Float, dropShadow: Boolean, color: Int): Int
    *///?}
}

//? if < 1.21.10 {
/*private class HeldDisplayLine {
    var draw: DisplayLineDraw? = null
    var text: FormattedCharSequence? = null
    var x = 0f
    var y = 0f
    var dropShadow = false
    var color = 0
}
*///?}

object DisplayTags {
    private const val MAX_DEPTH = 8
    private const val MAX_BINDINGS = 256
    private const val UUID_VERSION = 4
    private const val VISIBLE_ALPHA = 26
    private const val SETTLED = 1f

    //? if >= 1.21.8 {
    private const val OPAQUE_BELOW = 0
    //?} else {
    /*private const val OPAQUE_BELOW = 4
    *///?}

    private val bindings = LongObjectMap<DisplayTagBinding>(64)

    private val queue = ArrayList<Entity>()
    private val plain = StringBuilder()
    private var plainDefaults = BooleanArray(64)
    private val sink = FormattedCharSink { _, style, codepoint ->
        val at = plain.length
        if (at == plainDefaults.size) plainDefaults = plainDefaults.copyOf(at * 2)
        plainDefaults[at] = defaultFont(style)
        plain.append(glyphChar(codepoint))
        true
    }
    private val splitter = Display.TextDisplay.LineSplitter { input, width -> split(input, width) }

    //? if >= 1.21.10 {
    private var state: TextDisplayEntityRenderState? = null
    private var entry: NameTagPending? = null
    //?} else {
    /*//? if >= 1.21.8 {
    private const val HIDDEN_ALPHA = 0
    //?} else {
    /^private const val HIDDEN_ALPHA = 4
    ^///?}

    private val tag = NameplateDrawTag()
    private val list = NameplateDrawList()
    private val layout = NameTagLayout()

    private var binding: DisplayTagBinding? = null
    private var info: Display.TextDisplay.CachedInfo? = null
    private var flags: Byte = 0
    private val held = ArrayList<HeldDisplayLine>()
    private var heldCount = 0
    *///?}

    //? if >= 1.21.4 {
    @JvmStatic
    fun bind(display: Display.TextDisplay, state: TextDisplayEntityRenderState, partialTick: Float) {
        val access = state as? DisplayTagState ?: return
        val text = state.textRenderState
        val binding = if (text == null) {
            null
        } else {
            DisplayTags.bind(display, state.cachedInfo, text.textOpacity().get(partialTick), partialTick)
        }
        binding?.distanceSq = state.distanceToCameraSq
        access.`polyplus$setDisplayTag`(binding)
    }
    //?}

    //? if >= 1.21.10 {
    @JvmStatic
    fun backgroundColor(state: TextDisplayEntityRenderState, color: Int): Int =
        if (begin(state)?.list?.ownsBackground == true) color and RGB else color

    @JvmStatic
    fun drawsBackground(state: TextDisplayEntityRenderState): Boolean = begin(state)?.list?.ownsBackground != true

    @JvmStatic
    fun submitLine(
        state: TextDisplayEntityRenderState,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        color: Int,
        outlineColor: Int,
        original: DisplayLineDraw,
    ) {
        val pending = begin(state)
        val binding = (state as? DisplayTagState)?.`polyplus$displayTag`()
        val info = state.cachedInfo
        val render = state.textRenderState
        if (pending == null || binding == null || info == null || render == null || !DisplayTags.isNameLine(binding, info, text)) {
            original.submit(x, y, text, dropShadow, color, outlineColor)
            return
        }
        this.entry = null
        val list = pending.list
        val tag = pending.tag
        try {
            pending.layout.scan(Minecraft.getInstance().font, text, tag.profileName, color, tag)
            DisplayTags.measureBlock(tag, binding, info, render.flags(), color ushr 24)
        } catch (e: RuntimeException) {
            NameTags.failDisplay(pending, e)
            original.submit(x, y, text, dropShadow, color, outlineColor)
            return
        }
        val peeked = NameTags.peekDisplay(pending)
        val moves = peeked && displayMode != Font.DisplayMode.SEE_THROUGH
        val dx = if (moves) list.plateDx else 0
        val dy = if (moves) list.plateDy else 0
        val hides = moves && DisplayTags.redraws(list, dropShadow)
        pending.matrix.set(poseStack.last().pose())
        pending.x = x - DisplayTags.nameOriginX(tag)
        pending.y = y - DisplayTags.nameOriginY(tag)
        pending.light = lightCoords
        pending.color = color
        pending.textColor = color
        pending.shadow = dropShadow
        pending.sequence = text
        pending.textX = x
        pending.textY = y
        pending.mode = displayMode
        pending.hidden = hides
        when {
            hides -> original.submit(x + dx, y + dy, text, dropShadow, color and RGB, outlineColor and RGB)
            moves -> original.submit(
                x + dx,
                y + dy,
                DisplayTags.restyle(text, list, color, dropShadow, pending.layout.nameStart, pending.layout.nameEnd),
                dropShadow,
                color,
                outlineColor,
            )
            else -> original.submit(x, y, text, dropShadow, color, outlineColor)
        }
        NameTags.measuredDisplay(pending, collector)
    }

    @JvmStatic
    fun endFrame() {
        state = null
        entry = null
    }

    private fun begin(state: TextDisplayEntityRenderState): NameTagPending? {
        if (state === this.state) return entry
        this.state = state
        val binding = (state as? DisplayTagState)?.`polyplus$displayTag`()
        entry = if (binding == null) null else NameTags.beginDisplay(binding)
        return entry
    }
    //?} else {
    /*//? if >= 1.21.4 {
    @JvmStatic
    fun begin(state: TextDisplayEntityRenderState) {
        val bound = (state as? DisplayTagState)?.`polyplus$displayTag`()
        start(bound, state.cachedInfo, state.textRenderState?.flags() ?: 0)
    }
    //?} else {
    /^@JvmStatic
    fun begin(
        display: Display.TextDisplay,
        info: Display.TextDisplay.CachedInfo,
        state: Display.TextDisplay.TextRenderState,
        partialTick: Float,
    ): Display.TextDisplay.CachedInfo {
        val bound = DisplayTags.bind(display, info, state.textOpacity().get(partialTick), partialTick)
        bound?.distanceSq = Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(display)
        start(bound, info, state.flags())
        return info
    }
    ^///?}

    @JvmStatic
    fun end() {
        binding = null
        info = null
        NameTagSupport.release(tag)
        replayHeld()
    }

    @JvmStatic
    fun backgroundColor(color: Int): Int = if (binding != null && list.ownsBackground) color and RGB else color

    @JvmStatic
    fun drawLine(
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        matrix: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        original: DisplayLineDraw,
    ): Int {
        val bound = binding
        val cached = info
        if (bound == null || cached == null) return original.draw(text, x, y, dropShadow, color)
        if (!DisplayTags.isNameLine(bound, cached, text)) {
            hold(original, text, x, y, dropShadow, color)
            return 0
        }
        binding = null
        try {
            return drawName(bound, cached, font, text, x, y, color, dropShadow, matrix, buffers, displayMode, lightCoords, original)
        } finally {
            replayHeld()
        }
    }

    private fun drawName(
        bound: DisplayTagBinding,
        cached: Display.TextDisplay.CachedInfo,
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        matrix: Matrix4f,
        buffers: MultiBufferSource,
        displayMode: Font.DisplayMode,
        lightCoords: Int,
        original: DisplayLineDraw,
    ): Int {
        try {
            layout.scan(font, text, tag.profileName, color, tag)
            DisplayTags.measureBlock(tag, bound, cached, flags, color ushr 24)
            NameTagSupport.measureAndDraw(tag, list)
            NameTags.drawList(list, matrix, buffers, x - DisplayTags.nameOriginX(tag), y - DisplayTags.nameOriginY(tag), lightCoords)
        } catch (e: RuntimeException) {
            NameTagSupport.error("A nameplate on a server name tag failed to draw", e)
            return original.draw(text, x, y, dropShadow, color)
        }
        val moves = displayMode != Font.DisplayMode.SEE_THROUGH
        val lineX = if (moves) x + list.plateDx else x
        val lineY = if (moves) y + list.plateDy else y
        if (!moves) return original.draw(text, x, y, dropShadow, color)
        if (!DisplayTags.redraws(list, dropShadow)) {
            val restyled = DisplayTags.restyle(text, list, color, dropShadow, layout.nameStart, layout.nameEnd)
            return original.draw(restyled, lineX, lineY, dropShadow, color)
        }
        val drawn = original.draw(text, lineX, lineY, dropShadow, NameTagSupport.hide(color, HIDDEN_ALPHA))
        try {
            list.baseShadow = dropShadow
            NameTagText.redraw(font, matrix, buffers, lineX, lineY, color, lightCoords, layout, list, displayMode)
        } catch (e: RuntimeException) {
            NameTagSupport.error("A nameplate on a server name tag failed to draw its letters", e)
            NameTagText.plain(font, text, lineX, lineY, color, matrix, buffers, lightCoords, displayMode)
        }
        return drawn
    }

    private fun hold(draw: DisplayLineDraw, text: FormattedCharSequence, x: Float, y: Float, dropShadow: Boolean, color: Int) {
        if (heldCount == held.size) held += HeldDisplayLine()
        val line = held[heldCount++]
        line.draw = draw
        line.text = text
        line.x = x
        line.y = y
        line.dropShadow = dropShadow
        line.color = color
    }

    private fun replayHeld() {
        val count = heldCount
        heldCount = 0
        for (i in 0 until count) {
            val line = held[i]
            val draw = line.draw
            val text = line.text
            line.draw = null
            line.text = null
            if (draw != null && text != null) draw.draw(text, line.x, line.y, line.dropShadow, line.color)
        }
    }

    private fun dropHeld() {
        for (i in 0 until heldCount) {
            held[i].draw = null
            held[i].text = null
        }
        heldCount = 0
    }

    private fun start(bound: DisplayTagBinding?, cached: Display.TextDisplay.CachedInfo?, displayFlags: Byte) {
        dropHeld()
        end()
        if (bound == null || cached == null) return
        DisplayTags.prepare(bound, tag, false)
        list.reset()
        val drawn = try {
            NameplateDrawSources.current.prepare(tag, list)
        } catch (e: RuntimeException) {
            NameTagSupport.error("A nameplate on a server name tag failed to prepare", e)
            false
        }
        if (!drawn) {
            NameTagSupport.release(tag)
            return
        }
        binding = bound
        info = cached
        flags = displayFlags
    }
    *///?}

    @JvmStatic
    fun bind(display: Display.TextDisplay, info: Display.TextDisplay.CachedInfo?, opacity: Int, partialTick: Float): DisplayTagBinding? {
        if (info == null || !display.isPassenger || !visible(opacity)) return null
        val player = rider(display) ?: return null
        val wearer = player.uuid
        if (wearer.version() != UUID_VERSION) return null
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, wearer)) return null
        val equip = NameplateWearers.resolve(wearer) ?: return null
        val profileName = player.scoreboardName
        val lines = info.lines()
        val key = display.id.toLong()
        val cached = bindings[key]
        val nameLine = if (cached != null && cached.lines === lines && cached.profileName == profileName) {
            cached.nameLine
        } else {
            nameLine(lines, profileName)
        }
        if (nameLine < 0 || !shallowest(player, display, profileName)) return null
        val own = player === Minecraft.getInstance().player
        val signals = PlayerSignals.sample(player, partialTick)
        if (cached != null && cached.wearer == wearer && cached.equip === equip && cached.nameLine == nameLine &&
            cached.own == own && cached.profileName == profileName && cached.signals === signals
        ) {
            return cached
        }
        if (bindings.size >= MAX_BINDINGS) bindings.clear()
        return DisplayTagBinding(wearer, profileName, equip, nameLine, own, signals, lines).also { bindings[key] = it }
    }

    fun visible(opacity: Int): Boolean {
        val alpha = opacity and 0xFF
        if (alpha < OPAQUE_BELOW) return true
        return alpha >= VISIBLE_ALPHA
    }

    @JvmStatic
    fun prepare(binding: DisplayTagBinding, tag: NameplateDrawTag, deferred: Boolean) {
        tag.wearer = binding.wearer
        tag.profileName = binding.profileName
        tag.equip = binding.equip
        tag.flags = NameplateDrawTag.FLAG_DISPLAY or if (binding.own) NameplateDrawTag.FLAG_OWN else 0
        tag.badgeShown = PolyPlusBadge.shouldBadge(binding.wearer)
        tag.deferred = deferred
        tag.distanceSq = if (binding.own) 0.0 else binding.distanceSq
        tag.signals = binding.signals
    }

    @JvmStatic
    fun isNameLine(binding: DisplayTagBinding, info: Display.TextDisplay.CachedInfo?, text: FormattedCharSequence): Boolean {
        val lines = info?.lines() ?: return false
        return binding.nameLine < lines.size && lines[binding.nameLine].contents() === text
    }

    @JvmStatic
    fun measureBlock(tag: NameplateDrawTag, binding: DisplayTagBinding, info: Display.TextDisplay.CachedInfo, flags: Byte, textAlpha: Int) {
        val lines = info.lines()
        val name = binding.nameLine
        tag.align = when (Display.TextDisplay.getAlign(flags)) {
            Display.TextDisplay.Align.LEFT -> Nameplates.ALIGN_LEFT
            Display.TextDisplay.Align.RIGHT -> Nameplates.ALIGN_RIGHT
            Display.TextDisplay.Align.CENTER -> Nameplates.ALIGN_CENTER
        }
        var first = name
        var last = name
        for (i in lines.indices) {
            if (lines[i].width() <= 0) continue
            first = minOf(first, i)
            last = maxOf(last, i)
        }
        val count = last - first + 1
        tag.lineWidths.fill(0)
        if (count > Nameplates.MAX_LINES) {
            tag.lineCount = 1
            tag.nameLine = 0
            tag.lineWidths[0] = lines[name].width()
        } else {
            tag.lineCount = count
            tag.nameLine = name - first
            for (i in 0 until count) tag.lineWidths[i] = lines[first + i].width()
        }
        tag.textAlpha = textAlpha
        tag.badgeGapX = NameplateDrawTag.NO_GAP
    }

    @JvmStatic
    fun nameOriginX(tag: NameplateDrawTag): Int = -extent(tag, true)

    @JvmStatic
    fun nameOriginY(tag: NameplateDrawTag): Int = -extent(tag, false)

    private fun extent(tag: NameplateDrawTag, horizontal: Boolean): Int {
        val count = tag.lineCount
        val name = tag.nameLine
        var widest = 0
        for (i in 0 until count) widest = maxOf(widest, tag.lineWidths[i])
        val nameOffset = doubled(tag.align, widest, tag.lineWidths[name])
        var low = 0
        for (i in 0 until count) {
            if (i != name && tag.lineWidths[i] <= 0) continue
            low = if (horizontal) {
                minOf(low, Math.floorDiv(doubled(tag.align, widest, tag.lineWidths[i]) - nameOffset, 2))
            } else {
                minOf(low, (i - name) * Nameplates.LINE_PITCH)
            }
        }
        if (horizontal && tag.badgeShown && reservesBadge(tag)) low = minOf(low, -Nameplates.BADGE_ADVANCE)
        return low
    }

    private fun reservesBadge(tag: NameplateDrawTag): Boolean {
        val equip = tag.equip ?: return false
        val placement = NameplateDrawSources.current.badgePlacement(equip)
        return placement != NameplateDrawTag.BADGE_NONE && placement != NameplateBundleInfo.BADGE_PLACED
    }

    private fun doubled(align: Int, widest: Int, width: Int): Int = when (align) {
        Nameplates.ALIGN_LEFT -> 0
        Nameplates.ALIGN_RIGHT -> 2 * (widest - width)
        else -> widest - width
    }

    @JvmStatic
    fun restyle(text: FormattedCharSequence, list: NameplateDrawList, base: Int, shadow: Boolean, nameStart: Int, nameEnd: Int): FormattedCharSequence =
        Restyled(text, list, base and RGB, shadow, nameStart, nameEnd)

    @JvmStatic
    fun redraws(list: NameplateDrawList, dropShadow: Boolean): Boolean =
        list.restyle || list.textOutline != 0 || shadow(list, dropShadow) != dropShadow ||
            (dropShadow && list.textShadowUse == Nameplates.SHADOW_WITH_DROP && list.textShadow != 0)

    @JvmStatic
    fun shadow(list: NameplateDrawList, dropShadow: Boolean): Boolean = when (list.textShadowUse) {
        Nameplates.SHADOW_ALWAYS -> true
        Nameplates.SHADOW_REMOVE -> false
        else -> dropShadow
    }

    fun defaultFont(style: Style): Boolean =
        //? if >= 1.21.10 {
        style.font == FontDescription.DEFAULT
        //?} else {
        /*style.font == Style.DEFAULT_FONT
        *///?}

    private class Restyled(
        private val source: FormattedCharSequence,
        private val list: NameplateDrawList,
        private val base: Int,
        private val shadow: Boolean,
        private val nameStart: Int,
        private val nameEnd: Int,
    ) : FormattedCharSequence {
        override fun accept(sink: FormattedCharSink): Boolean {
            var glyph = 0
            return source.accept { index, style, codepoint ->
                val at = glyph++
                sink.accept(index, if (at >= nameStart && at < nameEnd) recolor(style) else style, codepoint)
            }
        }

        private fun recolor(style: Style): Style {
            if (!defaultFont(style)) return style
            val color = style.color
            val source = if (color != null) color.value else base
            val rgb = list.glyphRgb(color != null, source) and RGB
            if (rgb == source) return style
            val guarded = (if (shadow) NameplateDrawList.guardShadowed(OPAQUE or rgb) else NameplateDrawList.guard(OPAQUE or rgb)) and RGB
            if (guarded == source) return style
            return style.withColor(guarded)
        }
    }

    private fun rider(display: Entity): Player? {
        var current: Entity = display
        repeat(MAX_DEPTH) {
            val vehicle = current.vehicle ?: return null
            if (vehicle === display) return null
            if (vehicle is Player) return vehicle
            current = vehicle
        }
        return null
    }

    private fun nameLine(lines: List<Display.TextDisplay.CachedLine>, profileName: String): Int {
        if (profileName.isEmpty()) return -1
        for (line in lines.indices.reversed()) {
            plain.setLength(0)
            lines[line].contents().accept(sink)
            if (findName(plain, plainDefaults, profileName) >= 0) return line
        }
        return -1
    }

    @JvmStatic
    fun glyphChar(codepoint: Int): Char = if (Character.isBmpCodePoint(codepoint)) codepoint.toChar() else REPLACEMENT

    @JvmStatic
    fun findName(glyphs: CharSequence, defaults: BooleanArray, name: String): Int {
        if (name.isEmpty()) return -1
        var at = glyphs.indexOf(name)
        while (at >= 0) {
            val end = at + name.length
            if (allDefault(defaults, at, end) && boundary(glyphs, at - 1) && boundary(glyphs, end)) return at
            at = glyphs.indexOf(name, at + 1)
        }
        return -1
    }

    private fun allDefault(defaults: BooleanArray, from: Int, to: Int): Boolean {
        for (i in from until to) if (!defaults[i]) return false
        return true
    }

    private fun boundary(glyphs: CharSequence, at: Int): Boolean =
        at < 0 || at >= glyphs.length || !wordChar(glyphs[at])


    private fun wordChar(c: Char): Boolean = c == '_' || c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun shallowest(player: Player, display: Display.TextDisplay, profileName: String): Boolean {
        val riders = player.passengers
        if (riders.size == 1 && riders[0] === display) return true
        queue.clear()
        try {
            queue.addAll(riders)
            var head = 0
            var depth = 1
            while (head < queue.size) {
                val end = queue.size
                while (head < end) {
                    val rider = queue[head++]
                    if (rider === display) return true
                    if (rider is Display.TextDisplay && showsName(rider, profileName)) return false
                    if (depth < MAX_DEPTH) queue.addAll(rider.passengers)
                }
                depth++
            }
            return true
        } finally {
            queue.clear()
        }
    }

    private fun showsName(other: Display.TextDisplay, profileName: String): Boolean {
        val state = other.textRenderState() ?: return false
        if (!visible(state.textOpacity().get(SETTLED))) return false
        return nameLine(other.cacheDisplay(splitter).lines(), profileName) >= 0
    }

    private fun split(input: Component, width: Int): Display.TextDisplay.CachedInfo {
        val font = Minecraft.getInstance().font
        val lines = font.split(input, width)
        val result = ArrayList<Display.TextDisplay.CachedLine>(lines.size)
        var widest = 0
        for (line in lines) {
            val lineWidth = font.width(line)
            widest = maxOf(widest, lineWidth)
            result.add(Display.TextDisplay.CachedLine(line, lineWidth))
        }
        return Display.TextDisplay.CachedInfo(result, widest)
    }

    private const val RGB = 0xFFFFFF
    private const val OPAQUE = -0x1000000
    private const val REPLACEMENT = 0xFFFD.toChar()
}
//?}
