package org.polyfrost.polyplus.client.gui.pausescreen

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if = 1.8.9 {
/*import net.minecraft.client.resource.language.I18n as Lang
*///?}

internal class PausePlace(val eyebrow: String, val title: String, val detail: String)

internal class PauseState(val paused: Boolean, val label: String, val detail: String?)

internal object PauseWorld {
    private const val SEPARATOR = " · "

    private val singleplayer: Boolean get() = SocialHosting.singleplayer

    private val hosted: Boolean get() = P2PSessionManager.currentSessionId != null

    fun state(): PauseState = when {
        PausePlatform.gamePaused() || (singleplayer && !SocialHosting.lanOpen && !hosted && ClientPlatform.currentScreen() is OneClientPauseScreen) -> PauseState(true, "Game paused", null)
        singleplayer && hosted -> PauseState(false, "Not paused", "Hosting for friends")
        singleplayer && SocialHosting.lanOpen -> PauseState(false, "Not paused", "Open to LAN")
        singleplayer -> PauseState(false, "Not paused", null)
        else -> PauseState(false, "Not paused", "Multiplayer")
    }

    private fun gameMode(): String = runCatching {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        mc.gameMode?.playerMode?.shortDisplayName?.let(Platform.i18n()::getUnformattedText)
        //?} else {
        /*mc.interactionManager?.gameMode?.toString()?.lowercase()?.replaceFirstChar { it.uppercase() }
        *///?}
    }.getOrNull().orEmpty()

    private fun difficulty(): String = runCatching {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        mc.level?.difficulty?.displayName?.let(Platform.i18n()::getUnformattedText)
        //?} else {
        /*mc.world?.difficulty?.getKey()?.let { Platform.i18n().getUnformattedText(Lang.translate(it)) }
        *///?}
    }.getOrNull().orEmpty()

    private fun serverDetail(): String {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val connection = mc.connection ?: return ""
        val online = connection.onlinePlayers.size
        val ping = connection.getPlayerInfo(ClientPlatform.localPlayerUuid())?.latency ?: 0
        //?} else {
        /*val connection = mc.getNetworkHandler() ?: return ""
        val online = connection.getOnlinePlayers().size
        val ping = connection.getOnlinePlayer(ClientPlatform.localPlayerUuid())?.getPing() ?: 0
        *///?}
        val people = if (online == 1) "1 online" else "$online online"
        return if (ping > 0) people + SEPARATOR + "$ping ms" else people
    }

    private fun serverName(): String {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val server = mc.currentServer
        val hide = mc.options.hideServerAddress
        val name = server?.name?.takeIf { it.isNotBlank() && it != "Minecraft Server" && !(hide && it.trim().equals(server.ip.trim(), true)) }
        return name ?: server?.ip?.takeUnless { hide } ?: "Server"
        //?} else {
        /*val server = mc.getCurrentServerEntry()
        val hide = mc.options.hideServerAddress
        val name = server?.name?.takeIf { it.isNotBlank() && it != "Minecraft Server" && !(hide && it.trim().equals(server.ip.trim(), true)) }
        return name ?: server?.ip?.takeUnless { hide } ?: "Server"
        *///?}
    }

    fun place(): PausePlace {
        if (!singleplayer) return PausePlace("MULTIPLAYER", serverName(), serverDetail())
        val name = SocialHosting.worldName ?: "World"
        val detail = listOf(gameMode(), difficulty()).filter { it.isNotBlank() }.joinToString(SEPARATOR)
        return when {
            hosted -> PausePlace("HOSTED WORLD", name, serverDetail())
            SocialHosting.lanOpen -> PausePlace("OPEN TO LAN", name, detail)
            else -> PausePlace("SINGLEPLAYER", name, detail)
        }
    }
}
