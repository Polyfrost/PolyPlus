package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.math.Axis
import net.minecraft.client.StringSplitter
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import net.minecraft.util.Mth
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.mixin.client.nameplate.StringSplitterAccessor
import org.polyfrost.polyplus.nameplate.engine.api.anim.TextMotion
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import java.util.Collections
import java.util.Optional
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import net.minecraft.util.FastColor.ARGB32 as ARGB
*///?}

object NameplateText {
    const val BADGE_GLYPH: Int = 0xE000
    const val BADGE_SPACE: Int = 0xE001

    //? if >= 1.21.10 {
    val GAP_FONT: FontDescription = FontDescription.Resource(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "badge_gap"))
    //?} else {
    /*val GAP_FONT: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "badge_gap")
    *///?}

    private val segmentStyles = ArrayList<Style>()
    private val segmentTexts = ArrayList<String>()
    private val plain = StringBuilder()
    private val layout = Layout()

    fun badgeGap(): Component =
        Component.literal("\uE000\uE001").setStyle(Style.EMPTY.withFont(GAP_FONT))

    fun isDefaultFont(style: Style): Boolean =
        //? if >= 1.21.10 {
        FontDescription.DEFAULT == style.font
        //?} else {
        /*Style.DEFAULT_FONT == style.font
        *///?}

    fun mode(variant: NameplateBundle.Variant): NameplateBundle.TextMode {
        val settings = NameplateBinding.settings
        return if (settings.animatedNames && settings.motion == NameplateMotion.FULL) variant.textMode else NameplateBundle.TextMode.VANILLA
    }

    fun isBadgeGap(style: Style): Boolean = GAP_FONT == style.font

    fun restyle(name: Component, profileName: String?, text: ResolvedText, submitDropShadow: Boolean): Component {
        val shadows = shadows(text, submitDropShadow)
        val recolors = text.color != ResolvedText.UNSET
        if (!recolors && !shadows && text.formatting == TextStyling.Formatting.KEEP) {
            return name
        }

        segmentStyles.clear()
        segmentTexts.clear()
        plain.setLength(0)
        name.visit(FormattedText.StyledContentConsumer<Any> { style, segment -> collectSegment(style, segment) }, Style.EMPTY)

        var from = 0
        var to = plain.length
        if (!profileName.isNullOrEmpty()) {
            val at = DisplayNameLine.lastIndexOf(plain, profileName)
            if (at >= 0) {
                from = at
                to = at + profileName.length
            }
        }

        val result: MutableComponent = Component.empty()
        var plainStart = 0
        for (i in segmentStyles.indices) {
            val segmentStyle = segmentStyles[i]
            val segment = segmentTexts[i]
            if (!isDefaultFont(segmentStyle)) {
                result.append(Component.literal(segment).setStyle(segmentStyle))
                continue
            }

            val length = segment.length
            val a = Mth.clamp(from - plainStart, 0, length)
            val b = Mth.clamp(to - plainStart, 0, length)
            if (a > 0) {
                result.append(Component.literal(segment.substring(0, a)).setStyle(formatted(segmentStyle, text)))
            }
            if (b > a) {
                result.append(Component.literal(segment.substring(a, b)).setStyle(nameStyle(segmentStyle, text, recolors, shadows)))
            }
            if (b < length) {
                result.append(Component.literal(segment.substring(b)).setStyle(segmentStyle))
            }
            plainStart += length
        }

        return result
    }

    fun restyleLine(line: FormattedCharSequence, nameStart: Int, nameEnd: Int, text: ResolvedText, submitDropShadow: Boolean): FormattedCharSequence {
        val shadows = shadows(text, submitDropShadow)
        val recolors = text.color != ResolvedText.UNSET
        if (!recolors && !shadows && text.formatting == TextStyling.Formatting.KEEP) {
            return line
        }

        return RestyledLine(line, nameStart, nameEnd, text, recolors, shadows)
    }

    fun layout(font: Font, text: FormattedCharSequence, profileName: String?, into: Layout = layout): Layout {
        into.reset((font.splitter as StringSplitterAccessor).`polyplus$widthProvider`())
        text.accept(into)
        into.finish(profileName)
        return into
    }

    private fun shadows(text: ResolvedText, submitDropShadow: Boolean): Boolean = when (text.shadowUse) {
        ResolvedText.ShadowUse.LEAVE -> false
        ResolvedText.ShadowUse.ALWAYS, ResolvedText.ShadowUse.REMOVE -> true
        ResolvedText.ShadowUse.WITH_DROP_SHADOW -> submitDropShadow
    }

    private fun nameStyle(style: Style, text: ResolvedText, recolors: Boolean, shadows: Boolean): Style {
        var restyled = when {
            style.color != null -> formatted(style, text)
            recolors -> style.withColor(PortraitGuard.text(text.color))
            else -> style
        }
        if (shadows && !hasAdjustShadow(style, text)) {
            restyled = if (text.shadowUse == ResolvedText.ShadowUse.REMOVE) {
                restyled.unshadowed()
            } else {
                restyled.shadowed(PortraitGuard.vertex(text.shadow))
            }
        }
        return restyled
    }

    private fun formatted(style: Style, text: ResolvedText): Style {
        val own: TextColor = style.color ?: return style
        return when (text.formatting) {
            TextStyling.Formatting.KEEP -> style
            TextStyling.Formatting.OVERRIDE -> if (text.color != ResolvedText.UNSET) {
                style.withColor(PortraitGuard.text(text.color))
            } else {
                style.withColor(null as TextColor?)
            }
            TextStyling.Formatting.ADJUST -> {
                val adjusted = style.withColor(PortraitGuard.text(text.adjust(own.value)))
                if (hasAdjustShadow(style, text)) {
                    adjusted.shadowed(PortraitGuard.vertex(text.adjustShadow(own.value)))
                } else {
                    adjusted
                }
            }
        }
    }

    private fun hasAdjustShadow(style: Style, text: ResolvedText): Boolean {
        val own = style.color ?: return false
        return text.formatting == TextStyling.Formatting.ADJUST &&
            text.shadowUse != ResolvedText.ShadowUse.REMOVE &&
            text.adjustShadow(own.value) != 0
    }

    private fun collectSegment(style: Style, text: String): Optional<Any> {
        if (text.isNotEmpty()) {
            segmentStyles.add(style)
            segmentTexts.add(text)
            if (isDefaultFont(style)) {
                plain.append(text)
            }
        }
        return Optional.empty()
    }

    private class RestyledLine(
        private val source: FormattedCharSequence,
        private val nameStart: Int,
        private val nameEnd: Int,
        private val text: ResolvedText,
        private val recolors: Boolean,
        private val shadows: Boolean,
    ) : FormattedCharSequence {
        private var prefixIn: Style? = null
        private var prefixOut: Style? = null
        private var nameIn: Style? = null
        private var nameOut: Style? = null

        override fun accept(sink: FormattedCharSink): Boolean {
            var index = 0
            return source.accept { position, style, codepoint ->
                sink.accept(position, restyled(index++, style), codepoint)
            }
        }

        private fun restyled(index: Int, style: Style): Style {
            if (index >= nameEnd || !isDefaultFont(style)) {
                return style
            }
            if (index < nameStart) {
                if (style !== prefixIn) {
                    prefixIn = style
                    prefixOut = formatted(style, text)
                }
                return prefixOut ?: style
            }
            if (style !== nameIn) {
                nameIn = style
                nameOut = nameStyle(style, text, recolors, shadows)
            }
            return nameOut ?: style
        }
    }

    class Layout internal constructor() : FormattedCharSink {
        private val plain = StringBuilder()
        private var plainGlyph = IntArray(64)
        private var glyphX = FloatArray(65)
        private var defaultBefore = FloatArray(65)
        private var defaults = BooleanArray(64)
        private var widths: StringSplitter.WidthProvider? = null
        private var firstDefault = -1
        private var lastDefault = -1

        var count: Int = 0
            private set
        var badgeX: Float = -1f
            private set
        var textWidth: Int = 0
            private set
        var nameStart: Int = 0
            private set
        var nameEnd: Int = 0
            private set
        var nameX: Int = 0
            private set
        var nameWidth: Int = 0
            private set
        var prefixWidth: Int = 0
            private set

        internal fun reset(widths: StringSplitter.WidthProvider) {
            this.widths = widths
            plain.setLength(0)
            count = 0
            firstDefault = -1
            lastDefault = -1
            badgeX = -1f
            glyphX[0] = 0f
            defaultBefore[0] = 0f
        }

        override fun accept(position: Int, style: Style, codepoint: Int): Boolean {
            if (count + 1 >= glyphX.size) {
                glyphX = glyphX.copyOf(glyphX.size * 2)
                defaultBefore = defaultBefore.copyOf(defaultBefore.size * 2)
            }
            if (count >= defaults.size) {
                defaults = defaults.copyOf(defaults.size * 2)
            }

            val advance = widths?.getWidth(codepoint, style) ?: 0f
            val isDefault = isDefaultFont(style)
            if (badgeX < 0 && codepoint == BADGE_GLYPH && isBadgeGap(style)) {
                badgeX = glyphX[count]
            }
            if (isDefault) {
                val charStart = plain.length
                plain.appendCodePoint(codepoint)
                if (plain.length > plainGlyph.size) {
                    plainGlyph = plainGlyph.copyOf(maxOf(plainGlyph.size * 2, plain.length))
                }
                for (c in charStart until plain.length) {
                    plainGlyph[c] = count
                }
                if (firstDefault < 0) {
                    firstDefault = count
                }
                lastDefault = count
            }

            defaults[count] = isDefault
            glyphX[count + 1] = glyphX[count] + advance
            defaultBefore[count + 1] = defaultBefore[count] + if (isDefault) advance else 0f
            count++
            return true
        }

        internal fun finish(profileName: String?) {
            textWidth = Mth.ceil(glyphX[count])
            val at = if (profileName.isNullOrEmpty()) -1 else DisplayNameLine.lastIndexOf(plain, profileName)
            when {
                at >= 0 && profileName != null -> {
                    nameStart = plainGlyph[at]
                    nameEnd = plainGlyph[at + profileName.length - 1] + 1
                }
                firstDefault >= 0 -> {
                    nameStart = firstDefault
                    nameEnd = lastDefault + 1
                }
                else -> {
                    nameStart = 0
                    nameEnd = 0
                }
            }

            nameX = glyphX[nameStart].roundToInt()
            nameWidth = (glyphX[nameEnd] - glyphX[nameStart]).roundToInt()
            prefixWidth = defaultBefore[nameStart].roundToInt()
        }

        fun glyphX(index: Int): Float = glyphX[index]

        fun isNameGlyph(index: Int): Boolean = index in nameStart until nameEnd

        fun isDefault(index: Int): Boolean = defaults[index]
    }
}

