@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.worldoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojang.blaze3d.platform.InputConstants
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsPageHeader
import org.polyfrost.polyplus.client.gui.screens.world.WorldContentColumn
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal object WorldOptions {
    fun create(parent: Screen?, shell: ScreenShell? = null): WorldOptionsShell? {
        val snapshot = WorldOptionsPlatform.snapshot() ?: return null
        val host = shell ?: ScreenRouter.originOf(parent) ?: ScreenRouter.fallbackShell()
        lateinit var screen: WorldOptionsShell
        val session = WorldOptionsSession(snapshot) { SocialOverlay.openHostCurrentWorld(screen) }
        screen = WorldOptionsShell(host, parent, session)
        return screen
    }

    fun open(parent: Screen?) {
        create(parent)?.let { ClientPlatform.setScreen(it) }
    }
}

internal class WorldOptionsShell(shell: ScreenShell, parent: Screen?, private val session: WorldOptionsSession) : ShellScreen(shell, parent) {
    override val title: String get() = "World options"

    override val closeOnEscape: Boolean get() = false

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key != InputConstants.KEY_ESCAPE) return super.handleKeyPressed(key, modifiers)
        leave()
        return true
    }

    private fun leave() {
        if (session.page == OptionsPage.Main) back() else session.page = OptionsPage.Main
    }

    private fun apply() {
        WorldOptionsPlatform.apply(session.changes())
        back()
    }

    private fun applyOrAsk() {
        if (!session.lockPending) {
            apply()
            return
        }
        Confirm.ask(
            "Lock the difficulty?",
            "The difficulty stays on ${session.difficultyName} and can't be changed again, ever.",
            "Lock difficulty",
            ::apply,
        )
    }

    @Composable
    override fun Body() {
        val rules = session.page == OptionsPage.Rules
        WorldContentColumn {
            if (rules) {
                RulesPage(session, Modifier.weight(1f))
            } else {
                PanelPageColumn(Modifier.weight(1f)) {
                    SettingsPageHeader("World options", "Changes apply when you press Apply.")
                    session.sections().forEach { OptionsCard(it) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rules) {
                    TextButton("Back", ::leave, style = ControlStyle.Ghost, size = ControlSize.Large)
                } else {
                    TextButton("Cancel", ::back, style = ControlStyle.Ghost, size = ControlSize.Large)
                }
                Spacer(Modifier.weight(1f))
                TextButton("Apply", ::applyOrAsk, style = ControlStyle.Primary, size = ControlSize.Large, enabled = session.dirty && session.valid)
            }
        }
    }
}
