package org.polyfrost.polyplus.client.gui.serveredit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
//? if >= 1.21.11
import net.minecraft.server.network.EventLoopGroupHolder
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyServers
import java.net.UnknownHostException
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal class ServerStatusProbe(private val minecraft: Minecraft, original: ServerData?) {
    private val pinger = ServerStatusPinger()
    private val originalAddress = original?.ip
    private val originalIcon = original?.iconBytes

    @Volatile
    private var closed = false

    var data by mutableStateOf(draft(originalAddress.orEmpty()))
        private set
    var revision by mutableIntStateOf(0)
        private set

    fun reset(address: String) {
        pinger.removeAll()
        data = draft(address)
    }

    fun ping() {
        val target = data
        target.setState(ServerData.State.PINGING)
        target.motd = CommonComponents.EMPTY
        target.status = CommonComponents.EMPTY
        revision++
        PolyPlusClient.SCOPE.launch(PING_DISPATCHER) {
            pinger.pingInto(target, {}) { revision++ }
            if (closed) pinger.removeAll()
        }
    }

    fun tick() {
        pinger.tick()
        val current = data
        if (current.state() != ServerData.State.PINGING) return
        if (LobbyServers.failed(current)) current.setState(ServerData.State.UNREACHABLE)
        revision++
    }

    fun close() {
        closed = true
        pinger.removeAll()
    }

    private fun draft(address: String): ServerData {
        val draft = ServerData("", address, ServerData.Type.OTHER)
        if (address == originalAddress) draft.setIconBytes(originalIcon)
        return draft
    }

    private companion object {
        val PING_DISPATCHER = Dispatchers.IO.limitedParallelism(2)
    }
}

internal const val CANT_RESOLVE_KEY = "multiplayer.status.cannot_resolve"
internal const val CANT_CONNECT_KEY = "multiplayer.status.cannot_connect"

internal fun ServerStatusPinger.pingInto(target: ServerData, onSaved: () -> Unit, onChange: () -> Unit) {
    try {
        pingServer(
            target,
            onSaved,
            {
                val current = LobbyCompat.protocolVersion()
                target.setState(if (target.protocol == current) ServerData.State.SUCCESSFUL else ServerData.State.INCOMPATIBLE)
                ClientPlatform.runOnMain(onChange)
            },
            //? if >= 1.21.11
            EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()),
        )
    } catch (_: UnknownHostException) {
        markUnreachable(target, CANT_RESOLVE_KEY, onChange)
    } catch (_: Exception) {
        markUnreachable(target, CANT_CONNECT_KEY, onChange)
    }
}

private fun markUnreachable(target: ServerData, key: String, onChange: () -> Unit) {
    target.setState(ServerData.State.UNREACHABLE)
    target.motd = Component.translatable(key)
    ClientPlatform.runOnMain(onChange)
}
