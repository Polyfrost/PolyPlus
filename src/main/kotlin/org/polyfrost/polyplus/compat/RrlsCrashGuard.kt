package org.polyfrost.polyplus.compat

//? if >= 26.2 {
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform

// https://github.com/dima-dencep/rrls/issues/254
object RrlsCrashGuard {
    private val logger = LogManager.getLogger("PolyPlus/RrlsCrashGuard")

    @Volatile
    private var tripped = false
    private var notified = false

    @JvmStatic
    fun active(): Boolean {
        val vulkan = RenderSystem.tryGetDevice()?.deviceInfo?.backendName() == "Vulkan"
        if (vulkan && !tripped) {
            tripped = true
            logger.warn("Disabled Remove Reloading Screen on the Vulkan backend to prevent a crash")
        }
        return vulkan
    }

    fun initialize() {
        eventHandler<TickEvent.End> {
            if (notified || !tripped) return@eventHandler
            val screen = ClientPlatform.currentScreen()
            if (screen !is TitleScreen && screen !is PolyPlusMainMenuScreen) return@eventHandler
            if (Minecraft.getInstance().gui.overlay() != null) return@eventHandler
            notified = true
            Notifications.error("PolyPlus", "Remove Reloading Screen (RRLS) has been disabled to prevent a crash.")
        }
    }

}
//?}
