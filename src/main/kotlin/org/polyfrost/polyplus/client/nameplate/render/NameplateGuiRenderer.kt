package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}

object NameplateGuiRenderer {
    const val FULL_BRIGHT: Int = 0xF000F0

    private const val LINE_CENTER = 4.5f
    private const val TEXT_COLOR = -1
    private const val BACKGROUND_COLOR = 0x40000000
    private const val BACKGROUND_ORDER = -0x10000
    private const val UV_CENTER = 0.5f

    private val backgroundLayer: NameplateLayer by lazy {
        //? if >= 1.21.11 {
        RenderTypes.textSeeThrough(NameplateCanvas.WHITE)
        //?} else {
        /*RenderType.textSeeThrough(NameplateCanvas.WHITE)
        *///?}
    }

    fun name(profileName: String, nameplate: BoundNameplate, badge: Boolean): Component {
        val name = Component.literal(profileName)
        if (!badge || !nameplate.variant.badge.placement.keepsGap) return name
        return Component.empty().append(NameplateText.badgeGap()).append(name)
    }

    fun submit(
        collector: NameplateTarget,
        poseStack: PoseStack,
        centerX: Float,
        centerY: Float,
        pixelsPerFontPixel: Float,
        tag: NameplateTag,
        name: Component,
    ) {
        poseStack.pushPose()
        try {
            poseStack.translate(centerX, centerY - LINE_CENTER * pixelsPerFontPixel, 0f)
            poseStack.scale(pixelsPerFontPixel, pixelsPerFontPixel, pixelsPerFontPixel)
            draw(collector, poseStack, tag, name)
        } finally {
            poseStack.popPose()
        }
    }

    private fun draw(collector: NameplateTarget, poseStack: PoseStack, tag: NameplateTag, name: Component) {
        val font = Minecraft.getInstance().font
        val nameplate = tag.nameplate
        if (!NameplateGate.usable(nameplate) || !NameplateGate.active()) {
            vanilla(collector, poseStack, font, name.visualOrderText)
            return
        }

        val mode = NameplateText.mode(nameplate.variant)
        val resolved = resolveText(font, tag, name)
        val context = NameplateContext.begin(nameplate.variant.ownsBackground, mode != NameplateBundle.TextMode.VANILLA, resolved.outline)
        if (context == null) {
            vanilla(collector, poseStack, font, name.visualOrderText)
            return
        }

        val text: FormattedCharSequence
        val x: Float
        try {
            text = NameplateText.restyle(name, tag.profileName, resolved, false).visualOrderText
            x = -font.width(text) / 2f
            NameplateContext.observe(false, poseStack.last().pose(), x, 0f, text, false, TEXT_COLOR, BACKGROUND_COLOR, 0, FULL_BRIGHT)
        } finally {
            context.end()
        }
        val background = NameplateContext.rewrittenBackground
        val color = NameplateContext.rewrittenColor
        val outline = NameplateContext.rewrittenOutline

        val layout = NameplateText.layout(font, text, tag.profileName)
        val width = layout.textWidth
        val rect = PolyNametagPadding.backgroundRect(width, TagBlock.GLYPH_HEIGHT)
        val block = TagBlock.line(width, layout.nameX, layout.nameWidth).placed(layout.prefixWidth, rect)
        val frame = NameplateFrame(poseStack, collector, x, 0f, NameplateFrame.alphaOf(TEXT_COLOR), FULL_BRIGHT, block)
        if (background != 0) {
            submitBackground(frame, rect, background)
        }
        val plate = NameplatePaint.plate(nameplate, block, tag.frame, tag.state)
        val moved = NameplateFrame(poseStack, collector, x + plate.dx, plate.dy.toFloat(), NameplateFrame.alphaOf(TEXT_COLOR), FULL_BRIGHT, block)
        NameplateDecorations.submit(moved, nameplate, tag.frame, tag.state, tag.detailed, tag.badge, layout.badgeX)
        if (mode == NameplateBundle.TextMode.VANILLA) {
            frame.submitText(x, 0f, text, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, outline)
        } else {
            NameplateLetters.submit(moved, context, text, layout, tag, mode)
        }
    }

