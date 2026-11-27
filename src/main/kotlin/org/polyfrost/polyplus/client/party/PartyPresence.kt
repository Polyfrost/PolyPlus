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
    @Volatile private var lastSentParty: String? = null
    @Volatile private var lastSentAt = 0L
    @Volatile var local: MemberState = MemberState()
        private set

    fun forceSend() {
        lastSent = null
    }

    fun reset() {
        lastSent = null
        lastSentParty = null
        lastSentAt = 0L
    }

    private class ServerKey(val address: String, val key: String)

    @Volatile private var cachedLocation = MemberLocation()
    @Volatile private var cachedServerKey: ServerKey? = null
    @Volatile private var cachedHostedSession: String? = null
    @Volatile private var cachedHostedKey = ""

    fun tick() {
        val location = location()
        val voice = PartyVoice.memberVoice()
        val scene = PartyScene.localScene()
        val previous = local
        val next = if (previous.location === location && previous.voice === voice && previous.scene === scene) {
            previous
        } else {
            MemberState(location, voice, scene = scene)
        }
        local = next
        val partyId = PartyRepository.party.value?.id
        if (partyId == null) {
            lastSent = null
            lastSentParty = null
            return
        }
        if (next == lastSent && partyId == lastSentParty) return
        val now = System.currentTimeMillis()
        if (now - lastSentAt < MIN_INTERVAL_MS || !PolyConnection.isConnected) return
        if (PolyConnection.sendPacket(ServerboundPacket.SetPartyState(next)).isSuccess) {
            lastSent = next
            lastSentParty = partyId
            lastSentAt = now
        }
    }

    private fun shared(kind: LocationKind, key: String? = null, display: String? = null): MemberLocation {
        val cached = cachedLocation
        if (cached.kind == kind && cached.key == key && cached.display == display) return cached
        return MemberLocation(kind, key, display).also { cachedLocation = it }
    }

    private fun cachedServerKey(address: String): String {
        val cached = cachedServerKey
        if (cached != null && cached.address == address) return cached.key
        val key = serverKey(address)
        cachedServerKey = ServerKey(address, key)
        return key
    }

    fun location(): MemberLocation {
        val mc = Minecraft.getInstance()
        if (mc.level == null || LobbyScene.terrainActive()) return shared(LocationKind.Menu)
        val hosting = P2PSessionManager.currentSessionId
        //? if > 1.8.9 {
        val integrated = mc.hasSingleplayerServer()
        //?} else {
        /*val integrated = mc.isIntegratedServerRunning
        *///?}
        if (integrated) {
            return if (hosting != null) hosted(hosting) else shared(LocationKind.Singleplayer)
        }
        val address = mc.currentServer?.ip?.trim() ?: return shared(LocationKind.Menu)
        if (address == P2PSessionManager.P2P_PLACEHOLDER_IP) {
            val joined = P2PSessionManager.joinedSessionId ?: return shared(LocationKind.HostedWorld)
            return hosted(joined)
        }
        val display = address.take(MAX_DISPLAY).takeIf { PolyPlusPartyConfig.partyShareServer }
        return shared(LocationKind.Server, cachedServerKey(address), display)
    }

    private fun hosted(session: String): MemberLocation {
        if (session != cachedHostedSession) {
            cachedHostedKey = "session:$session"
            cachedHostedSession = session
        }
        return shared(LocationKind.HostedWorld, cachedHostedKey)
    }

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
