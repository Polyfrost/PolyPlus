package org.polyfrost.polyplus.client.party

import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.currentServer
import org.polyfrost.polyplus.client.lobby.level
*///?}
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.MemberLocation
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

object PartyPresence {
    private const val MIN_INTERVAL_MS = 250L
    private const val DEFAULT_PORT = 25565
    private const val KEY_LENGTH = 22
    private const val MAX_DISPLAY = 255

    @Volatile private var lastSent: MemberState? = null
    @Volatile private var lastSentAt = 0L
    @Volatile var local: MemberState = MemberState()
        private set

    fun forceSend() {
        lastSent = null
    }

    fun tick() {
        val next = MemberState(location(), PartyVoice.memberVoice(), scene = PartyScene.localScene())
        local = next
        if (PartyRepository.party.value == null) {
            lastSent = null
            return
        }
        if (next == lastSent) return
        val now = System.currentTimeMillis()
        if (now - lastSentAt < MIN_INTERVAL_MS || !PolyConnection.isConnected) return
        if (PolyConnection.sendPacket(ServerboundPacket.SetPartyState(next)).isSuccess) {
            lastSent = next
            lastSentAt = now
        }
    }

    fun location(): MemberLocation {
        val mc = Minecraft.getInstance()
        if (mc.level == null || LobbyScene.terrainActive()) return MemberLocation(LocationKind.Menu)
        val hosting = P2PSessionManager.currentSessionId
        //? if > 1.8.9 {
        val integrated = mc.hasSingleplayerServer()
        //?} else {
        /*val integrated = mc.isIntegratedServerRunning
        *///?}
        if (integrated) {
            return if (hosting != null) hosted(hosting) else MemberLocation(LocationKind.Singleplayer)
        }
        val address = mc.currentServer?.ip?.trim() ?: return MemberLocation(LocationKind.Menu)
        if (address == P2PSessionManager.P2P_PLACEHOLDER_IP) {
            val joined = P2PSessionManager.joinedSessionId ?: return MemberLocation(LocationKind.HostedWorld)
            return hosted(joined)
        }
        val display = address.take(MAX_DISPLAY).takeIf { PolyPlusPartyConfig.partyShareServer }
        return MemberLocation(LocationKind.Server, serverKey(address), display)
    }

    private fun hosted(session: String) = MemberLocation(LocationKind.HostedWorld, "session:$session")

    fun serverKey(address: String): String {
        val (host, port) = split(address.trim())
        val digest = MessageDigest.getInstance("SHA-256").digest("${host.lowercase(Locale.ROOT)}:$port".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(KEY_LENGTH)
    }

    private fun split(address: String): Pair<String, Int> {
        if (address.startsWith("[")) {
            val end = address.indexOf(']')
            if (end > 0) {
                val host = address.substring(1, end)
                val port = address.substring(end + 1).removePrefix(":").toIntOrNull() ?: DEFAULT_PORT
                return host to port
            }
        }
        val colon = address.lastIndexOf(':')
        if (colon > 0 && address.indexOf(':') == colon) {
            val port = address.substring(colon + 1).toIntOrNull()
            if (port != null) return address.substring(0, colon) to port
        }
        return address to DEFAULT_PORT
    }
}
