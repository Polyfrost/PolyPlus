package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
*///?}

internal fun interface LivePreviewFrame {
    fun render(main: RenderTarget)
}

object LivePreviewFrames {
    private class Deferred(var frames: Int, val task: () -> Unit)

    private val frames = mutableListOf<LivePreviewFrame>()
    private val deferred = ArrayList<Deferred>()

    internal fun later(frameCount: Int, task: () -> Unit) {
        deferred += Deferred(frameCount, task)
    }

    internal fun register(frame: LivePreviewFrame) {
        frames += frame
    }

    internal fun unregister(frame: LivePreviewFrame) {
        frames -= frame
    }

    @JvmStatic
    fun renderAll(main: RenderTarget) {
        //? if = 1.8.9 {
        /*val saved = LegacyBindings.save()
        try {
            runDeferred()
            frames.toList().forEach { it.render(main) }
        } finally {
            saved.restore()
        }
        *///?} else {
        runDeferred()
        frames.toList().forEach { it.render(main) }
        //?}
    }

    private fun runDeferred() {
        if (deferred.isEmpty()) return
        val due = deferred.filter { --it.frames <= 0 }
        deferred.removeAll(due.toSet())
        due.forEach { it.task() }
    }
}

//? if = 1.8.9 {
/*private class LegacyBindings(
    private val active: Int,
    private val textures: IntArray,
    private val program: Int,
    private val arrayBuffer: Int,
    private val vertexArray: Int,
    private val framebuffer: Int,
    private val viewport: IntArray,
) {
    fun restore() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GL20.glUseProgram(program)
        GL30.glBindVertexArray(vertexArray)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer)
        textures.forEachIndexed { unit, texture ->
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
            GlStateManager.activeTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
            GlStateManager.bindTexture(texture)
        }
        GL13.glActiveTexture(active)
        GlStateManager.activeTexture(active)
    }

    companion object {
        private const val UNITS = 2
        private const val VIEWPORT_SIZE = 16

        fun save(): LegacyBindings {
            val active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
            val textures = IntArray(UNITS) { unit ->
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit)
                GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            }
            GL13.glActiveTexture(active)
            val viewport = BufferUtils.createIntBuffer(VIEWPORT_SIZE).also { GL11.glGetInteger(GL11.GL_VIEWPORT, it) }
            return LegacyBindings(
                active,
                textures,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                IntArray(4) { viewport.get(it) },
            )
        }
    }
}
*///?}
