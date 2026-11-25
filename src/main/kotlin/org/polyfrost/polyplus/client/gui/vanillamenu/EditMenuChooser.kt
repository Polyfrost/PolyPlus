package org.polyfrost.polyplus.client.gui.vanillamenu

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor

/** "Edit main menu": pick which of the three menus to edit, then it opens in edit mode. */
class EditMenuChooser(private val parent: Screen?) : Screen(Component.literal("Edit main menu")) {
    override fun init() {
        val access = this as ScreenAccessor
        val title = Component.literal("Which menu do you want to edit?")
        val titleWidth = font.width(title)
        val top = height / 2 - 50
        access.polyplusAddRenderableWidget(StringWidget(width / 2 - titleWidth / 2, top, titleWidth, 9, title, font))
        val choices = listOf(
            "OneClient Lobby" to { MenuLayoutEditor.start("lobby"); MainMenuReplacement.switchToLobby() },
            "PolyPlus Menu" to { MenuLayoutEditor.start("polyplus"); MainMenuReplacement.switchToPolyPlusMenu() },
            "Vanilla Menu" to { MenuLayoutEditor.start(VanillaTitleMenu.scope()); MainMenuReplacement.switchToVanilla() },
        )
        choices.forEachIndexed { index, (label, action) ->
            access.polyplusAddRenderableWidget(Button.builder(Component.literal(label)) { action() }.bounds(width / 2 - 100, top + 20 + index * 24, 200, 20).build())
        }
        access.polyplusAddRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(width / 2 - 100, top + 20 + choices.size * 24 + 8, 200, 20).build())
    }

    override fun onClose() = ClientPlatform.setScreen(parent)

    companion object {
        @JvmStatic
        fun open() = ClientPlatform.setScreen(EditMenuChooser(ClientPlatform.currentScreen()))
    }
}
