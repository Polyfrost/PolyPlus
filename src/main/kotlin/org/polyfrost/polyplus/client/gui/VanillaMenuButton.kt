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
            val accessor = screen as ScreenAccessor
            accessor.polyplusAddRenderableWidget(lobbyButton(screen.width))
            accessor.polyplusAddRenderableWidget(polyPlusMenuButton(screen.width))
        }
    }

    private fun lobbyButton(screenWidth: Int): Button =
        Button.builder(PolyPlusBadge.badgeIcon) { MainMenuReplacement.switchToLobby() }
            .tooltip(Tooltip.create(Component.translatable("polyplus.mainmenu.switchToOneClient")))
            .bounds(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE)
            .build()

    private fun polyPlusMenuButton(screenWidth: Int): Button =
        Button.builder(Component.literal("P+")) { MainMenuReplacement.switchToPolyPlusMenu() }
            .tooltip(Tooltip.create(Component.translatable("polyplus.mainmenu.switchToPolyPlus")))
            .bounds(screenWidth - 2 * (SIZE + MARGIN), MARGIN, SIZE, SIZE)
            .build()
    //?} else {
    /*const val LEGACY_BUTTON_ID = 0x504C5553
    const val LEGACY_POLYPLUS_BUTTON_ID = LEGACY_BUTTON_ID + 1

    fun register() {}

    @JvmStatic
    fun legacyButtons(screenWidth: Int): List<ButtonWidget> =
        if (MainMenuReplacement.replacesTitleScreen()) emptyList()
        else listOf(
            ButtonWidget(LEGACY_BUTTON_ID, screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE, "OC"),
            ButtonWidget(LEGACY_POLYPLUS_BUTTON_ID, screenWidth - 2 * (SIZE + MARGIN), MARGIN, SIZE, SIZE, "P+"),
        )

    @JvmStatic
    fun onLegacyButton(id: Int): Boolean {
        when (id) {
            LEGACY_BUTTON_ID -> MainMenuReplacement.switchToLobby()
            LEGACY_POLYPLUS_BUTTON_ID -> MainMenuReplacement.switchToPolyPlusMenu()
            else -> return false
        }
        return true
    }
    *///?}
}
