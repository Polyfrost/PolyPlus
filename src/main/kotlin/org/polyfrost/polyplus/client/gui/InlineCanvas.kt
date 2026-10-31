package org.polyfrost.polyplus.client.gui

import net.minecraft.client.Minecraft
import org.polyfrost.compose.backend.PolyCanvas

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 1.21.11 {
import org.polyfrost.oneconfig.internal.ui.compose.BlazeRenderer
//?} else {
/*import org.polyfrost.oneconfig.internal.ui.RenderTargetFbo
import org.polyfrost.oneconfig.internal.ui.compose.GlHost
import org.polyfrost.oneconfig.internal.ui.compose.GlRenderer
import org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState
*///?}

//? if >= 1.21.8 && < 1.21.11 {
/*import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
*///?}

/**
 * Draws a [PolyCanvas] in framebuffer pixels into a vanilla screen's frame, at the point it's called from.
 * Each instance owns its own offscreen target where a version needs one, so two callers in one frame don't share it.
 */
class InlineCanvas(textureName: String) {
    //? if >= 26.1 {
    fun draw(ctx: GuiGraphicsExtractor, block: (PolyCanvas) -> Unit): Boolean {
        val renderer = BlazeRenderer(Minecraft.getInstance().window.guiScale.toFloat())
        block(PolyCanvas(renderer))
        for (op in renderer.finish()) op(ctx)
        return true
    }
    //?} elif >= 1.21.11 {
    /*fun draw(ctx: GuiGraphics, block: (PolyCanvas) -> Unit): Boolean {
        val renderer = BlazeRenderer(Minecraft.getInstance().window.guiScale.toFloat())
        block(PolyCanvas(renderer))
        for (op in renderer.finish()) op(ctx)
        return true
    }
    *///?} elif >= 1.21.8 {
    /*private val textureId = Identifier.fromNamespaceAndPath("polyplus", textureName)
    private val offscreen = OffscreenTarget()
    private var texture: TargetTexture? = null

    private class TargetTexture : AbstractTexture() {
        fun set(target: RenderTarget) {
            texture = target.colorTexture
            textureView = target.colorTextureView
        }

        override fun close() {
            texture = null
            textureView = null
        }
    }

    fun draw(ctx: GuiGraphics, block: (PolyCanvas) -> Unit): Boolean {
        if (!GlHost.legacyGl) return false
        val mc = Minecraft.getInstance()
        val w = mc.window.width
        val h = mc.window.height
        if (w <= 0 || h <= 0 || !offscreen.resolve(w, h)) return false
        val target = offscreen.target ?: return false
        val fbo = RenderTargetFbo.getFboId(target)
        if (fbo <= 0) return false
        drawGl(fbo, w, h, topDown = true, clear = true, block)
        val tex = texture ?: TargetTexture().also {
            texture = it
            mc.textureManager.register(textureId, it)
        }
        tex.set(target)
        val guiScale = mc.window.guiScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().scale(1f / guiScale, 1f / guiScale)
            ctx.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, textureId, 0, 0, 0f, 0f, w, h, w, h)
        } finally {
            ctx.pose().popMatrix()
        }
        return true
    }
    *///?} elif > 1.8.9 {
    /*fun draw(ctx: GuiGraphics, block: (PolyCanvas) -> Unit): Boolean {
        if (!GlHost.legacyGl) return false
        ctx.flush()
        val mc = Minecraft.getInstance()
        //? if = 1.21.1 {
        /^if (GlHost.vanillaPipeline) {
            val renderer = GlRenderer()
            block(PolyCanvas(renderer))
            mc.mainRenderTarget.bindWrite(true)
            renderer.drawVanilla(mc.window.width, mc.window.height, topDown = false)
            return true
        }
        ^///?}
        drawGl(RenderTargetFbo.getFboId(mc.mainRenderTarget), mc.window.width, mc.window.height, topDown = false, clear = false, block)
        return true
    }
    *///?} else {
    /*fun draw(block: (PolyCanvas) -> Unit): Boolean {
        if (!GlHost.legacyGl) return false
        val mc = Minecraft.getInstance()
        drawGl(RenderTargetFbo.getFboId(mc.renderTarget), mc.window.width, mc.window.height, topDown = false, clear = false, block)
        return true
    }
    *///?}

    //? if < 1.21.11 {
    /*private val glState = StoredGLState(330)

    private fun drawGl(fbo: Int, w: Int, h: Int, topDown: Boolean, clear: Boolean, block: (PolyCanvas) -> Unit) {
        if (fbo <= 0 || w <= 0 || h <= 0) return
        glState.capture()
        try {
            val renderer = GlRenderer()
            block(PolyCanvas(renderer))
            renderer.draw(fbo, w, h, topDown, clear)
        } finally {
            glState.restore()
        }
    }
    *///?}
}
