package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.joml.Matrix4fc
//? if < 1.21.5 {
/*import org.joml.Matrix4f
*///?}
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import java.util.UUID

object PlayerNameplateRenderer {
    private const val VANILLA_BACKGROUND_OPACITY = 0.25f
    private const val PLATE_OPACITY = 0.7f

    fun interface NameDraw {
        fun draw(name: Component)
    }

    @JvmStatic
    fun submit(tag: NameplateTag?, name: Component, collector: NameplateTarget, poseStack: PoseStack, draw: NameDraw) {
        if (tag == null || !NameplateGate.usable(tag.nameplate) || !NameplateGate.active()) {
            draw.draw(name)
            return
        }

        val nameplate = tag.nameplate
        val entry = NameplateTextCache.entry(name, tag.profileName)
        val text = resolveText(nameplate, entry)
        val mode = NameplateText.mode(nameplate.variant)
        val context = NameplateContext.begin(nameplate.variant.ownsBackground, mode != NameplateBundle.TextMode.VANILLA, text.outline)
        if (context == null) {
            draw.draw(name)
            return
        }

        try {
            draw.draw(entry.restyled(text, NameplateContext.externalDropShadow))
        } finally {
            context.end()
        }

        if (context.submitted) {
            submitNameplate(context, tag, entry, mode, collector, poseStack)
        }
    }

    internal fun resolveText(tag: NameplateTag, name: Component, badgeOwner: UUID? = null): ResolvedText {
        val nameplate = tag.nameplate
        if (!nameplate.variant.text.needsBackdrop) {
            return NameplatePaint.text(nameplate, NameplateTextCache.EMPTY, NameplateContext.externalTextColor)
        }

        val shown = if (badgeOwner == null) name else PolyPlusBadge.decorate(name, badgeOwner)
        return NameplatePaint.text(nameplate, NameplateTextCache.block(shown, tag.profileName), NameplateContext.externalTextColor)
    }

    private fun resolveText(nameplate: BoundNameplate, entry: NameplateTextCache.Entry): ResolvedText {
        val block = if (nameplate.variant.text.needsBackdrop) entry.block() else NameplateTextCache.EMPTY
        return NameplatePaint.text(nameplate, block, NameplateContext.externalTextColor)
    }

    internal fun decorate(
        tag: NameplateTag,
        layout: NameplateText.Layout,
        poseStack: PoseStack,
        collector: NameplateTarget,
        x: Float,
        y: Float,
        color: Int,
        lightCoords: Int,
    ): NameplateFrame {
        val background = PolyNametagPadding.backgroundRect(layout.textWidth, TagBlock.GLYPH_HEIGHT)
        val block = NameplateTextCache.placed(tag.profileName, layout, background)
        val plate = NameplatePaint.plate(tag.nameplate, block, tag.frame, tag.state)
        val alpha = NameplateFrame.alphaOf(color)
        val opacity = Minecraft.getInstance().options.getBackgroundOpacity(VANILLA_BACKGROUND_OPACITY)
        val plateAlpha = alpha * minOf(1f, PLATE_OPACITY * opacity / VANILLA_BACKGROUND_OPACITY)
        val frame = NameplateFrame(poseStack, collector, x + plate.dx, y + plate.dy, alpha, lightCoords, block, plateAlpha)
        NameplateDecorations.submit(frame, tag.nameplate, tag.frame, tag.state, tag.detailed, tag.badge, layout.badgeX)
        return frame
    }

    private fun submitNameplate(
        context: NameplateContext,
        tag: NameplateTag,
        entry: NameplateTextCache.Entry,
        mode: NameplateBundle.TextMode,
        collector: NameplateTarget,
        poseStack: PoseStack,
    ) {
        val finalText = context.text ?: return
        val pose = context.pose ?: return
        val layout = entry.layout(Minecraft.getInstance().font, finalText)

        poseStack.pushPose()
        try {
            setPose(poseStack, pose)
            val frame = decorate(tag, layout, poseStack, collector, context.x, context.y, context.color, context.lightCoords)
            if (mode != NameplateBundle.TextMode.VANILLA) {
                NameplateLetters.submit(frame, context, finalText, layout, tag, mode)
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun setPose(poseStack: PoseStack, pose: Matrix4fc) {
        poseStack.setIdentity()
        //? if >= 1.21.5 {
        poseStack.mulPose(pose)
        //?} else {
        /*poseStack.mulPose(Matrix4f(pose))
        *///?}
        poseStack.last().pose().set(pose)
    }
}
//?}
