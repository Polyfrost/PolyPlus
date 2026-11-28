package org.polyfrost.polyplus.compat

import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.reflect.Method

/**
 * Mod Update Checker (https://github.com/ooTruffle/ModUpdateChecker) puts its button on the vanilla
 * title screen, which PolyPlus's menus replace, so they show their own. 1.8.9 only, and reflective so
 * the mod stays optional.
 */
internal object ModUpdateChecker {
    private const val MOD_ID = "modupdatechecker"
    private const val CHECKER = "com.ootruffle.modupdatechecker.UpdateChecker"
    private const val SCREEN = "com.ootruffle.modupdatechecker.ui.UpdatesScreen"

    data class Status(val updates: Int, val checking: Boolean) {
        val label: String
            get() = when {
                checking -> "Checking mods..."
                updates == 1 -> "Mod Updates (1 update)"
                updates > 0 -> "Mod Updates ($updates updates)"
                else -> "Mod Updates"
            }
    }

    private class Api(val updateCount: Method, val running: Method, val screens: Any, val create: Method)

    private val api: Api? by lazy {
        if (!Mods.loaded(MOD_ID)) return@lazy null
        runCatching {
            val loader = javaClass.classLoader
            val checker = Class.forName(CHECKER, false, loader)
            val screen = Class.forName(SCREEN, false, loader)
            Api(
                updateCount = checker.getMethod("updateCount"),
                running = checker.getMethod("running"),
                screens = screen.getField("INSTANCE").get(null),
                create = screen.getMethod("create", Screen::class.java),
            )
        }.getOrNull()
    }

    val isPresent: Boolean get() = api != null

    fun status(): Status {
        val api = api ?: return Status(0, false)
        return runCatching { Status(api.updateCount.invoke(null) as Int, api.running.invoke(null) as Boolean) }
            .getOrDefault(Status(0, false))
    }

    fun open(parent: Screen) {
        val api = api ?: return
        val screen = runCatching { api.create.invoke(api.screens, parent) as? Screen }.getOrNull() ?: return
        ClientPlatform.setScreen(screen)
    }
}
