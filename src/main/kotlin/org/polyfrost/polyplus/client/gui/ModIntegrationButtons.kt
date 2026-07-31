package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.screens.Screen

//? if fabric {
import net.fabricmc.loader.api.FabricLoader
//?}

internal class ModIntegrationButton(
    val id: String,
    val icon: String,
    val tooltip: String,
    val isPresent: () -> Boolean,
    val onClick: (Screen) -> Unit,
)

internal object ModIntegrationButtons {
    private const val ASSETS = "assets/polyplus/mainmenu/"

    val all: List<ModIntegrationButton> = listOf(
        ModIntegrationButton(
            id = "flashback",
            icon = ASSETS + "video-recorder.svg",
            tooltip = "Flashback replays",
            isPresent = { modLoaded("flashback") && FlashbackSupport.isUsable },
            onClick = { parent -> FlashbackSupport.openReplaySelection(parent) },
        ),
    )

    fun available(): List<ModIntegrationButton> = all.filter { runCatching { it.isPresent() }.getOrDefault(false) }

    private fun modLoaded(id: String): Boolean {
        //? if fabric {
        return FabricLoader.getInstance().isModLoaded(id)
        //?} else {
        /*return false
        *///?}
    }
}

private object FlashbackSupport {
    private const val SELECT_REPLAY_SCREEN = "com.moulberry.flashback.screen.select_replay.SelectReplayScreen"

    private val constructor by lazy {
        runCatching {
            Class.forName(SELECT_REPLAY_SCREEN, false, javaClass.classLoader)
                .getConstructor(Screen::class.java)
        }.getOrNull()
    }

    val isUsable: Boolean get() = constructor != null

    fun openReplaySelection(parent: Screen) {
        val screen = runCatching { constructor?.newInstance(parent) as? Screen }.getOrNull() ?: return
        val mc = net.minecraft.client.Minecraft.getInstance()
        //? if >= 26.2 {
        /*mc.gui.setScreen(screen)
        *///?} else {
        mc.setScreen(screen)
        //?}
    }
}
