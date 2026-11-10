package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.render.TextRenderer
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.entity.Entity
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.render.NameplateDecorations
import org.polyfrost.polyplus.client.nameplate.render.NameplateFrame
import org.polyfrost.polyplus.client.nameplate.render.NameplateGate
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import java.util.UUID

object LegacyNameplateRenderer {
    private const val SNEAK_TEXT_ALPHA = 0x7F
    private const val SNEAK_ALPHA = SNEAK_TEXT_ALPHA / 255f
    private const val ALPHA_SHIFT = 24
    private const val RGB = 0xFFFFFF

    private var current: LegacyTag? = null

    @JvmStatic
    fun reset() {
        current = null
        LegacyVertexSink.clear()
    }

    @JvmStatic
    fun placesBadge(uuid: UUID): Boolean =
        NameplateGate.active() && NameplateGate.bound(uuid)?.variant?.badge?.placement?.keepsGap == false

    @JvmStatic
    fun background(entity: Entity, name: String, tesselator: Tesselator, original: Operation<Void>) {
        current = LegacyNameplateBinding.bind(entity, name, sneakPath = false)
        hideBackground(entity, tesselator, original)
    }

    @JvmStatic
    fun sneakBackground(entity: Entity, name: String, tesselator: Tesselator, original: Operation<Void>) {
        current = LegacyNameplateBinding.bind(entity, name, sneakPath = true)
        hideBackground(entity, tesselator, original)
    }

    private fun hideBackground(entity: Entity, tesselator: Tesselator, original: Operation<Void>) {
        LegacyVertexSink.clear()
        val tag = owned(entity)
        if (tag == null || !tag.tag.nameplate.variant.ownsBackground) {
            original.call(tesselator)
            return
        }
        if (LegacyTagBatch.active()) {
            LegacyTagBatch.discard(tesselator.buffer)
            return
        }
        GlStateManager.colorMask(false, false, false, false)
        try {
            original.call(tesselator)
        } finally {
            GlStateManager.colorMask(true, true, true, true)
        }
    }

    @JvmStatic
    fun beforeSeeThrough(entity: Entity) {
        val tag = owned(entity) ?: return
        if (tag.polyNametagSneak) {
            decorate(tag, SNEAK_ALPHA, depthMask = false)
        }
    }

    @JvmStatic
    fun beforeOpaque(entity: Entity) {
        val tag = owned(entity) ?: return
        if (!tag.polyNametagSneak) {
            decorate(tag, 1f, depthMask = true)
        }
    }

    @JvmStatic
    fun beforeSneak(entity: Entity) {
        val tag = owned(entity) ?: return
        decorate(tag, SNEAK_ALPHA, depthMask = true)
    }

    @JvmStatic
    fun seeThroughText(font: TextRenderer, entity: Entity, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        val tag = owned(entity) ?: return original.call(font, text, x, y, color)
        if (!tag.polyNametagSneak) {
            return drawText(font, tag, text, x, y, color, original, LegacyPass.SEE_THROUGH, sneak = false)
        }
        try {
            return drawText(font, tag, text, x, y, color, original, LegacyPass.DEPTH, sneak = true)
        } finally {
            finishDecorations(depthMask = false)
        }
    }

    @JvmStatic
    fun opaqueText(font: TextRenderer, entity: Entity, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        val tag = owned(entity)
        if (tag == null || tag.polyNametagSneak) {
            return original.call(font, text, x, y, color)
        }
        try {
            return drawText(font, tag, text, x, y, color, original, LegacyPass.DEPTH, sneak = false)
        } finally {
            finishDecorations(depthMask = true)
        }
    }

    @JvmStatic
    fun sneakText(font: TextRenderer, entity: Entity, text: String, x: Int, y: Int, color: Int, original: Operation<Int>): Int {
        val tag = owned(entity) ?: return original.call(font, text, x, y, color)
        try {
            return drawText(font, tag, text, x, y, color, original, LegacyPass.DEPTH, sneak = true)
        } finally {
            finishDecorations(depthMask = true)
        }
    }

    private fun owned(entity: Entity): LegacyTag? = current?.takeIf { it.entity === entity }

    private fun decorate(tag: LegacyTag, alpha: Float, depthMask: Boolean) {
        val bound = tag.tag
        val frame = NameplateFrame(tag.x + tag.plateX, tag.y + tag.plateY, alpha, 0, tag.block)
        GlStateManager.enableAlphaTest()
        LegacyVertexSink.record()
        try {
            NameplateDecorations.submit(frame, bound.nameplate, bound.frame, bound.state, bound.detailed, bound.badge, tag.gapX)
            LegacyVertexSink.drawRecorded(overText = false, depthMask)
        } finally {
            LegacyVertexSink.stopRecording()
            GlStateManager.clearColor()
        }
    }

    private fun finishDecorations(depthMask: Boolean) {
        try {
            LegacyVertexSink.drawRecorded(overText = true, depthMask)
        } finally {
            LegacyVertexSink.clear()
            GlStateManager.clearColor()
        }
    }

    private fun drawText(
        font: TextRenderer,
        tag: LegacyTag,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        original: Operation<Int>,
        pass: LegacyPass,
        sneak: Boolean,
    ): Int {
        PolyPlusBadge.inlineSuppressed = true
        try {
            val style = tag.style
            if (style == null || text != tag.line.text) {
                return original.call(font, text, x, y, if (sneak) (color and RGB) or (SNEAK_TEXT_ALPHA shl ALPHA_SHIFT) else color)
            }
            val alpha = if (sneak) SNEAK_TEXT_ALPHA else LegacyPolyNametag.textColor(color) ushr ALPHA_SHIFT
            val drawX = x.toFloat() + tag.plateX
            val drawY = y + LegacyPolyNametag.offsetY + tag.plateY
            if (tag.mode == NameplateBundle.TextMode.VANILLA) {
                LegacyNameplateText.drawStyled(font, tag.line, style, drawX, drawY, alpha, pass, tag.outline)
            } else {
                LegacyNameplateLetters.draw(font, tag, style, drawX, drawY, alpha, pass)
            }
            return x + tag.line.textWidth
        } catch (e: RuntimeException) {
            current = null
            NameplateGate.fail(tag.tag.nameplate, e)
            return original.call(font, text, x, y, color)
        } finally {
            PolyPlusBadge.inlineSuppressed = false
        }
    }
}
*///?}
