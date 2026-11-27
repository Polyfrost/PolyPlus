package org.polyfrost.polyplus.client.cosmetics.sets

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEquips
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BonusState {
    const val MAX_ACTIVE = 2

    private val byPlayer = ConcurrentHashMap<UUID, List<BonusRef>>()

    fun effectsFor(player: UUID): List<BonusRef> = byPlayer[accountId(player)].orEmpty()

    fun hasBonuses(player: UUID): Boolean = byPlayer.containsKey(accountId(player))

    fun activeRefs(): Set<BonusRef> = byPlayer.values.flatMapTo(HashSet()) { it }

    internal fun applySnapshot(players: Collection<String>, bonuses: Map<String, List<BonusRef>>) {
        for (uuidString in players + bonuses.keys) {
            val uuid = parse(uuidString) ?: continue
            put(uuid, bonuses[uuidString].orEmpty())
        }
    }

    internal fun handle(packet: ClientboundPacket.PlayerBonusesChanged): Boolean {
        val uuid = parse(packet.player) ?: return false
        val before = byPlayer[uuid].orEmpty()
        put(uuid, packet.bonuses)
        val after = byPlayer[uuid].orEmpty()
        CosmeticEquips.bonusesChanged(uuid, before, after)
        return dropped(before, after)
    }

    internal fun dropped(before: List<BonusRef>, after: List<BonusRef>): Boolean = before.any { it !in after }

    internal fun forget(player: UUID) {
        byPlayer.remove(player)
    }

    internal fun reset() {
        byPlayer.clear()
    }

    private fun put(player: UUID, bonuses: List<BonusRef>) {
        val kept = bonuses.distinctBy { it.id }.take(MAX_ACTIVE)
        if (kept.isEmpty()) byPlayer.remove(player) else byPlayer[player] = kept
    }

    private fun parse(uuid: String): UUID? = runCatching { UUID.fromString(uuid) }.getOrNull()

    internal fun accountId(uuid: UUID): UUID =
        if (uuid == Minecraft.getInstance().player?.uuid) ClientPlatform.localPlayerUuid() else uuid
}
