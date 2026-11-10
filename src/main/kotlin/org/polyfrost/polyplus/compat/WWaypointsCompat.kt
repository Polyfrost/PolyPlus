package org.polyfrost.polyplus.compat

//? if wwaypoints {
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.utils.ServerUtils
import org.polyfrost.polyplus.mixin.compat.wwaypoints.ServerFeaturePolicyInvoker

object WWaypointsCompat {
    // cached once a tick since wWaypoints checks its policy several times a frame
    @JvmStatic
    @Volatile
    var isOnHypixel = false
        private set

    fun initialize() {
        if (!FabricLoader.getInstance().isModLoaded("wwaypoints")) return
        eventHandler { _: TickEvent.End ->
            val onHypixel = ServerUtils.isOnHypixel()
            if (onHypixel == isOnHypixel) return@eventHandler
            isOnHypixel = onHypixel
            // wWaypoints only tells its listeners (like OneConfig's greyed out rows) about policy changes the server sends
            ServerFeaturePolicyInvoker.`polyplus$notifyListeners`()
        }
    }
}
//?}
