package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
//? if >= 1.21.1
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
//? if >= 1.21.1
import org.polyfrost.polyplus.client.pets.PetManager
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object CosmeticSync : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200), HashSet<String>()) { players ->
        subscribePlayers(players.toList())
    }
    private val subscribedPlayers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingSubscriptions = ConcurrentHashMap<Long, List<String>>()
    private val nextRequestId = AtomicLong()

    private const val MAX_PLAYERS_PER_REQUEST = 64

    private const val MAX_PLAYER_SUBSCRIPTIONS = 512

    @Volatile
    private var capWarned = false

    //? if >= 1.21.1 {
    private const val RECONCILE_INTERVAL_TICKS = 20
    private var reconcileTicks = 0

    private const val RESUBSCRIBE_INTERVAL_TICKS = 600
    private var resubscribeTicks = 0
    //?}

    override fun earlyInitialize() {
        eventHandler<WorldEvent.Load> {
            PolyPlusClient.refreshCosmetics()
            refreshVisibleSubscriptions()
            Unit
        }.register()

        //? if >= 1.21.1 {
        eventHandler<TickEvent.End> {
            if (++reconcileTicks >= RECONCILE_INTERVAL_TICKS) {
                reconcileTicks = 0
                reconcileVisiblePlayers()
            }
            if (++resubscribeTicks >= RESUBSCRIBE_INTERVAL_TICKS) {
                resubscribeTicks = 0
                refreshVisibleSubscriptions()
            }
            Unit
        }.register()
        //?}

        eventHandler<WorldEvent.Unload> {
            unsubscribeAllPlayers()
            CosmeticCatalog.reset()
            //? if >= 1.21.1 {
            CosmeticAssetCache.reset()
            PetManager.despawnAll()
            //?}
        }.register()

        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.CosmeticsInfo -> handleCosmeticsInfo(packet)
                is ClientboundPacket.SubscriptionSnapshot -> handleSubscriptionSnapshot(packet)
                is ClientboundPacket.PlayerCosmeticEquipped -> handlePlayerCosmeticEquipped(packet)
                is ClientboundPacket.PlayerParticleColorChanged -> handleParticleColorChanged(packet)
                is ClientboundPacket.PlayerPresence -> {
                    LOGGER.info("PolyPlus presence: {} is now {}", packet.player, if (packet.online) "online" else "offline")
                    CosmeticCatalog.setPolyPlusUser(UUID.fromString(packet.player), packet.online)
                }
                is ClientboundPacket.OwnershipUpdated -> handleOwnershipUpdated(packet)
                is ClientboundPacket.Error -> handleError(packet)
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
                //? if >= 1.21.1 {
                handleEmoteStop(uuid.toString())
                PetManager.despawn(uuid)
                //?}
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
        val mc = Minecraft.getInstance()
        val level = mc.level
            ?: return Result.failure(IllegalStateException("No world is loaded"))

        val visible = LinkedHashSet<String>()
        val self = mc.player
        val loaded = level.players().let { players ->
            if (self == null) players else players.sortedBy { it.distanceToSqr(self) }
        }
        for (player in loaded) {
            if (visible.size >= MAX_PLAYER_SUBSCRIPTIONS) break
            normalizePlayerUuid(player.uuid.toString())?.let(visible::add)
        }
        for (uuid in mc.connection?.onlinePlayerIds ?: emptyList()) {
            if (visible.size >= MAX_PLAYER_SUBSCRIPTIONS) break
            normalizePlayerUuid(uuid.toString())?.let(visible::add)
        }

        val stale = subscribedPlayers.filter { it !in visible }
        unsubscribePlayers(stale)
        return subscribePlayers(visible)
    }

    fun resubscribeVisiblePlayers(): Result<Unit> {
        subscribedPlayers.clear()
        pendingSubscriptions.clear()
        capWarned = false
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
        packet.requestId?.let(pendingSubscriptions::remove)
        if (packet.rejected.isNotEmpty()) {
            rollBackSubscriptions(packet.rejected)
            LOGGER.warn("PolyPlus server rejected {} subscription(s).", packet.rejected.size)
        }
        for ((uuidString, equipment) in packet.equipped) {
            val uuid = UUID.fromString(uuidString)
            CosmeticCatalog.applyRemoteEquipped(uuid, equipment)
            applyActiveToPlayer(uuid, equipment.values.toList())
        }
        for ((uuidString, color) in packet.particleColors) {
            CosmeticCatalog.setParticleColor(UUID.fromString(uuidString), color)
        }
        for ((uuidString, emoteId) in packet.activeEmotes) {
            handleEmotePlay(uuidString, emoteId)
        }
        if (packet.users.isNotEmpty()) {
            LOGGER.info("PolyPlus presence snapshot: {} online user(s): {}", packet.users.size, packet.users)
        }
        for (uuidString in packet.users) {
            CosmeticCatalog.setPolyPlusUser(UUID.fromString(uuidString), true)
        }
    }

    private fun handlePlayerCosmeticEquipped(packet: ClientboundPacket.PlayerCosmeticEquipped) {
        val uuid = UUID.fromString(packet.player)
        CosmeticCatalog.applyRemoteEquippedSlot(uuid, packet.slot, packet.cosmeticId)
        applyActiveToPlayer(uuid, packet.cosmeticId?.let(::listOf).orEmpty())
    }

    private fun handleParticleColorChanged(packet: ClientboundPacket.PlayerParticleColorChanged) {
        val uuid = UUID.fromString(packet.player)
        if (packet.color != null) {
            CosmeticCatalog.setParticleColor(uuid, packet.color)
        } else {
            CosmeticCatalog.clearParticleColor(uuid)
        }
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

        //? if >= 1.21.1 {
        reconcileAttachedCosmetics(player, uuid)
        reconcilePet(uuid)
        //?}

        for (id in cosmeticIds) {
            val definition = CosmeticCatalog.getDefinition(id) ?: continue
            when (definition.type) {
                CosmeticType.Cape -> Unit
                // Backpack/Glasses/Wings/Glove are reconciled from the catalog
                // above so unequips are handled even when no id is passed here.
                CosmeticType.Backpack,
                CosmeticType.Glasses,
                CosmeticType.Wings,
                CosmeticType.Glove,
                CosmeticType.Hat,
                CosmeticType.Aura,
                CosmeticType.Boots,
                CosmeticType.Shoulder,
                CosmeticType.Pet -> Unit
                CosmeticType.Unknown -> Unit
                //? if >= 1.21.1 {
                CosmeticType.Emote -> applyEmote(player, id)
                //?}
            }
        }
    }

    //? if >= 1.21.1 {
    private val ATTACHED_SLOTS = listOf(
        BodySlot.Backpack,
        BodySlot.Glasses,
        BodySlot.Wings,
        BodySlot.LeftHand,
        BodySlot.RightHand,
        BodySlot.Hat,
        BodySlot.Aura,
        BodySlot.Boots,
        BodySlot.Shoulder,
        BodySlot.Pet,
    )

    private fun reconcileVisiblePlayers() {
        val level = Minecraft.getInstance().level ?: return
        for (player in level.players()) {
            val uuid = player.uuid
            if (!uuid.isRealPlayer()) continue
            if (CosmeticCatalog.getRemoteEquipped(uuid) == null) continue
            reconcileAttachedCosmetics(player, uuid)
            reconcilePet(uuid)
        }
    }

    private fun reconcileAttachedCosmetics(player: AbstractClientPlayer, uuid: UUID) {
        val equipped = CosmeticCatalog.getRemoteEquipped(uuid).orEmpty()
        for (slot in ATTACHED_SLOTS) {
            val desiredId = equipped[slot]
            val current = CosmeticApi.equippedSlot(player, slot)

            if (desiredId == null) {
                if (current != null) CosmeticApi.unequipSlot(player, slot)
                continue
            }

            PolyPlusClient.SCOPE.launch {
                if (!CosmeticAssetCache.ensureCosmeticLoaded(desiredId)) return@launch
                val attached = CosmeticAssetCache.getAttachedCosmetic(desiredId) ?: return@launch
                if (current != null &&
                    current.cosmetic.id == CosmeticAssetCache.attachedCosmeticId(desiredId) &&
                    current.cosmetic == attached
                ) {
                    return@launch
                }
                ClientPlatform.runOnMain {
                    if (CosmeticCatalog.getActiveId(uuid, slot) != desiredId) return@runOnMain
                    CosmeticApi.unequipSlot(player, slot)
                    CosmeticApi.equipLocal(player, attached.copy(slot = slot))
                }
            }
        }
    }

    private fun reconcilePet(uuid: UUID) {
        val equipped = CosmeticCatalog.getRemoteEquipped(uuid).orEmpty()
        val desiredId = equipped[BodySlot.Pet]

        if (desiredId == null) {
            PetManager.despawn(uuid)
            return
        }

        if (PetManager.currentPetCosmeticId(uuid) == desiredId) return

        LOGGER.info("reconcilePet: {} wants pet cosmetic {}, loading assets", uuid, desiredId)
        PolyPlusClient.SCOPE.launch {
            if (!CosmeticAssetCache.ensurePetLoaded(desiredId)) {
                LOGGER.warn("reconcilePet: failed to load pet cosmetic {} for {}", desiredId, uuid)
                return@launch
            }
            ClientPlatform.runOnMain {
                if (CosmeticCatalog.getRemoteEquipped(uuid)?.get(BodySlot.Pet) != desiredId) return@runOnMain
                PetManager.ensurePet(uuid, desiredId)
            }
        }
    }

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
        val added = ArrayList<String>()
        var dropped = 0
        for (player in players.mapNotNull(::normalizePlayerUuid)) {
            if (player in subscribedPlayers) continue
            if (subscribedPlayers.size >= MAX_PLAYER_SUBSCRIPTIONS) {
                dropped++
                continue
            }
            if (subscribedPlayers.add(player)) {
                added.add(player)
            }
        }
        if (dropped > 0 && !capWarned) {
            capWarned = true
            LOGGER.warn(
                "PolyPlus is at its subscription cap ({}); skipping {} further player(s).",
                MAX_PLAYER_SUBSCRIPTIONS,
                dropped,
            )
        }
        if (added.isEmpty()) {
            return Result.success(Unit)
        }

        for (chunk in added.chunked(MAX_PLAYERS_PER_REQUEST)) {
            val requestId = nextRequestId.incrementAndGet()
            pendingSubscriptions[requestId] = chunk
            val result = PolyConnection.sendPacket(ServerboundPacket.SubscribePlayers(chunk, requestId))
            if (result.isFailure) {
                pendingSubscriptions.remove(requestId)
                subscribedPlayers.removeAll(added.toSet())
                return result
            }
        }
        return Result.success(Unit)
    }

    private fun rollBackSubscriptions(players: Collection<String>) {
        if (players.isEmpty()) return
        subscribedPlayers.removeAll(players.toSet())
    }

    private fun handleError(packet: ClientboundPacket.Error) {
        val requestId = packet.requestId ?: return
        val players = pendingSubscriptions.remove(requestId) ?: return
        rollBackSubscriptions(players)
        LOGGER.warn(
            "PolyPlus subscription request {} was rejected ({}): rolled back {} player(s).",
            requestId,
            packet.code,
            players.size,
        )
    }

    private fun unsubscribePlayers(players: Iterable<String>): Result<Unit> {
        val removed = players
            .mapNotNull(::normalizePlayerUuid)
            .filter(subscribedPlayers::remove)
        if (removed.isEmpty()) {
            return Result.success(Unit)
        }
        for (uuidString in removed) {
            CosmeticCatalog.clearPolyPlusUser(UUID.fromString(uuidString))
        }
        return PolyConnection.sendPacket(ServerboundPacket.UnsubscribePlayers(removed))
    }

    private fun unsubscribeAllPlayers() {
        val players = subscribedPlayers.toList()
        subscribedPlayers.clear()
        pendingSubscriptions.clear()
        capWarned = false
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

    private fun UUID.isRealPlayer(): Boolean = version() == 4
}
