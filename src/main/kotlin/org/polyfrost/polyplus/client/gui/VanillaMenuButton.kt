package org.polyfrost.polyplus.client.gui

//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
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
            val button = when {
                screen is OptionsScreen -> optionsButton(screen.width)
                screen is TitleScreen && !MainMenuReplacement.replacesTitleScreen() -> lobbyButton(screen.width)
                else -> return@register
            }
            (screen as ScreenAccessor).polyplusAddRenderableWidget(button)
        }
    }

    private fun optionsButton(screenWidth: Int): Button =
        Button.builder(PolyPlusBadge.badgeIcon) { PolyPlusOneConfigIntegration.openOneClientSettings() }
            .tooltip(Tooltip.create(Component.translatableWithFallback("polyplus.options.oneclient", "OneClient settings")))
            .bounds(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE)
            .build()

    private fun lobbyButton(screenWidth: Int): Button {
        val unavailable = MainMenuReplacement.lobbyUnavailable()
        val tooltip = if (unavailable) {
            Component.translatable("polyplus.mainmenu.oneClientUnavailable")
        } else {
            Component.translatable("polyplus.mainmenu.switchToOneClient")
        }
        return Button.builder(PolyPlusBadge.badgeIcon) { MainMenuReplacement.switchToLobby() }
            .tooltip(Tooltip.create(tooltip))
            .bounds(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE)
            .build()
            .also { it.active = !unavailable }
    }
    //?} else {
    /*const val LEGACY_BUTTON_ID = 0x504C5553

    fun register() {}

    @JvmStatic
    fun legacyButtons(screenWidth: Int): List<ButtonWidget> =
        if (MainMenuReplacement.replacesTitleScreen()) emptyList()
        else listOf(ButtonWidget(LEGACY_BUTTON_ID, screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE, "OC").also { it.active = !MainMenuReplacement.lobbyUnavailable() })

    @JvmStatic
    fun onLegacyButton(id: Int): Boolean {
        if (id != LEGACY_BUTTON_ID) return false
        MainMenuReplacement.switchToLobby()
        return true
    }

    const val LEGACY_OPTIONS_BUTTON_ID = 0x504C5554

    @JvmStatic
    fun legacyOptionsButton(screenWidth: Int): ButtonWidget =
        ButtonWidget(LEGACY_OPTIONS_BUTTON_ID, screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE, "OC")

    @JvmStatic
    fun onLegacyOptionsButton(id: Int): Boolean {
        if (id != LEGACY_OPTIONS_BUTTON_ID) return false
        PolyPlusOneConfigIntegration.openOneClientSettings()
        return true
    }
    *///?}
}
