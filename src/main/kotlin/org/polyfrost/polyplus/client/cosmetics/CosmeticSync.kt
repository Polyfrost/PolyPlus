package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.Batcher
import org.polyfrost.polyplus.utils.EarlyInitializable
import java.time.Duration
import java.util.UUID

object CosmeticSync : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200), HashSet<String>()) { players ->
        subscribePlayers(players.toList())
    }
    private val subscribedPlayers = HashSet<String>()

    override fun earlyInitialize() {
        eventHandler<WorldEvent.Load> {
            PolyPlusClient.refreshCosmetics()
            refreshVisibleSubscriptions()
            Unit
        }.register()

        eventHandler<WorldEvent.Unload> {
            unsubscribeAllPlayers()
            CosmeticCatalog.reset()
            //? if >= 1.21.1
            CosmeticAssetCache.reset()
        }.register()

        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.CosmeticsInfo -> handleCosmeticsInfo(packet)
                is ClientboundPacket.SubscriptionSnapshot -> handleSubscriptionSnapshot(packet)
                is ClientboundPacket.PlayerCosmeticEquipped -> handlePlayerCosmeticEquipped(packet)
                is ClientboundPacket.OwnershipUpdated -> handleOwnershipUpdated(packet)
                //? if >= 1.21.1 {
                is ClientboundPacket.PlayerEmoteStarted -> handleEmotePlay(packet.player, packet.emoteId)
                is ClientboundPacket.PlayerEmoteStopped -> handleEmoteStop(packet.player)
                is ClientboundPacket.EmotePlay -> handleEmotePlay(packet.player, packet.emoteId)
                is ClientboundPacket.EmoteStop -> handleEmoteStop(packet.player)
                //?}
                else -> Unit
            }
        }.register()

        eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? ClientboundPlayerInfoUpdatePacket ?: return@eventHandler
            for (action in packet.actions()) {
                processPlayerInfoAction(action, packet.entries())
            }
        }

        eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? ClientboundPlayerInfoRemovePacket ?: return@eventHandler
            val removed = ArrayList<String>()
            for (uuid in packet.profileIds()) {
                if (!uuid.isRealPlayer()) continue
                CosmeticCatalog.removeRemote(uuid)
                removed.add(uuid.toString())
                //? if >= 1.21.1
                handleEmoteStop(uuid.toString())
            }
            unsubscribePlayers(removed)
            Unit
        }
    }

    fun applyLocalActiveFromCatalog() {
        val active = CosmeticCatalog.localEquipped()
        val client = Minecraft.getInstance().player ?: return
        applyActiveToPlayer(client.uuid, active.ids())
    }

    fun refreshVisibleSubscriptions(): Result<Unit> {
        val level = Minecraft.getInstance().level
            ?: return Result.failure(IllegalStateException("No world is loaded"))
        val visible = level.players()
            .map { it.uuid }
            .filter { it.isRealPlayer() }
            .map(UUID::toString)
            .toSet()

        val stale = subscribedPlayers.filter { it !in visible }
        unsubscribePlayers(stale)
        return subscribePlayers(visible)
    }

    fun resubscribeVisiblePlayers(): Result<Unit> {
        subscribedPlayers.clear()
        return refreshVisibleSubscriptions()
    }

    private fun handleCosmeticsInfo(packet: ClientboundPacket.CosmeticsInfo) {
        for ((uuidString, ids) in packet.all) {
            val uuid = UUID.fromString(uuidString)
            CosmeticCatalog.applyRemoteActive(uuid, ids)
            applyActiveToPlayer(uuid, ids)
        }
    }

    private fun handleSubscriptionSnapshot(packet: ClientboundPacket.SubscriptionSnapshot) {
        for ((uuidString, equipment) in packet.equipped) {
            val uuid = UUID.fromString(uuidString)
            CosmeticCatalog.applyRemoteEquipped(uuid, equipment)
            applyActiveToPlayer(uuid, equipment.values.toList())
        }
        for ((uuidString, emoteId) in packet.activeEmotes) {
            handleEmotePlay(uuidString, emoteId)
        }
    }

    private fun handlePlayerCosmeticEquipped(packet: ClientboundPacket.PlayerCosmeticEquipped) {
        val uuid = UUID.fromString(packet.player)
        CosmeticCatalog.applyRemoteEquippedSlot(uuid, packet.slot, packet.cosmeticId)
        applyActiveToPlayer(uuid, packet.cosmeticId?.let(::listOf).orEmpty())
    }

    private fun handleOwnershipUpdated(packet: ClientboundPacket.OwnershipUpdated) {
        if (UUID.fromString(packet.player) != ClientPlatform.localPlayerUuid()) return
        if (packet.cosmeticIds.isNotEmpty() || packet.emoteIds.isNotEmpty()) {
            PolyPlusClient.refreshCosmetics()
        }
    }

    //? if >= 1.21.1 {
    private fun handleEmotePlay(playerUuid: String, emoteId: Int) {
        val uuid = UUID.fromString(playerUuid)
        val player = findPlayer(uuid) ?: return
        PolyPlusClient.SCOPE.launch {
            if (!CosmeticAssetCache.ensureEmoteLoaded(emoteId)) return@launch
            val emote = CosmeticAssetCache.getEmote(emoteId) ?: return@launch
            ClientPlatform.runOnMain {
                (player as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
            }
        }
    }

    private fun handleEmoteStop(player: String) {
        val uuid = UUID.fromString(player)
        val player = findPlayer(uuid) ?: return
        (player as PlayerEmotesAccess).`polyplus$emoteController`().stop()
    }
    //?}

    private fun applyActiveToPlayer(uuid: UUID, cosmeticIds: List<Int>) {
        val player = findPlayer(uuid)
        if (player == null) {
            LOGGER.debug("Deferred cosmetic apply for {} ({} id(s)) — player not loaded", uuid, cosmeticIds.size)
            return
        }

        for (id in cosmeticIds) {
            val definition = CosmeticCatalog.getDefinition(id) ?: continue
            when (definition.type) {
                CosmeticType.Cape -> Unit
                CosmeticType.Backpack,
                CosmeticType.Glasses,
                CosmeticType.Wings,
                CosmeticType.Glove -> Unit
                //? if >= 1.21.1 {
                CosmeticType.Emote -> applyEmote(player, id)
                //?}
            }
        }
    }

    //? if >= 1.21.1 {
    private fun applyEmote(player: AbstractClientPlayer, cosmeticId: Int) {
        PolyPlusClient.SCOPE.launch {
            if (!CosmeticAssetCache.ensureEmoteLoaded(cosmeticId)) return@launch
            val emote = CosmeticAssetCache.getEmote(cosmeticId) ?: return@launch
            ClientPlatform.runOnMain {
                (player as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
            }
        }
    }
    //?}

    private fun processPlayerInfoAction(
        action: ClientboundPlayerInfoUpdatePacket.Action,
        entries: List<ClientboundPlayerInfoUpdatePacket.Entry>,
    ) {
        when (action) {
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER -> {
                entries.forEach { entry ->
                    val uuid = entry.profileId()
                    if (uuid.isRealPlayer()) {
                        BATCHER.add(uuid.toString())
                    }
                }
            }
            else -> return
        }
    }

    private fun subscribePlayers(players: Iterable<String>): Result<Unit> {
        val added = players
            .mapNotNull(::normalizePlayerUuid)
            .filter(subscribedPlayers::add)
        if (added.isEmpty()) {
            return Result.success(Unit)
        }

        val result = PolyConnection.sendPacket(ServerboundPacket.SubscribePlayers(added))
        if (result.isFailure) {
            subscribedPlayers.removeAll(added)
        }
        return result
    }

    private fun unsubscribePlayers(players: Iterable<String>): Result<Unit> {
        val removed = players
            .mapNotNull(::normalizePlayerUuid)
            .filter(subscribedPlayers::remove)
        if (removed.isEmpty()) {
            return Result.success(Unit)
        }
        return PolyConnection.sendPacket(ServerboundPacket.UnsubscribePlayers(removed))
    }

    private fun unsubscribeAllPlayers() {
        val players = subscribedPlayers.toList()
        subscribedPlayers.clear()
        if (players.isNotEmpty()) {
            PolyConnection.sendPacket(ServerboundPacket.UnsubscribePlayers(players))
        }
    }

    private fun findPlayer(uuid: UUID): AbstractClientPlayer? {
        val level = Minecraft.getInstance().level ?: return null
        return level.players().firstOrNull { it.uuid == uuid }
    }

    private fun normalizePlayerUuid(uuidString: String): String? {
        val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: return null
        return uuid.takeIf { it.isRealPlayer() }?.toString()
    }

    private fun UUID.isRealPlayer(): Boolean = version() != 2
}
