package org.polyfrost.polyplus.client.gui.screens.packs

import androidx.compose.runtime.Composable
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
//?} elif = 1.8.9 {
/*import net.minecraft.client.gui.screen.menu.resourcepacks.ResourcePacksScreen
import org.polyfrost.polyplus.mixin.client.access.PackResourcePacksScreenAccessor
*///?}
import org.polyfrost.polyplus.client.gui.screens.Routed
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsCategory
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsPageProvider

object PackRoutes {
    //? if > 1.8.9 {
    fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen !is PackSelectionScreen) return null
        if (screen.javaClass != PackSelectionScreen::class.java && screen.javaClass.name != PACK_MANAGER_SCREEN) return null
        val current = ClientPlatform.currentScreen()
        val parent = PackDetour.backFrom(current) ?: current
        val shell = origin ?: ScreenRouter.originOf(parent) ?: ScreenRouter.fallbackShell()
        return Routed.show(PackShellScreen(shell, parent, PackSession(screen)))
    }

    private const val PACK_MANAGER_SCREEN = "dev.microcontrollers.packmanager.gui.FolderedPackSelectionScreen"
    //?} elif = 1.8.9 {
    /*fun route(screen: Screen, origin: ScreenShell?): Routed? {
        if (screen.javaClass != ResourcePacksScreen::class.java) return null
        val exit = (screen as Any as PackResourcePacksScreenAccessor).`polyplus$parent`()
        return Routed.show(PackShellScreen(origin ?: ScreenRouter.fallbackShell(), ClientPlatform.currentScreen(), PackSession.resources(exit)))
    }
    *///?}
}

internal object PackSettingsPages : SettingsPageProvider {
    override fun handles(category: SettingsCategory): Boolean = category == SettingsCategory.Packs

    @Composable
    override fun Page(category: SettingsCategory, parent: () -> Screen?) {
        val session = remember { PackParking.take() ?: PackSession.resources() }
        SideEffect { session.host = parent }
        DisposableEffect(session) {
            PackDrops.active = session
            onDispose {
                if (PackDrops.active === session) PackDrops.active = null
                if (session.isAway) PackParking.park(session) else session.commitLater()
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PackHeader(category.label, "Changes apply when you leave this page.")
            PackBody(session, Modifier.weight(1f))
        }
    }
}
