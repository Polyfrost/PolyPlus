package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import net.minecraft.world.level.storage.LevelSummary
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.singleplayer.SingleplayerState
import org.polyfrost.polyplus.client.gui.singleplayer.SingleplayerWorld
import org.polyfrost.polyplus.client.gui.singleplayer.WorldListPhase
import org.polyfrost.polyplus.client.gui.singleplayer.playedText
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal data class LobbyWorld(val id: String, val name: String, val detail: String, val icon: ByteArray?, val playable: Boolean)

internal class LobbyWorlds(@Suppress("UNUSED_PARAMETER") minecraft: Minecraft) {
    private var state by mutableStateOf<SingleplayerState?>(null)

    val saved: List<LobbyWorld> by derivedStateOf {
        val shared = state ?: return@derivedStateOf emptyList()
        if (shared.phase != WorldListPhase.Ready) return@derivedStateOf emptyList()
        shared.worlds.filterNot { it.broken }.map { world(it, shared.iconBytes[it.id]) }
    }

    val loaded: Boolean
        get() = state.let { it != null && it.phase != WorldListPhase.Loading }

    fun reload() {
        val shared = SingleplayerState.of(ClientPlatform.currentScreen())
        state = shared
        shared.reload()
    }

    private fun world(world: SingleplayerWorld, icon: ByteArray?): LobbyWorld =
        LobbyWorld(world.id, world.name, detail(world.summary), icon, world.canPlay)

    private fun detail(summary: LevelSummary): String {
        //? if > 1.8.9 {
        val mode = Platform.i18n().getUnformattedText(if (summary.isHardcore) I18n.get("gameMode.hardcore") else summary.gameMode.shortDisplayName)
        //?} else {
        /*val mode = Platform.i18n().getUnformattedText(if (summary.isHardcore) I18n.get("gameMode.hardcore") else I18n.get("gameMode.${summary.gameMode.key}"))
        *///?}
        return "$mode, ${playedText(summary.lastPlayed)}"
    }
}
