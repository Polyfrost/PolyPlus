package org.polyfrost.polyplus.client.gui.screens.worldoptions

//? if > 1.8.9 {
import net.minecraft.client.server.IntegratedServer
import net.minecraft.util.HttpUtil
import net.minecraft.world.level.GameType
//?} else {
/*import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.world.WorldSettings.GameMode as GameType
*///?}
import org.polyfrost.polyplus.client.host.HostWorldManager

internal class MultiplayerState(
    val published: Boolean,
    val port: Int,
    val portEditable: Boolean,
    val canStop: Boolean,
    val guestCommands: Boolean?,
    val forceMode: Boolean?,
)

internal class LanChange(val open: Boolean, val port: Int, val mode: Int, val guestCommands: Boolean)

internal object MultiplayerPlatform {
    const val PORT_MIN = 1024
    const val PORT_MAX = 65535

    fun state(server: IntegratedServer): MultiplayerState = MultiplayerState(
        server.isPublished,
        //? if > 1.8.9 {
        server.port,
        true,
        //?} else {
        /*if (server.isPublished) server.serverPort else 0,
        false,
        *///?}
        //? if >= 26.2 {
        true,
        //?} else {
        /*false,
        *///?}
        guestOf(server),
        forceOf(server),
    )

    fun suggestedPort(): Int {
        //? if > 1.8.9 {
        return HttpUtil.getAvailablePort()
        //?} else {
        /*return 0
        *///?}
    }

    fun portAvailable(port: Int): Boolean {
        //? if > 1.8.9 {
        return HttpUtil.isPortAvailable(port)
        //?} else {
        /*return true
        *///?}
    }

    fun refreshPermissions(server: IntegratedServer) {
        //? if > 1.8.9 {
        val list = server.playerList
        list.players.forEach {
            list.sendPlayerPermissionLevel(it)
            server.commands.sendCommands(it)
        }
        //?}
    }

    fun apply(server: IntegratedServer, changes: WorldOptionsChanges) {
        changes.guestCommands?.let { setGuest(server, it) }
        changes.forceMode?.let { setForce(server, it) }
        changes.lan?.let { setLan(server, it) }
    }

    private fun setLan(server: IntegratedServer, change: LanChange) {
        //? if >= 26.2 {
        if (server.isPublished) server.unpublishServer()
        //?}
        if (change.open) HostWorldManager.hostCurrentWorldLan(GameType.byId(change.mode), change.guestCommands, change.port)
    }

    //? if >= 26.3 {
    private fun guestOf(server: IntegratedServer): Boolean? = server.guestCommandAccess

    private fun setGuest(server: IntegratedServer, on: Boolean) = server.setGuestCommandAccess(on)
    //?} elif >= 26.2 {
    /*private fun guestOf(server: IntegratedServer): Boolean? = server.commandsAllowedForOtherPlayers()

    private fun setGuest(server: IntegratedServer, on: Boolean) = server.setCommandsAllowedForOtherPlayers(on)
    *///?} elif > 1.8.9 {
    /*private fun guestOf(server: IntegratedServer): Boolean? = server.playerList.isAllowCommandsForAllPlayers

    private fun setGuest(server: IntegratedServer, on: Boolean) {
        server.execute {
            server.playerList.setAllowCommandsForAllPlayers(on)
            refreshPermissions(server)
        }
    }
    *///?} else {
    /*private fun guestOf(server: IntegratedServer): Boolean? = null

    private fun setGuest(server: IntegratedServer, on: Boolean) {}
    *///?}

    //? if >= 26.3 {
    private fun forceOf(server: IntegratedServer): Boolean? = server.forceGameMode()

    private fun setForce(server: IntegratedServer, on: Boolean) = server.setForceGameMode(on)
    //?} elif > 1.8.9 {
    /*private fun forceOf(server: IntegratedServer): Boolean? = null

    private fun setForce(server: IntegratedServer, on: Boolean) {}
    *///?} else {
    /*private fun forceOf(server: IntegratedServer): Boolean? = server.shouldForceGameMode()

    private fun setForce(server: IntegratedServer, on: Boolean) = server.setForceGameMode(on)
    *///?}
}
