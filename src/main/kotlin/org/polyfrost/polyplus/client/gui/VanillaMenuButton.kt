package org.polyfrost.polyplus.client.gui

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.TitleScreen
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
//?} else
//import net.minecraft.client.gui.widget.ButtonWidget

object VanillaMenuButton {
    private const val SIZE = 20
    private const val MARGIN = 4

    //? if > 1.8.9 {
    private val LATE_PHASE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "late_screen_widgets")

    fun register() {
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
        ScreenEvents.AFTER_INIT.register(LATE_PHASE) { _, screen, _, _ ->
            if (screen !is TitleScreen || MainMenuReplacement.replacesTitleScreen()) return@register
            (screen as ScreenAccessor).polyplusAddRenderableWidget(lobbyButton(screen.width))
        }
    }

    private fun lobbyButton(screenWidth: Int): Button =
        Button.builder(PolyPlusBadge.badgeIcon) { MainMenuReplacement.switchToLobby() }
            .tooltip(Tooltip.create(Component.translatable("polyplus.mainmenu.switchToOneClient")))
            .bounds(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE)
            .build()
    //?} else {
    /*const val LEGACY_BUTTON_ID = 0x504C5553

    fun register() {}

    @JvmStatic
    fun legacyButtons(screenWidth: Int): List<ButtonWidget> =
        if (MainMenuReplacement.replacesTitleScreen()) emptyList()
        else listOf(ButtonWidget(LEGACY_BUTTON_ID, screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE, "OC"))

    @JvmStatic
    fun onLegacyButton(id: Int): Boolean {
        if (id != LEGACY_BUTTON_ID) return false
        MainMenuReplacement.switchToLobby()
        return true
    }
    *///?}
}
