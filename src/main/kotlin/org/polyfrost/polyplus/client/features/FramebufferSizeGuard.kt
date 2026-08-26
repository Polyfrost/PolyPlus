package org.polyfrost.polyplus.client.features

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object FramebufferSizeGuard {
    private val logger = LogManager.getLogger("PolyPlus/Framebuffer")
    private var reported = false

    fun initialize() {
        eventHandler { _: TickEvent.End -> resyncIfStale() }
    }

    private fun resyncIfStale() {
        runCatching {
            val mc = Minecraft.getInstance() ?: return
            val window = mc.window ?: return
            //? if >= 1.21.10 {
            val handle = window.handle()
            //?} else {
            /*val handle = window.window
            *///?}
            if (handle == 0L) return

            val widths = IntArray(1)
            val heights = IntArray(1)
            GLFW.glfwGetFramebufferSize(handle, widths, heights)
            val width = widths[0]
            val height = heights[0]
            if (width <= 0 || height <= 0) return
            if (width == window.width && height == window.height) return

            if (!reported) {
                reported = true
                logger.warn(
                    "Framebuffer size was stale ({}x{}), GLFW reports {}x{} - resizing",
                    window.width, window.height, width, height,
                )
            }
            window.width = width
            window.height = height
            //? if >= 26.1 {
            mc.resizeGui()
            //?} else {
            /*mc.resizeDisplay()
            *///?}
        }.onFailure { logger.warn("Could not re-sync the framebuffer size", it) }
    }
}
