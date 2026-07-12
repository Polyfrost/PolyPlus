package org.polyfrost.polyplus.client.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.HttpUtil
import net.minecraft.world.level.GameType
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Path

//? if fabric {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
//?}

object HostWorldManager {
    private val LOGGER = LogManager.getLogger("PolyPlus/HostWorld")

    val clientVersionName: String by lazy {
        //? if <1.21.6 {
        /*net.minecraft.SharedConstants.getCurrentVersion().name*/
        //?} else
        net.minecraft.SharedConstants.getCurrentVersion().name()
    }

    enum class Compat {
        CURRENT,

        OLDER,

        NEWER,

        INCOMPATIBLE,
    }

    data class HostWorldEntry(
        val id: String,
        val name: String,
        val iconBytes: ByteArray?,
        val gameMode: GameType,
        val lastPlayed: Long,
        val requiresConversion: Boolean,
        val versionName: String,
        val compat: Compat,
    )

    private data class PendingHost(val gameMode: GameType, val allowCheats: Boolean)

    @Volatile
    private var pending: PendingHost? = null

    suspend fun loadWorlds(): List<HostWorldEntry> = withContext(Dispatchers.IO) {
        val source = Minecraft.getInstance().levelSource
        val summaries = try {
            val candidates = source.findLevelCandidates()
            source.loadLevelSummaries(candidates).get()
        } catch (e: Exception) {
            LOGGER.error("Failed to enumerate singleplayer worlds", e)
            return@withContext emptyList()
        }

        summaries
            .asSequence()
            .filterNot { it.isDisabled }
            .map { summary ->
                val compat = when {
                    !summary.isCompatible -> Compat.INCOMPATIBLE
                    summary.isDowngrade -> Compat.NEWER
                    summary.shouldBackup() -> Compat.OLDER
                    else -> Compat.CURRENT
                }
                HostWorldEntry(
                    id = summary.levelId,
                    name = summary.levelName,
                    iconBytes = readIcon(summary.icon),
                    gameMode = summary.gameMode,
                    lastPlayed = summary.lastPlayed,
                    requiresConversion = summary.requiresManualConversion(),
                    versionName = summary.worldVersionName.string,
                    compat = compat,
                )
            }
            .sortedByDescending { it.lastPlayed }
            .toList()
    }

    private fun readIcon(icon: Path?): ByteArray? {
        if (icon == null || !Files.isRegularFile(icon)) return null
        return runCatching { Files.readAllBytes(icon) }.getOrNull()
    }

    fun host(returnScreen: Screen, entry: HostWorldEntry, gameMode: GameType, allowCheats: Boolean) {
        val mc = Minecraft.getInstance()
        pending = PendingHost(gameMode, allowCheats)
        mc.createWorldOpenFlows().openWorld(entry.id) {
            pending = null
            //? if >= 26.2 {
            /*mc.gui.setScreen(returnScreen)
            *///?} else {
            mc.setScreen(returnScreen)
            //?}
        }
    }

    fun registerLanPublishHook() {
        //? if fabric {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
        //?}
    }

    private fun tick(mc: Minecraft) {
        val request = pending ?: return
        val server = mc.singleplayerServer ?: return
        if (!server.isReady) return
        if (mc.connection == null) return
        if (server.isPublished) {
            pending = null
            return
        }

        val port = HttpUtil.getAvailablePort()
        val published =
            //? if >= 26.2 {
            /*server.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, request.gameMode, request.allowCheats, port)
            *///?} else {
            server.publishServer(request.gameMode, request.allowCheats, port)
            //?}
        pending = null

        if (published) {
            LOGGER.info("Opened world to LAN on port {} (e4mc will relay if installed)", port)
        } else {
            LOGGER.warn("publishServer returned false — world was not opened to LAN")
        }
    }
}
