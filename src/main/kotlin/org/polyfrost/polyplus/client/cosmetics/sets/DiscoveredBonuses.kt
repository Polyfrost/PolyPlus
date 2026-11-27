package org.polyfrost.polyplus.client.cosmetics.sets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.BonusApi
import org.polyfrost.polyplus.client.network.http.responses.DiscoveredBonus
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

object DiscoveredBonuses {
    private val LOGGER = LogManager.getLogger("polyplus/sets")

    private val _bonuses = MutableStateFlow<List<DiscoveredBonus>>(emptyList())
    val bonuses: StateFlow<List<DiscoveredBonus>> = _bonuses.asStateFlow()

    private var account: UUID? = null
    private var loaded = false
    private var refreshing: UUID? = null
    private val toasted = HashSet<Int>()

    fun isDiscovered(bonusId: Int): Boolean {
        syncAccount()
        return _bonuses.value.any { it.bonusId == bonusId }
    }

    fun ensureLoaded() {
        syncAccount()
        if (!synchronized(this) { loaded }) refresh()
    }

    fun refresh() {
        val target = syncAccount()
        synchronized(this) {
            if (refreshing == target) return
            refreshing = target
        }
        PolyPlusClient.SCOPE.launch {
            try {
                BonusApi.discovered()
                    .onSuccess { list ->
                        synchronized(this@DiscoveredBonuses) {
                            if (account != target) return@onSuccess
                            loaded = true
                            _bonuses.update { current -> merge(list, current) }
                        }
                    }
                    .onFailure { LOGGER.warn("Couldn't load discovered bonuses", it) }
            } finally {
                synchronized(this@DiscoveredBonuses) {
                    if (refreshing == target) refreshing = null
                }
            }
        }
    }

    internal fun handle(packet: ClientboundPacket.BonusDiscovered) {
        val found = DiscoveredBonus(packet.bonusId, packet.name, packet.cosmeticIds)
        syncAccount()
        val firstToast = synchronized(this) {
            _bonuses.update { current -> merge(current, listOf(found)) }
            toasted.add(packet.bonusId)
        }
        if (firstToast) toast(packet.name)
        ensureLoaded()
    }

    fun syncAccount(): UUID {
        val current = ClientPlatform.localPlayerUuid()
        synchronized(this) {
            if (account != current) clear(current)
        }
        return current
    }

    private fun clear(next: UUID) {
        account = next
        loaded = false
        refreshing = null
        toasted.clear()
        _bonuses.value = emptyList()
    }

    private fun merge(first: List<DiscoveredBonus>, second: List<DiscoveredBonus>): List<DiscoveredBonus> {
        val byId = LinkedHashMap<Int, DiscoveredBonus>()
        for (bonus in first + second) byId.putIfAbsent(bonus.bonusId, bonus)
        return byId.values.toList()
    }

    private fun toast(name: String) {
        runCatching { Notifications.info("PolyPlus", "Bonus found: $name") }
            .onFailure { LOGGER.error("Failed to show the bonus toast", it) }
    }
}
