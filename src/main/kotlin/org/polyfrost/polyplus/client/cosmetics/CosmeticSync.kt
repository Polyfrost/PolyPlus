package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket
*///?}
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.pets.PetEntity
import org.polyfrost.polyplus.client.utils.Batcher
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.pets.PetManager
import org.polyfrost.polyplus.events.WebSocketMessage
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch

object CosmeticSync {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200)) { players ->
        subscribePlayers(players.toList())
    }
    private val subscribedPlayers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val pendingSubscriptions = ConcurrentHashMap<Long, List<String>>()
    private val nextRequestId = AtomicLong()

    private const val MAX_PLAYERS_PER_REQUEST = 64

    private const val MAX_PLAYER_SUBSCRIPTIONS = 512

    @Volatile
    private var capWarned = false

    //? if >= 1.21.1 || = 1.8.9 {
    private const val RECONCILE_INTERVAL_TICKS = 20
    private var reconcileTicks = 0

    private const val RESUBSCRIBE_INTERVAL_TICKS = 600
    private var resubscribeTicks = 0

    private val loading: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val pendingEmotes = ConcurrentHashMap<Int, Int>()

    // gives a closed cosmetics screen's last preview captures time to finish first
    private const val TRIM_DELAY_TICKS = 20
    private var trimCountdown = 0
    private var configUiWasOpen = false
    //?}

    fun earlyInitialize() {
        eventHandler<WorldEvent.Load> {
            PolyPlusClient.refreshCosmetics()
            refreshVisibleSubscriptions()
            Unit
        }

        //? if >= 1.21.1 || = 1.8.9 {
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
        }
        //?}

        //? if > 1.8.9 {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            unsubscribeAllPlayers()
            CosmeticCatalog.reset()
            //? if >= 1.21.1 {
            PolyPlusBadge.clearTabCache()
            CosmeticAssetCache.reset()
            PetManager.despawnAll()
            //?}
        }
        //?} else {
        /*eventHandler<TickEvent.End> {
            val connected = Minecraft.getInstance().networkHandler != null
            if (wasConnected && !connected) {
                unsubscribeAllPlayers()
                CosmeticCatalog.reset()
                PolyPlusBadge.clearTabCache()
                CosmeticAssetCache.reset()
                PetManager.despawnAll()
            }
            wasConnected = connected
            Unit
        }.register()
        *///?}

        //? if >= 1.21.1 {
        eventHandler<WorldEvent.Unload> { trimCountdown = TRIM_DELAY_TICKS }
        eventHandler<TickEvent.End> {
            // polled since ScreenOpenEvent misses screens changed through Gui#setScreen, which 26.2+ uses to close them
            val configUiOpen = ClientPlatform.currentScreen() is OneConfigUIScreen
            if (configUiWasOpen && !configUiOpen) trimCountdown = TRIM_DELAY_TICKS
            configUiWasOpen = configUiOpen
            if (trimCountdown > 0 && --trimCountdown == 0) trimAssets()
        }
        //?}

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
                //? if >= 1.21.1 || = 1.8.9 {
                is ClientboundPacket.PlayerEmoteStarted -> handleEmotePlay(packet.player, packet.emoteId)
                is ClientboundPacket.PlayerEmoteStopped -> handleEmoteStop(packet.player)
                is ClientboundPacket.EmotePlay -> handleEmotePlay(packet.player, packet.emoteId)
                is ClientboundPacket.EmoteStop -> handleEmoteStop(packet.player)
                //?}
                else -> Unit
            }
        }

        //? if > 1.8.9 {
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
        //?} else {
        /*eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? PlayerInfoS2CPacket ?: return@eventHandler
            if (packet.action == PlayerInfoS2CPacket.Action.ADD_PLAYER) {
                for (entry in packet.entries) {
                    val uuid = entry.profile.id
                    if (uuid.isRealPlayer()) BATCHER.add(uuid.toString())
                }
            }
        }

        eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? PlayerInfoS2CPacket ?: return@eventHandler
            if (packet.action != PlayerInfoS2CPacket.Action.REMOVE_PLAYER) return@eventHandler
            val removed = ArrayList<String>()
            for (uuid in packet.entries.map { it.profile.id }) {
        *///?}
                if (!uuid.isRealPlayer()) continue
                CosmeticCatalog.removeRemote(uuid)
                removed.add(uuid.toString())
                //? if >= 1.21.1 || = 1.8.9 {
                handleEmoteStop(uuid.toString())
                //?}
                //? if >= 1.21.1 || = 1.8.9 {
                PetManager.despawn(uuid)
                //?}
            }
            unsubscribePlayers(removed)
            Unit
        }

        //? if >= 1.21.1 || = 1.8.9 {
        // pets hold their level, so they must go before it's replaced
        // reconcileVisiblePlayers respawns them in the new one
        eventHandler<WorldEvent.Unload> { PetManager.despawnAll() }
        //?}
    }

    fun applyLocalActiveFromCatalog() {
        val active = CosmeticCatalog.localEquipped()
        val client = Minecraft.getInstance().player ?: return
        applyActiveToPlayer(client.uuid, active.ids())
    }

    fun refreshVisibleSubscriptions(): Result<Unit> {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        if (!mc.isSameThread) {
            mc.execute { refreshVisibleSubscriptions() }
            return Result.success(Unit)
        }
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
        //?} else {
        /*if (!mc.isOnSameThread) {
            mc.tell { refreshVisibleSubscriptions() }
            return Result.success(Unit)
        }
        val level = mc.world
            ?: return Result.failure(IllegalStateException("No world is loaded"))

        val visible = LinkedHashSet<String>()
        val self = mc.player
        val loaded = level.players.let { players ->
            if (self == null) players else players.sortedBy { it.squaredDistanceTo(self) }
        }
        for (player in loaded) {
            if (visible.size >= MAX_PLAYER_SUBSCRIPTIONS) break
            normalizePlayerUuid(player.uuid.toString())?.let(visible::add)
        }
        for (uuid in mc.networkHandler?.onlinePlayers?.map { it.profile.id } ?: emptyList()) {
        *///?}
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
        //? if >= 1.21.1 || = 1.8.9 {
        for ((uuidString, emoteId) in packet.activeEmotes) {
            handleEmotePlay(uuidString, emoteId)
        }
        //?}
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

    //? if >= 1.21.1 || = 1.8.9 {
    private fun handleEmotePlay(playerUuid: String, emoteId: Int) = playEmoteWhenLoaded(UUID.fromString(playerUuid), emoteId)

    private fun playEmoteWhenLoaded(uuid: UUID, emoteId: Int) {
        // keeps a trim from evicting the emote between its install and this playback
        pendingEmotes.merge(emoteId, 1, Int::plus)
        PolyPlusClient.SCOPE.launch {
            val loaded = CosmeticAssetCache.ensureEmoteLoaded(emoteId)
            ClientPlatform.runOnMain {
                pendingEmotes.computeIfPresent(emoteId) { _, count -> (count - 1).takeIf { it > 0 } }
                if (!loaded) return@runOnMain
                val emote = CosmeticAssetCache.getEmote(emoteId) ?: return@runOnMain
                val player = findPlayer(uuid) ?: return@runOnMain
                (player as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
            }
        }
    }

    private fun handleEmoteStop(playerUuid: String) {
        val uuid = UUID.fromString(playerUuid)
        ClientPlatform.runOnMain {
            val player = findPlayer(uuid) ?: return@runOnMain
            (player as PlayerEmotesAccess).`polyplus$emoteController`().stop()
        }
    }
    //?}

    private fun applyActiveToPlayer(uuid: UUID, cosmeticIds: List<Int>) = ClientPlatform.runOnMain {
        val player = findPlayer(uuid)
        if (player == null) {
            //? if >= 1.21.1 || = 1.8.9 {
            // a pet stays in the level without its owner, and reconcileVisiblePlayers only sees loaded owners
            if (CosmeticCatalog.getActiveId(uuid, BodySlot.Pet) == null) PetManager.despawn(uuid)
            //?}
            LOGGER.debug("Deferred cosmetic apply for {} ({} id(s)) — player not loaded", uuid, cosmeticIds.size)
            return@runOnMain
        }

        //? if >= 1.21.1 {
        reconcileAttachedCosmetics(player, uuid)
        reconcilePet(uuid)
        //?} elif = 1.8.9 {
        /*reconcileAttachedCosmetics(player, uuid)
        reconcilePet(uuid)
        *///?}

        // every other type is reconciled from the catalog above
        //? if >= 1.21.1 || = 1.8.9 {
        for (id in cosmeticIds) {
            if (CosmeticCatalog.getDefinition(id)?.type == CosmeticType.Emote) applyEmote(player, id)
        }
        //?}
    }

    //? if >= 1.21.1 || = 1.8.9 {
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

    // loads run in the background, one per cosmetic at a time, and the next reconcile applies whatever has loaded
    private fun reconcileAttachedCosmetics(player: AbstractClientPlayer, uuid: UUID) {
        val equipped = CosmeticCatalog.getRemoteEquipped(uuid).orEmpty()
        reconcileCape(equipped[BodySlot.Cape])
        for (slot in ATTACHED_SLOTS) {
            val desiredId = equipped[slot]
            val current = CosmeticApi.equippedSlot(player, slot)

            if (desiredId == null) {
                if (current != null) CosmeticApi.unequipSlot(player, slot)
                continue
            }

            // also picks up a new asset hash for one that is already loaded
            loadInBackground(desiredId)
            val attached = CosmeticAssetCache.getAttachedCosmetic(desiredId)?.copy(slot = slot) ?: continue
            if (current?.cosmetic == attached) continue
            CosmeticApi.unequipSlot(player, slot)
            CosmeticApi.equipLocal(player, attached)
        }
    }
    //?}

    //? if >= 1.21.1 {
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
    //?}

    //? if >= 1.21.1 || = 1.8.9 {
    private fun reconcileCape(cosmeticId: Int?) {
        if (cosmeticId == null || CosmeticAssetCache.isCapeLoaded(cosmeticId)) return
        loadInBackground(cosmeticId)
    }
    //?}

    //? if >= 1.21.1 || = 1.8.9 {
    private fun reconcilePet(uuid: UUID) {
        val equipped = CosmeticCatalog.getRemoteEquipped(uuid).orEmpty()
        val desiredId = equipped[BodySlot.Pet]

        if (desiredId == null) {
            PetManager.despawn(uuid)
            return
        }

        if (PetManager.currentPetCosmeticId(uuid) == desiredId) return

        loadInBackground(desiredId)
        // shoulder pets load as attached cosmetics and are equipped like one instead
        if (CosmeticAssetCache.getPetDefinition(desiredId) != null) PetManager.ensurePet(uuid, desiredId)
    }

    private fun loadInBackground(cosmeticId: Int) {
        if (!loading.add(cosmeticId)) return
        PolyPlusClient.SCOPE.launch {
            try {
                CosmeticAssetCache.ensureCosmeticLoaded(cosmeticId)
            } finally {
                loading.remove(cosmeticId)
            }
        }
    }
    //?}

    //? if >= 1.21.1 {
    private fun trimAssets() {
        // the cosmetics screen can preview anything in the catalog - closing it calls trim again
        if (ClientPlatform.currentScreen() is OneConfigUIScreen) return
        val keep = CosmeticCatalog.ownedIds() + CosmeticCatalog.localEquipped().ids() +
            CosmeticCatalog.remoteEquippedIds() + pendingEmotes.keys
        val inUse = HashSet<Identifier>()
        Minecraft.getInstance().level?.entitiesForRendering()?.forEach { entity ->
            when (entity) {
                is AbstractClientPlayer -> {
                    (entity as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped().mapTo(inUse) { it.cosmetic.texture }
                    (entity as PlayerEmotesAccess).`polyplus$emoteController`().playbackSnapshot()
                        ?.emote?.effects?.mapTo(inUse) { it.texture }
                }
                is PetEntity -> entity.definition?.let { inUse += it.texture }
            }
        }
        CosmeticAssetCache.trim(keep, inUse)
    }
    //?}

    //? if >= 1.21.1 || = 1.8.9 {
    private fun applyEmote(player: AbstractClientPlayer, cosmeticId: Int) = playEmoteWhenLoaded(player.uuid, cosmeticId)
    //?}

    //? if = 1.8.9 {
    /*private var wasConnected = false

    private fun reconcileVisiblePlayers() {
        val level = Minecraft.getInstance().world ?: return
        for (player in level.players) {
            val uuid = player.uuid
            if (!uuid.isRealPlayer()) continue
            if (CosmeticCatalog.getRemoteEquipped(uuid) == null) continue
            reconcileAttachedCosmetics(player as? AbstractClientPlayer ?: continue, uuid)
            reconcilePet(uuid)
        }
    }
    *///?}

    //? if > 1.8.9 {
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
    //?}

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
        //? if > 1.8.9 {
        val level = Minecraft.getInstance().level ?: return null
        return level.players().firstOrNull { it.uuid == uuid }
        //?} else {
        /*val level = Minecraft.getInstance().world ?: return null
        return level.players.firstOrNull { it.uuid == uuid } as? AbstractClientPlayer
        *///?}
    }

    private fun normalizePlayerUuid(uuidString: String): String? {
        val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: return null
        return uuid.takeIf { it.isRealPlayer() }?.toString()
    }

    private fun UUID.isRealPlayer(): Boolean = version() == 4
}
