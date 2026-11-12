package org.polyfrost.polyplus.client.gui.lobbyshell

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.pausescreen.PauseModSource
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.WidgetPress
import org.polyfrost.polyplus.mixin.client.access.ButtonAccessor
//?} else {
/*import org.lwjgl.input.Keyboard
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
*///?}

internal class ServerListModButton(val label: String, val mod: String?, val enabled: Boolean, val press: () -> Unit)

internal object ServerListModButtons {
    private val logger = LogManager.getLogger("PolyPlus/ServerListModButtons")
    private const val UNNAMED = "Mod button"

    private class Found(
        val type: Class<*>,
        val key: String,
        val label: String,
        val mod: String?,
        val enabled: Boolean,
        val press: () -> Unit,
    )

    fun capture(parent: Screen): List<ServerListModButton> = keepingInputState {
        val hidden = JoinMultiplayerScreen(parent)
        try {
            //? if >= 1.21.11 {
            hidden.init(parent.width, parent.height)
            //?} else {
            /*hidden.init(Minecraft.getInstance(), parent.width, parent.height)
            *///?}
            read(hidden).mapIndexed { index, found ->
                val type = found.type
                val key = found.key
                val label = found.label
                ServerListModButton(label, found.mod?.takeIf { it != label }, found.enabled) {
                    pressInVanilla(parent, type, key, label, index)
                }
            }
        } catch (error: Throwable) {
            logger.warn("Couldn't read other mods' server list buttons", error)
            emptyList()
        } finally {
            runCatching { hidden.removed() }
        }
    }

    private fun pressInVanilla(parent: Screen, type: Class<*>, key: String, label: String, index: Int) {
        ClientPlatform.runOnMain {
            MainMenuReplacement.openVanillaServerList(JoinMultiplayerScreen(parent))
            val screen = ClientPlatform.currentScreen() as? JoinMultiplayerScreen
            if (screen == null) {
                logger.warn("Couldn't open the vanilla server list for {}", label)
                return@runOnMain
            }
            val candidates = read(screen)
            val match = candidates.firstOrNull { it.type == type && it.key == key }
                ?: candidates.getOrNull(index)?.takeIf { it.type == type }
            if (match == null) {
                logger.warn("Couldn't find {} on the vanilla server list", label)
                return@runOnMain
            }
            match.press()
        }
    }

    private fun named(text: String, mod: String?): String =
        text.removeSuffix("...").removeSuffix("…").trim().ifBlank { mod ?: UNNAMED }

    //? if > 1.8.9 {
    private val VANILLA_KEYS = setOf(
        "selectServer.select", "selectServer.join", "selectServer.direct", "selectServer.add", "selectServer.edit",
        "selectServer.delete", "selectServer.refresh", "gui.back", "gui.done", "gui.cancel",
    )

    private fun <T> keepingInputState(block: () -> T): T = block()

    private fun read(screen: JoinMultiplayerScreen): List<Found> =
        screen.children().filterIsInstance<AbstractButton>()
            .filter { it.visible && (it.message.contents as? TranslatableContents)?.key !in VANILLA_KEYS }
            .map { button ->
                val handler = (button as? Button)?.let { (it as ButtonAccessor).`polyplus$onPress`() }
                val mod = PauseModSource.of(button.javaClass, handler?.javaClass)
                val text = Platform.i18n().getUnformattedText(button.message).ifBlank { WidgetPress.tooltip(button).orEmpty() }
                val key = (button.message.contents as? TranslatableContents)?.key ?: text
                Found(button.javaClass, key, named(text, mod), mod, button.isActive) { WidgetPress.press(button) }
            }
    //?} else {
    /*private val VANILLA_IDS = setOf(0, 1, 2, 3, 4, 7, 8)

    private fun <T> keepingInputState(block: () -> T): T {
        val repeat = Keyboard.areRepeatEventsEnabled()
        return try {
            block()
        } finally {
            Keyboard.enableRepeatEvents(repeat)
        }
    }

    private fun read(screen: JoinMultiplayerScreen): List<Found> {
        val access = screen as ScreenAccessor
        return access.`polyplus$buttons`().filter { it.visible && it.id !in VANILLA_IDS }.map { button ->
            val mod = PauseModSource.of(button.javaClass, null)
            Found(button.javaClass, "${button.id}:${button.message}", named(button.message, mod), mod, button.active) {
                if (button.active) access.`polyplus$buttonClicked`(button)
            }
        }
    }
    *///?}
}