    private fun resolveText(font: Font, tag: NameplateTag, name: Component): ResolvedText {
        val nameplate = tag.nameplate
        if (!nameplate.variant.text.needsBackdrop) {
            return NameplatePaint.text(nameplate, TagBlock.line(0, 0, 0), ResolvedText.UNSET)
        }
        val layout = NameplateText.layout(font, name.visualOrderText, tag.profileName)
        return NameplatePaint.text(nameplate, TagBlock.line(layout.textWidth, layout.nameX, layout.nameWidth), ResolvedText.UNSET)
    }

    private fun vanilla(collector: NameplateTarget, poseStack: PoseStack, font: Font, text: FormattedCharSequence) {
        val width = font.width(text)
        val x = -width / 2f
        val frame = NameplateFrame(poseStack, collector, x, 0f, 1f, FULL_BRIGHT, TagBlock.line(width, 0, width))
        submitBackground(frame, PixelRect.vanillaBackground(width, TagBlock.GLYPH_HEIGHT), BACKGROUND_COLOR)
        frame.submitText(x, 0f, text, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, TEXT_COLOR, 0)
    }

    private fun submitBackground(frame: NameplateFrame, rect: PixelRect, color: Int) {
        val x0 = rect.x0.toFloat()
        val y0 = rect.y0.toFloat()
        val x1 = rect.x1.toFloat()
        val y1 = rect.y1.toFloat()
        frame.submitGeometry(BACKGROUND_ORDER, backgroundLayer) { pose, buffer ->
            corner(buffer, pose, x0, y1, color)
            corner(buffer, pose, x1, y1, color)
            corner(buffer, pose, x1, y0, color)
            corner(buffer, pose, x0, y0, color)
        }
    }

    private fun corner(buffer: NameplateVertexSink, pose: NameplatePose, x: Float, y: Float, color: Int) {
        Vertices.add(buffer, pose, x, y, 0f).setUv(UV_CENTER, UV_CENTER).setLight(FULL_BRIGHT).setColor(color)
    }
}
//?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.TextRenderer
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateBinding
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateLetters
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateText
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyPass
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyPolyNametag
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyTag
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyVertexSink
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText

object NameplateGuiRenderer {
    private const val LINE_CENTER = 4.5f
    private const val TEXT_COLOR = -1
    private const val WHITE_RGB = 0xFFFFFF
    private const val ALPHA_SHIFT = 24
    private const val ALPHA_REF = 0.1f
    private const val BACKGROUND_ALPHA = 0.25f
    private const val GLYPH_BOTTOM = 8.0
    private const val BADGE_PAD = 1f

