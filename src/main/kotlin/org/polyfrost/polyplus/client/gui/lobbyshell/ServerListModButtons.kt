package org.polyfrost.polyplus.client.gui.lobbyshell

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.gui.pausescreen.PauseModSource

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.WidgetPress
import org.polyfrost.polyplus.mixin.client.access.ButtonAccessor
//?} else {
/*import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
*///?}

internal class ServerListModButton(val label: String, val mod: String?, val enabled: Boolean, val press: () -> Unit)

internal object ServerListModButtons {
    private val logger = LogManager.getLogger("PolyPlus/ServerListModButtons")
    private const val UNNAMED = "Mod button"

    fun capture(parent: Screen): List<ServerListModButton> {
        val hidden = JoinMultiplayerScreen(parent)
        return try {
            //? if >= 1.21.11 {
            hidden.init(parent.width, parent.height)
            //?} else {
            /*hidden.init(Minecraft.getInstance(), parent.width, parent.height)
            *///?}
            read(hidden)
        } catch (error: Throwable) {
            logger.warn("Couldn't read other mods' server list buttons", error)
            emptyList()
        } finally {
            runCatching { hidden.removed() }
        }
    }

    private fun named(text: String, mod: String?): String =
        text.removeSuffix("...").removeSuffix("…").trim().ifBlank { mod ?: UNNAMED }

    //? if > 1.8.9 {
    private val VANILLA_KEYS = setOf(
        "selectServer.select", "selectServer.join", "selectServer.direct", "selectServer.add", "selectServer.edit",
        "selectServer.delete", "selectServer.refresh", "gui.back", "gui.done", "gui.cancel",
    )

    private fun read(screen: JoinMultiplayerScreen): List<ServerListModButton> =
        screen.children().filterIsInstance<AbstractButton>()
            .filter { it.visible && (it.message.contents as? TranslatableContents)?.key !in VANILLA_KEYS }
            .map { button ->
                val handler = (button as? Button)?.let { (it as ButtonAccessor).`polyplus$onPress`() }
                val mod = PauseModSource.of(button.javaClass, handler?.javaClass)
                val text = Platform.i18n().getUnformattedText(button.message).ifBlank { WidgetPress.tooltip(button).orEmpty() }
                val label = named(text, mod)
                ServerListModButton(label, mod?.takeIf { it != label }, button.isActive) { WidgetPress.press(button) }
            }
    //?} else {
    /*private val VANILLA_IDS = setOf(0, 1, 2, 3, 4, 7, 8)

    private fun read(screen: JoinMultiplayerScreen): List<ServerListModButton> {
        val access = screen as ScreenAccessor
        return access.`polyplus$buttons`().filter { it.visible && it.id !in VANILLA_IDS }.map { button ->
            val mod = PauseModSource.of(button.javaClass, null)
            val label = named(button.message, mod)
            ServerListModButton(label, mod?.takeIf { it != label }, button.active) {
                ClientPlatform.runOnMain { if (button.active) access.`polyplus$buttonClicked`(button) }
            }
        }
    }
    *///?}
}
