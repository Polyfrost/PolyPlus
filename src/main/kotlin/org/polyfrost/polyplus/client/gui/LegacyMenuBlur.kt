package org.polyfrost.polyplus.client.gui

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.PostChain
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11

object LegacyMenuBlur {
    private val LOGGER = LogManager.getLogger("PolyPlus/MenuBlur")
    private val ID = Identifier.fromNamespaceAndPath("polyplus", "shaders/post/menu_blur.json")

    private var chain: PostChain? = null
    private var failed = false
    private var width = -1
    private var height = -1

    @JvmStatic
    fun blur(): Boolean {
        if (failed || !GLX.usePostProcess || !GLX.useFbo()) return false
        val mc = Minecraft.getInstance()
        val target = mc.renderTarget ?: return false
        val chain = chain ?: runCatching { PostChain(mc.textureManager, mc.resourceManager, target, ID) }
            .onFailure { failed = true; LOGGER.error("Menu blur unavailable", it) }
            .getOrNull()?.also { chain = it } ?: return false
        if (width != target.viewWidth || height != target.viewHeight) {
            width = target.viewWidth
            height = target.viewHeight
            chain.resize(width, height)
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        try {
            chain.process(0f) // the blur program has no time uniform
        } finally {
            target.bindWrite(true)
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
            GlStateManager.enableTexture()
            GlStateManager.color4f(1f, 1f, 1f, 1f)
        }
        return true
    }
}
*///?}
