package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import net.minecraft.util.FastColor.ARGB32 as ARGB
*///?}
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Display
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText

object DisplayTagRenderer {
    private const val ALPHA_SHIFT = 24
    private const val CHANNEL_MAX = 255f
    private const val SHADER_DISCARD_BELOW = 26
    //? if < 1.21.8 {
    /*private const val FONT_OPAQUE_BELOW = 4
    private const val OPAQUE = 255
    *///?}

    private var widths = IntArray(4)

    fun interface LineDraw {
        fun draw(text: FormattedCharSequence, outlineColor: Int, dx: Int, dy: Int)
    }

    @JvmStatic
    fun textVisible(opacity: Int): Boolean = textAlpha(opacity shl ALPHA_SHIFT) > 0

    fun textAlpha(color: Int): Int {
        val alpha = ARGB.alpha(color)
        //? if < 1.21.8 {
        /*if (alpha < FONT_OPAQUE_BELOW) {
            return OPAQUE
        }
        *///?}
        return if (alpha < SHADER_DISCARD_BELOW) 0 else alpha
    }

    @JvmStatic
    fun backgroundColor(binding: DisplayTagBinding?, color: Int): Int {
        val owns = binding != null && binding.ownsBackground && NameplateGate.usable(binding.nameplate)
        return if (owns) ARGB.color(0, color) else color
    }

    @JvmStatic
    fun submitLine(
        binding: DisplayTagBinding,
        info: Display.TextDisplay.CachedInfo,
        flags: Byte,
        collector: NameplateTarget,
        poseStack: PoseStack,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        dropShadow: Boolean,
        seeThrough: Boolean,
        lightCoords: Int,
        color: Int,
        outlineColor: Int,
        draw: LineDraw,
    ) {
        val visible = textAlpha(color)
        if (visible == 0 || !NameplateGate.usable(binding.nameplate)) {
            draw.draw(text, outlineColor, 0, 0)
            return
        }
        val alpha = visible / CHANNEL_MAX

        val nameplate = binding.nameplate
        val layout = NameplateText.layout(Minecraft.getInstance().font, text, binding.profileName)
        val beside = binding.badge && nameplate.variant.badge.placement.kind != BadgeStyling.Placement.Kind.PLACED
        val shape = block(info, flags, binding, layout.nameX, layout.nameWidth, beside)
        val block = shape.placed(layout.prefixWidth, PixelRect.vanillaBackground(shape.width, shape.height))

        val resolved = NameplatePaint.text(nameplate, block, ResolvedText.UNSET)
        val restyled = NameplateText.restyleLine(text, layout.nameStart, layout.nameEnd, resolved, dropShadow)
        var outline = outlineColor
        if (outline == 0 && !seeThrough && resolved.outline != ResolvedText.UNSET) {
            outline = ARGB.color(visible, PortraitGuard.vertex(resolved.outline))
        }
        val plate = NameplatePaint.plate(nameplate, block, binding.frame, binding.state)
        draw.draw(restyled, outline, plate.dx, plate.dy)

        poseStack.pushPose()
        try {
            val frame = NameplateFrame(poseStack, collector, x - block.nameLineX + plate.dx, y - block.nameLineY + plate.dy, alpha, lightCoords, block)
            NameplateDecorations.submit(
                frame,
                nameplate,
                binding.frame,
                binding.state,
                binding.detailed,
                binding.badge,
                NameplateDecorations.NO_BADGE,
            )
        } finally {
            poseStack.popPose()
        }
    }

    private fun block(
        info: Display.TextDisplay.CachedInfo,
        flags: Byte,
        binding: DisplayTagBinding,
        nameX: Int,
        nameWidth: Int,
        badge: Boolean,
    ): TagBlock {
        val lines = info.lines()
        val count = lines.size
        if (widths.size < count) {
            widths = IntArray(maxOf(widths.size * 2, count))
        }
        for (i in 0 until count) {
            widths[i] = lines[i].width()
        }

        val align = when (Display.TextDisplay.getAlign(flags)) {
            Display.TextDisplay.Align.LEFT -> TagBlock.Align.LEFT
            Display.TextDisplay.Align.RIGHT -> TagBlock.Align.RIGHT
            Display.TextDisplay.Align.CENTER -> TagBlock.Align.CENTER
        }
        return TagBlock.display(widths, count, info.width(), align, binding.lineIndex, nameX, nameWidth, badge)
    }
}
//?}
