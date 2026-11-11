package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.render.platform.GlStateManager
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11

object LegacyMatrixStack {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private var reported = false

    @JvmStatic
    fun depth(): Int = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)

    @JvmStatic
    fun restore(depth: Int) {
        val leaked = depth() - depth
        if (leaked <= 0) {
            return
        }
        if (!reported) {
            reported = true
            LOGGER.warn("HUD rendering left {} matrices on the modelview stack; nameplates pop them each frame so the stack cannot overflow", leaked)
        }
        val mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        repeat(leaked) { GlStateManager.popMatrix() }
        GlStateManager.matrixMode(mode)
    }
}
*///?}
