package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.gui.serveredit.ServerStatusProbe
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import net.minecraft.client.multiplayer.ServerData

class ServerCheck(address: String) {
    enum class Result { PENDING, OK, INCOMPATIBLE, UNREACHABLE }

    private val probe = ServerStatusProbe(Minecraft.getInstance(), null).also {
        it.reset(address)
        it.ping()
    }

    var result = Result.PENDING
        private set

    val serverVersion: String get() = runCatching { probe.data.version.string }.getOrDefault("")

    fun poll(): Result {
        if (result != Result.PENDING) return result
        probe.tick()
        result = when (probe.data.state()) {
            ServerData.State.SUCCESSFUL -> Result.OK
            ServerData.State.INCOMPATIBLE -> Result.INCOMPATIBLE
            ServerData.State.UNREACHABLE -> Result.UNREACHABLE
            else -> Result.PENDING
        }
        if (result != Result.PENDING) probe.close()
        return result
    }

    fun close() = probe.close()
}

object LeaderFollow {
    private const val CARD_MS = 60_000L

    class Card(val leader: String, val name: String, val address: String, val key: String, var shownAt: Long) {
        var check by mutableStateOf(ServerCheck.Result.PENDING)
        var needs by mutableStateOf<String?>(null)
        var checking by mutableStateOf(false)
        var cleared by mutableStateOf(false)
        var probe: ServerCheck? = null
    }

    var card by mutableStateOf<Card?>(null)
        private set

    private var lastKey: String? = null
    private var lastParty: String? = null

    fun dismiss() {
        card?.probe?.close()
        card = null
    }

    fun requestJoin() {
        val current = card ?: return
        if (current.probe != null || current.cleared) return
        current.shownAt = System.currentTimeMillis()
        current.checking = true
        current.probe = ServerCheck(current.address)
    }

    fun tick() {
        val party = PartyRepository.party.value
        if (party == null || PartyRepository.isLeader(party)) {
            if (card != null) dismiss()
            lastKey = null
            lastParty = party?.id
            return
        }
        val leader = party.member(party.leader)
        val location = leader?.state?.location
        val key = location?.takeIf { it.kind == LocationKind.Server && it.display != null }?.key
        if (party.id != lastParty) {
            lastParty = party.id
            lastKey = key
            return
        }
        if (key != lastKey) {
            lastKey = key
            dismiss()
            val address = location?.display
            if (key != null && address != null && key != PartyPresence.local.location.key && leader != null) {
                val name = PlayerNamesRepository.nameOr(leader.player)
                card = Card(leader.player, name, address, key, System.currentTimeMillis())
                if (PartyPresence.local.location.kind != LocationKind.Menu) {
                    Notifications.info("PolyPlus", "$name is playing on $address. Open Social to join.")
                }
            }
        }
        val current = card ?: return
        if (System.currentTimeMillis() - current.shownAt > CARD_MS || current.key == PartyPresence.local.location.key) {
            dismiss()
            return
        }
        val probe = current.probe ?: return
        val result = probe.poll()
        if (result != ServerCheck.Result.PENDING) {
            current.check = result
            if (result == ServerCheck.Result.INCOMPATIBLE) current.needs = probe.serverVersion.ifBlank { null } else current.cleared = true
            current.checking = false
            current.probe = null
        }
    }
}