    fun draw(centerX: Float, centerY: Float, pixelsPerFontPixel: Float, tag: NameplateTag) {
        val font = Minecraft.getInstance().textRenderer
        val nameplate = tag.nameplate
        val decorated = NameplateGate.usable(nameplate) && NameplateGate.active()
        val gap = tag.badge && (!decorated || nameplate.variant.badge.placement.keepsGap)
        val name = if (gap) "${PolyPlusBadge.LEGACY_CHAR}${tag.profileName}" else tag.profileName
        val half = font.getWidth(name) / 2
        GlStateManager.pushMatrix()
        try {
            GlStateManager.translatef(centerX, centerY - LINE_CENTER * pixelsPerFontPixel, 0f)
            GlStateManager.scalef(pixelsPerFontPixel, pixelsPerFontPixel, -pixelsPerFontPixel)
            prepare()
            if (!decorated || !drawDecorated(font, tag, name, half)) {
                background(half)
                font.draw(name, -half, 0, TEXT_COLOR)
            }
        } finally {
            PolyPlusBadge.inlineSuppressed = false
            LegacyVertexSink.clear()
            GlStateManager.clearColor()
            GlStateManager.disableBlend()
            GlStateManager.color4f(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    private fun prepare() {
        GlStateManager.activeTexture(GLX.GL_TEXTURE1)
        GlStateManager.disableTexture()
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        GlStateManager.enableTexture()
        GlStateManager.disableLighting()
        GlStateManager.disableCull()
        GlStateManager.enableDepthTest()
        GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.depthMask(true)
        GlStateManager.enableBlend()
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GlStateManager.enableAlphaTest()
        GlStateManager.alphaFunc(GL11.GL_GREATER, ALPHA_REF)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
    }

    private fun drawDecorated(font: TextRenderer, tag: NameplateTag, name: String, half: Int): Boolean {
        val nameplate = tag.nameplate
        try {
            LegacyPolyNametag.refresh()
            val line = LegacyNameplateText.line(font, name, tag.profileName)
            val width = line.textWidth
            val block = TagBlock.line(width, line.nameX, line.nameWidth).placed(line.prefixWidth, backgroundRect(width))
            val external = LegacyPolyNametag.textRgb
            val text = NameplatePaint.text(nameplate, block, external)
            val mode = LegacyNameplateBinding.mode(nameplate.variant)
            val style = LegacyNameplateText.style(line, text, if (external == ResolvedText.UNSET) WHITE_RGB else external, LegacyPolyNametag.textShadow)
            val outline = LegacyNameplateLetters.outlineOf(text, tag.detailed)
            val plate = NameplatePaint.plate(nameplate, block, tag.frame, tag.state)
            val x = (-half).toFloat() + plate.dx
            val y = plate.dy.toFloat()
            val gapX = if (line.badgeX >= 0) line.badgeX + BADGE_PAD else -1f

            if (!nameplate.variant.ownsBackground) {
                background(half)
            }
            decorate(NameplateFrame(x, y, 1f, 0, block), tag, gapX)
            PolyPlusBadge.inlineSuppressed = true
            val alpha = LegacyPolyNametag.textColor(TEXT_COLOR) ushr ALPHA_SHIFT
            val player = Minecraft.getInstance().player
            when {
                mode == NameplateBundle.TextMode.VANILLA && !style.changes -> font.draw(name, -half, 0, TEXT_COLOR)
                mode == NameplateBundle.TextMode.VANILLA || player == null ->
                    LegacyNameplateText.drawStyled(font, line, style, x, y, alpha, LegacyPass.DEPTH, outline)
                else -> {
                    val letters = LegacyTag(player, tag, line, block, mode, style, outline)
                    LegacyNameplateLetters.draw(font, letters, style, x, y, alpha, LegacyPass.DEPTH)
                }
            }
            PolyPlusBadge.inlineSuppressed = false
            LegacyVertexSink.drawRecorded(overText = true, depthMask = true)
            return true
        } catch (e: RuntimeException) {
            NameplateGate.fail(nameplate, e)
            return false
        }
    }

    private fun decorate(frame: NameplateFrame, tag: NameplateTag, gapX: Float) {
        LegacyVertexSink.record()
        try {
            NameplateDecorations.submit(frame, tag.nameplate, tag.frame, tag.state, tag.detailed, tag.badge, gapX)
            LegacyVertexSink.stopRecording()
            LegacyVertexSink.drawRecorded(overText = false, depthMask = true)
        } finally {
            LegacyVertexSink.stopRecording()
            GlStateManager.clearColor()
            GlStateManager.enableTexture()
            GlStateManager.color4f(1f, 1f, 1f, 1f)
        }
    }

    private fun backgroundRect(width: Int): PixelRect =
        if (LegacyPolyNametag.enabled) {
            PolyNametagPadding.backgroundRect(width, TagBlock.GLYPH_HEIGHT)
        } else {
            PixelRect(-1, -1, 2 * (width / 2) + 1, TagBlock.GLYPH_HEIGHT)
        }

    private fun background(half: Int) {
        GlStateManager.disableTexture()
        GlStateManager.depthMask(false)
        try {
            val tesselator = Tesselator.getInstance()
            val buffer = tesselator.getBuffer()
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR)
            buffer.vertex((-half - 1).toDouble(), -1.0, 0.0).color(0f, 0f, 0f, BACKGROUND_ALPHA).nextVertex()
            buffer.vertex((-half - 1).toDouble(), GLYPH_BOTTOM, 0.0).color(0f, 0f, 0f, BACKGROUND_ALPHA).nextVertex()
            buffer.vertex((half + 1).toDouble(), GLYPH_BOTTOM, 0.0).color(0f, 0f, 0f, BACKGROUND_ALPHA).nextVertex()
            buffer.vertex((half + 1).toDouble(), -1.0, 0.0).color(0f, 0f, 0f, BACKGROUND_ALPHA).nextVertex()
            tesselator.end()
        } finally {
            GlStateManager.depthMask(true)
            GlStateManager.enableTexture()
            GlStateManager.clearColor()
            GlStateManager.color4f(1f, 1f, 1f, 1f)
        }
    }
}
*///?}