object NameplateLetters {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val motions = WeakHashMap<NameplateBundle, TextMotion>()
    private val reported: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())
    private val letters = LetterFrame()
    private val glyphX = IntArray(LetterFrame.MAX_GLYPHS)
    private val glyphWidth = IntArray(LetterFrame.MAX_GLYPHS)

    private const val HALF_HEIGHT = 4f
    private const val CHANNEL_MAX = 255
    private const val OPAQUE = -0x1000000

    fun submit(
        frame: NameplateFrame,
        context: NameplateContext,
        text: FormattedCharSequence,
        layout: NameplateText.Layout,
        tag: NameplateTag,
        mode: NameplateBundle.TextMode,
    ) {
        val nameplate = tag.nameplate
        val start = layout.nameStart
        val count = layout.nameEnd - start
        val motion = motions.getOrPut(nameplate.bundle) { TextMotion.of(nameplate.bundle) }
        val fits = count <= LetterFrame.MAX_GLYPHS
        if (fits) {
            val origin = layout.glyphX(start)
            for (i in 0 until count) {
                glyphX[i] = (layout.glyphX(start + i) - origin).roundToInt()
                glyphWidth[i] = (layout.glyphX(start + i + 1) - layout.glyphX(start + i)).roundToInt()
            }
        }
        motion.evaluate(nameplate.bundle, nameplate.variant, frame.block, tag.frame, nameplate.seed, tag.state, if (fits) count else 0, glyphX, glyphWidth, letters)
        if (motion.failed && reported.add(nameplate.bundle)) {
            LOGGER.warn("Nameplate {} letter program ran out of its instruction budget; its letters draw still", nameplate.bundle.id)
        }

        val flat = FlatText(layout.count)
        text.accept(flat)
        if (mode == NameplateBundle.TextMode.OWNED && fits && ownedSubmits(layout) <= LetterFrame.MAX_GLYPHS) {
            submitOwned(frame, context, flat, layout)
        } else {
            submitRuns(frame, context, flat, layout)
        }
    }

    private fun letterOf(layout: NameplateText.Layout, glyph: Int): Int {
        val letter = glyph - layout.nameStart
        return if (letter in 0 until letters.count && layout.isDefault(glyph)) letter else -1
    }

    private fun sameRun(a: Int, b: Int): Boolean = when {
        a < 0 && b < 0 -> true
        a < 0 -> isPlain(b)
        b < 0 -> isPlain(a)
        else -> letters.sameRun(a, b)
    }

    private fun isPlain(letter: Int): Boolean =
        letters.dx[letter] == 0 && letters.dy[letter] == 0 && letters.opacity[letter] == LetterFrame.OPAQUE

    private fun submitRuns(frame: NameplateFrame, context: NameplateContext, text: FlatText, layout: NameplateText.Layout) {
        var from = 0
        while (from < layout.count) {
            val first = letterOf(layout, from)
            var to = from + 1
            while (to < layout.count && sameRun(first, letterOf(layout, to))) {
                to++
            }
            submitRun(frame, context, text, layout, from, to, first)
            from = to
        }
    }

    private fun submitRun(
        frame: NameplateFrame,
        context: NameplateContext,
        text: FlatText,
        layout: NameplateText.Layout,
        from: Int,
        to: Int,
        letter: Int,
    ) {
        val dx = if (letter < 0) 0 else letters.dx[letter]
        val dy = if (letter < 0) 0 else letters.dy[letter]
        val opacity = if (letter < 0) CHANNEL_MAX else letters.opacity[letter]
        if (opacity <= 0) {
            return
        }
        val run = LetterSlice(text, from, to, null)
        val x = frame.x + layout.glyphX(from) + dx
        val y = frame.y + dy
        frame.submitText(
            x,
            y,
            run,
            context.dropShadow,
            Font.DisplayMode.NORMAL,
            context.lightCoords,
            NameplatePaint.scaleAlpha(context.color, opacity),
            if (context.outlineColor == 0) 0 else NameplatePaint.scaleAlpha(context.outlineColor, opacity),
        )
        submitSeeThrough(frame, context, run, x, y, 0f, 0f, opacity, 0, 1f)
    }

    private fun submitSeeThrough(
        frame: NameplateFrame,
        context: NameplateContext,
        text: FormattedCharSequence,
        pivotX: Float,
        pivotY: Float,
        offsetX: Float,
        offsetY: Float,
        opacity: Int,
        rotation: Int,
        scale: Float,
    ) {
        val seeThroughPose = context.seeThroughPose
        if (!context.seeThrough || seeThroughPose == null) {
            return
        }
        val poseStack = frame.poseStack
        poseStack.pushPose()
        poseStack.last().pose().set(seeThroughPose)
        var x = pivotX
        var y = pivotY
        if (rotation != 0) {
            //? if >= 26.3 {
            poseStack.rotate(Axis.ZP.rotationDegrees(rotation.toFloat()))
            //?} else {
            /*poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.toFloat()))
            *///?}
            val radians = Math.toRadians(rotation.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            x = pivotX * cos + pivotY * sin
            y = pivotY * cos - pivotX * sin
        }
        if (scale != 1f) {
            poseStack.scale(scale, scale, 1f)
            x /= scale
            y /= scale
        }
        frame.submitText(
            x + offsetX,
            y + offsetY,
            text,
            context.seeThroughDropShadow,
            Font.DisplayMode.SEE_THROUGH,
            context.seeThroughLightCoords,
            NameplatePaint.scaleAlpha(context.seeThroughColor, opacity),
            0,
        )
        poseStack.popPose()
    }

    private fun ownedSubmits(layout: NameplateText.Layout): Int {
        var submits = 0
        var inRun = false
        for (glyph in 0 until layout.count) {
            if (letterOf(layout, glyph) >= 0) {
                submits++
                inRun = false
            } else if (!inRun) {
                submits++
                inRun = true
            }
        }
        return submits
    }

    private fun mergesStill(context: NameplateContext, text: FlatText): Boolean =
        !text.hasEffects() && (
            context.outlineColor == 0 ||
                ARGB.alpha(context.color) == CHANNEL_MAX && ARGB.alpha(context.outlineColor) == CHANNEL_MAX
            )

    private fun isOwnedPlain(layout: NameplateText.Layout, glyph: Int, mergesStill: Boolean): Boolean {
        val letter = letterOf(layout, glyph)
        return letter < 0 || mergesStill && letters.isStill(letter)
    }

    private fun submitOwned(frame: NameplateFrame, context: NameplateContext, text: FlatText, layout: NameplateText.Layout) {
        val mergesStill = mergesStill(context, text)
        var from = 0
        while (from < layout.count) {
            if (!isOwnedPlain(layout, from, mergesStill)) {
                submitLetter(frame, context, text, layout, from, letterOf(layout, from))
                from++
                continue
            }
            var to = from + 1
            while (to < layout.count && isOwnedPlain(layout, to, mergesStill)) {
                to++
            }
            val x = frame.x + layout.glyphX(from)
            val run = LetterSlice(text, from, to, null)
            frame.submitText(
                x,
                frame.y,
                run,
                context.dropShadow,
                Font.DisplayMode.NORMAL,
                context.lightCoords,
                context.color,
                context.outlineColor,
            )
            submitSeeThrough(frame, context, run, x, frame.y, 0f, 0f, CHANNEL_MAX, 0, 1f)
            from = to
        }
    }

    private fun submitLetter(
        frame: NameplateFrame,
        context: NameplateContext,
        text: FlatText,
        layout: NameplateText.Layout,
        glyph: Int,
        letter: Int,
    ) {
        val opacity = letters.opacity[letter]
        if (opacity <= 0) {
            return
        }
        val color = NameplatePaint.scaleAlpha(context.color, opacity)
        val shadow = letters.shadow[letter]
        val outline = when (val own = letters.outline[letter]) {
            LetterFrame.KEEP -> if (context.outlineColor == 0) 0 else NameplatePaint.scaleAlpha(context.outlineColor, opacity)
            LetterFrame.OFF -> 0
            else -> ARGB.color(ARGB.alpha(color), PortraitGuard.vertex(own))
        }
        val dropShadow = when (shadow) {
            LetterFrame.KEEP -> context.dropShadow
            LetterFrame.OFF -> false
            else -> true
        }
        val ownColor = letters.color[letter]
        val restyle = ownColor != LetterFrame.KEEP || shadow >= 0
        val slice = LetterSlice(text, glyph, glyph + 1, if (restyle) LetterStyle(ownColor, shadow) else null)

        val start = layout.glyphX(glyph)
        val half = (layout.glyphX(glyph + 1) - start) / 2f
        val centerX = frame.x + start + letters.dx[letter] + half
        val centerY = frame.y + letters.dy[letter] + HALF_HEIGHT
        val poseStack = frame.poseStack
        poseStack.pushPose()
        poseStack.translate(centerX, centerY, 0f)
        val rotation = letters.rotation[letter]
        if (rotation != 0) {
            //? if >= 26.3 {
            poseStack.rotate(Axis.ZP.rotationDegrees(rotation.toFloat()))
            //?} else {
            /*poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.toFloat()))
            *///?}
        }
        val scale = letters.scaleOf(letter)
        if (scale != 1f) {
            poseStack.scale(scale, scale, 1f)
        }
        frame.submitText(
            -half,
            -HALF_HEIGHT,
            slice,
            dropShadow,
            Font.DisplayMode.NORMAL,
            context.lightCoords,
            color,
            outline,
        )
        poseStack.popPose()

        val plain = if (shadow < 0) slice else LetterSlice(text, glyph, glyph + 1, if (ownColor != LetterFrame.KEEP) LetterStyle(ownColor, LetterFrame.KEEP) else null)
        submitSeeThrough(frame, context, plain, centerX, centerY, -half, -HALF_HEIGHT, opacity, rotation, scale)
    }

    private class LetterStyle(private val color: Int, private val shadow: Int) {
        private var input: Style? = null
        private var output: Style? = null

        fun apply(style: Style): Style {
            if (style === input) {
                return output ?: style
            }
            var restyled = style
            if (color != LetterFrame.KEEP) {
                restyled = restyled.withColor(PortraitGuard.text(color))
            }
            if (shadow >= 0) {
                restyled = restyled.shadowed(PortraitGuard.vertex(OPAQUE or shadow))
            }
            input = style
            output = restyled
            return restyled
        }
    }

    private class FlatText(capacity: Int) : FormattedCharSink {
        val codepoints = IntArray(capacity)
        val styles = Array(capacity) { Style.EMPTY }
        var size = 0
            private set

        override fun accept(position: Int, style: Style, codepoint: Int): Boolean {
            if (size < codepoints.size) {
                codepoints[size] = codepoint
                styles[size] = style
                size++
            }
            return true
        }

        fun hasEffects(): Boolean {
            for (i in 0 until size) {
                if (styles[i].isUnderlined || styles[i].isStrikethrough) {
                    return true
                }
            }
            return false
        }
    }

    private class LetterSlice(
        private val source: FlatText,
        private val from: Int,
        private val to: Int,
        private val restyle: LetterStyle?,
    ) : FormattedCharSequence {
        override fun accept(sink: FormattedCharSink): Boolean {
            for (i in from until minOf(to, source.size)) {
                val style = source.styles[i]
                if (!sink.accept(i - from, restyle?.apply(style) ?: style, source.codepoints[i])) {
                    return false
                }
            }
            return true
        }
    }
}

private fun Style.shadowed(argb: Int): Style =
    //? if >= 1.21.4 {
    withShadowColor(argb)
    //?} else {
    /*this
    *///?}

private fun Style.unshadowed(): Style =
    //? if >= 1.21.11 {
    withoutShadow()
    //?} elif >= 1.21.4 {
    /*withShadowColor(0)
    *///?} else {
    /*this
    *///?}
//?}

object PortraitGuard {
    private const val RED_ONE = 0x010000
    private const val RED_MASK = 0xFF0000

    fun vertex(argb: Int): Int = if (argb and RED_MASK == RED_ONE) argb + RED_ONE else argb

    fun text(argb: Int): Int {
        val red = (argb ushr 16) and 0xFF
        val safe = when (red) {
            1 -> 2
            4, 5 -> 3
            6, 7 -> 8
            else -> red
        }
        return if (safe == red) argb else (argb and 0xFF00FFFF.toInt()) or (safe shl 16)
    }
}
