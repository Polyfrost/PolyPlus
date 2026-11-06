package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import net.minecraft.world.level.storage.LevelSummary
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.gui.singleplayer.playedText
import java.nio.file.Files
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal class LobbyWorld(val id: String, val name: String, val detail: String, val icon: ByteArray?, val playable: Boolean)

internal class LobbyWorlds(private val minecraft: Minecraft) {
    var saved by mutableStateOf<List<LobbyWorld>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    fun reload() {
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val worlds = runCatching { read() }
                .onFailure { LOGGER.warn("Unable to list saved worlds", it) }
                .getOrDefault(emptyList())
            ClientPlatform.runOnMain {
                saved = worlds
                loaded = true
            }
        }
    }

    private fun read(): List<LobbyWorld> {
        //? if > 1.8.9 {
        val source = minecraft.levelSource
        val summaries = source.loadLevelSummaries(source.findLevelCandidates()).get()
        //?} else {
        /*val summaries = LevelSummary.all(minecraft)
        *///?}
        //? if > 1.8.9 {
        return summaries.filterNot { it is LevelSummary.CorruptedLevelSummary || it is LevelSummary.SymlinkLevelSummary }.sorted().map(::world)
        //?} else {
        /*return summaries.filterNot { it is LevelSummary.CorruptedLevelSummary }.sorted().map(::world)
        *///?}
    }

    private fun world(summary: LevelSummary): LobbyWorld {
        val icon = runCatching { summary.icon.takeIf(Files::isRegularFile)?.let(Files::readAllBytes) }.getOrNull()
        return LobbyWorld(summary.levelId, summary.levelName, detail(summary), icon, !summary.isDisabled)
    }

    private fun detail(summary: LevelSummary): String {
        //? if > 1.8.9 {
        val mode = Platform.i18n().getUnformattedText(if (summary.isHardcore) I18n.get("gameMode.hardcore") else summary.gameMode.shortDisplayName)
        //?} else {
        /*val mode = Platform.i18n().getUnformattedText(if (summary.isHardcore) I18n.get("gameMode.hardcore") else I18n.get("gameMode.${summary.gameMode.key}"))
        *///?}
        return "$mode, ${playedText(summary.lastPlayed)}"
    }

    private companion object {
        val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    }
}
