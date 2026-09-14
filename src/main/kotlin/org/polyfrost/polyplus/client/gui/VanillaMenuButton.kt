package org.polyfrost.polyplus.client.gui

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor

object VanillaMenuButton {
    private const val SIZE = 20
    private const val MARGIN = 4

    private val LATE_PHASE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "late_screen_widgets")

    fun register() {
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
        ScreenEvents.AFTER_INIT.register(LATE_PHASE) { _, screen, _, _ ->
            if (screen !is TitleScreen || !PolyPlusMainMenuConfig.useVanillaMainMenu) return@register
            (screen as ScreenAccessor).polyplusAddRenderableWidget(create(screen.width))
        }
    }

    private fun create(screenWidth: Int): Button =
        Button.builder(PolyPlusBadge.badgeIcon) { openPolyPlusMenu() }
            .tooltip(Tooltip.create(Component.translatable("polyplus.mainmenu.switchToOneClient")))
            .bounds(screenWidth - SIZE - MARGIN, MARGIN, SIZE, SIZE)
            .build()

    private fun openPolyPlusMenu() {
        PolyPlusMainMenuConfig.useVanillaMainMenu = false
        PolyPlusMainMenuConfig.save()
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        mc.gui.setScreen(TitleScreen())
        //?} else {
        /*mc.setScreen(TitleScreen())
        *///?}
    }
}
