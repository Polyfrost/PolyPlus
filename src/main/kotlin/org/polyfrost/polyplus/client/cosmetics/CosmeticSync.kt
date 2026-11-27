@file:OptIn(ExperimentalOneConfigUiApi::class)

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
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEquips
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.cosmetics.sets.BonusState
import org.polyfrost.polyplus.client.cosmetics.sets.DiscoveredBonuses
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.EmoteServerClock
import org.polyfrost.polyplus.client.emotes.endsDevPlayback
import org.polyfrost.polyplus.client.emotes.snapshotActive
import org.polyfrost.polyplus.client.emotes.snapshotSessions
import org.polyfrost.polyplus.client.emotes.snapshotSkips
import org.polyfrost.polyplus.client.emotes.duo.DuoEmoteState
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.Batcher
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.pets.PetEquips
import org.polyfrost.polyplus.client.pets.PetInteractions
import org.polyfrost.polyplus.client.pets.PetManager
import org.polyfrost.polyplus.client.pets.PetPlay
import org.polyfrost.polyplus.events.WebSocketMessage
import java.time.Duration
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

object CosmeticSync {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200)) { players ->
        subscribeVisible(players.toList())
    }
    private val subscribedPlayers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val watchedFriends: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val visiblePlayers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private class PendingSubscription(val players: List<String>, val sentAtMs: Long)

    private val pendingSubscriptions = ConcurrentHashMap<Long, PendingSubscription>()
    private val nextRequestId = AtomicLong()

    private const val MAX_PLAYERS_PER_REQUEST = 64

    private const val MAX_PLAYER_SUBSCRIPTIONS = 512

    @Volatile
    private var capWarned = false

    private const val LOCAL_RESYNC_DELAY_MS = 500L
    private val localResyncPending = AtomicBoolean(false)

    //? if >= 1.21.1 || = 1.8.9 {
    private const val RECONCILE_INTERVAL_TICKS = 20
    private var reconcileTicks = 0

    private const val RESUBSCRIBE_INTERVAL_TICKS = 600
    private var resubscribeTicks = 0

    private val loading: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val pendingEmotes = ConcurrentHashMap<Int, Int>()
    private val pendingRevisions = ConcurrentHashMap<EmoteRevision, Int>()

    private class RemoteEmote(
        val emoteId: Int,
        val startedAtMs: Long,
        val token: Long,
        val serverStart: Long?,
        val revision: EmoteRevision?,
        val dev: Boolean = false,
    ) {
        val receivedAtMs: Long = System.currentTimeMillis()
    }

    private class AppliedEmote(val token: Long, var offsetMs: Long)

    private data class PlayPin(val player: UUID)

    private const val CLOCK_WAIT_MS = 1500L
    private const val RESYNC_OFFSET_MS = 100L

    private val remoteEmotes = ConcurrentHashMap<UUID, RemoteEmote>()
    private val emoteTokens = AtomicLong()
    private val appliedEmotes = WeakHashMap<AbstractClientPlayer, AppliedEmote>()
    private val emoteLoads: MutableSet<Any> = ConcurrentHashMap.newKeySet()
    private val partnerWatchers = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // gives a closed cosmetics screen's last preview captures time to finish first
    private const val TRIM_DELAY_TICKS = 20
    private const val DEPARTURE_TRIM_DELAY_TICKS = 100
    private val trimTimer = AssetTrimTimer(TRIM_DELAY_TICKS, DEPARTURE_TRIM_DELAY_TICKS)
    private var configUiWasOpen = false

    @Volatile
    private var assetResetPending = false
    //?}

    fun earlyInitialize() {
        eventHandler<WorldEvent.Load> {
            if (!LobbyScene.building) {
                PolyPlusClient.refreshCosmetics()
                refreshVisibleSubscriptions()
                MojangSkinService.refreshLocalOverride()
            }
            Unit
        }

        //? if >= 1.21.1 || = 1.8.9 {
        eventHandler<TickEvent.End> {
            if (++reconcileTicks >= RECONCILE_INTERVAL_TICKS) {
                reconcileTicks = 0
                reconcileVisiblePlayers()
                CosmeticAssetCache.releaseRetired(wornCosmetics())
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
            NameplateWearers.forgetAll()
            CosmeticCatalog.reset()
            //? if >= 1.21.1 {
            PolyPlusBadge.clearTabCache()
            // this can run on the Netty thread, where waiting on the main thread hangs forever once Minecraft#disconnect drops the task
            assetResetPending = true
            PetManager.despawnAll()
            //?}
        }
        //?} else {
        /*eventHandler<TickEvent.End> {
            val connected = Minecraft.getInstance().networkHandler != null
            if (wasConnected && !connected) {
                unsubscribeAllPlayers()
                NameplateWearers.forgetAll()
                CosmeticCatalog.reset()
                PolyPlusBadge.clearTabCache()
                CosmeticAssetCache.reset()
                PetManager.despawnAll()
                PolyPlusClient.refreshCosmetics()
            }
            wasConnected = connected
            Unit
        }.register()
        *///?}

        //? if >= 1.21.1 || = 1.8.9 {
        eventHandler<WorldEvent.Unload> { trimTimer.settle() }
        eventHandler<TickEvent.End> {
            //? if >= 1.21.1 {
            if (assetResetPending) {
                assetResetPending = false
                CosmeticAssetCache.reset()
                PolyPlusClient.refreshCosmetics()
            }
            //?}
            // polled since ScreenOpenEvent misses screens changed through Gui#setScreen, which 26.2+ uses to close them
            val configUiOpen = previewUiOpen()
            if (configUiWasOpen && !configUiOpen) trimTimer.settle()
            configUiWasOpen = configUiOpen
            if (trimTimer.tick()) trimAssets()
        }
        //?}

        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.CosmeticsInfo -> handleCosmeticsInfo(packet)
                is ClientboundPacket.SubscriptionSnapshot -> handleSubscriptionSnapshot(packet)
                is ClientboundPacket.PlayerCosmeticEquipped -> handlePlayerCosmeticEquipped(packet)
                is ClientboundPacket.PlayerParticleColorChanged -> handleParticleColorChanged(packet)
                is ClientboundPacket.PlayerColorsChanged -> CosmeticColors.handle(packet)
                is ClientboundPacket.CosmeticColorsSaved -> CosmeticColors.handle(packet)
                is ClientboundPacket.ParticleColorSaved -> CosmeticColors.handle(packet)
                is ClientboundPacket.PlayerBonusesChanged -> if (BonusState.handle(packet)) assetsDeparted()
                is ClientboundPacket.PlayerPetPreferencesChanged ->
                    PetInteractions.setPetPlay(UUID.fromString(packet.player), packet.play, parseUuids(packet.friends))
                is ClientboundPacket.BonusDiscovered -> DiscoveredBonuses.handle(packet)
                is ClientboundPacket.SharedEmoteRequested -> DuoEmoteState.handle(packet)
                is ClientboundPacket.SharedEmoteRequestSent -> DuoEmoteState.handle(packet)
                is ClientboundPacket.SharedEmoteResolved -> DuoEmoteState.handle(packet)
                is ClientboundPacket.SharedEmoteStart -> handleSharedEmoteStart(packet)
                is ClientboundPacket.SharedEmoteStop -> handleSharedEmoteStop(packet)
                is ClientboundPacket.PlayerPresence -> {
                    LOGGER.info("PolyPlus presence: {} is now {}", packet.player, if (packet.online) "online" else "offline")
                    CosmeticCatalog.setPolyPlusUser(UUID.fromString(packet.player), packet.online)
                }
                is ClientboundPacket.OwnershipUpdated -> handleOwnershipUpdated(packet)
                is ClientboundPacket.CatalogUpdated -> CosmeticCatalog.onCatalogUpdated()
                is ClientboundPacket.Error -> handleError(packet)
                //? if >= 1.21.1 || = 1.8.9 {
                is ClientboundPacket.PlayerEmoteStarted -> if (
                    !EmoteApi.skipsEcho(packet.player, packet.emoteId, packet.startTime, packet.requestId) && !ignoresCancelledStart(packet)
                ) {
                    DuoEmoteState.handleSoloStart(packet)
                    handleEmotePlay(packet.player, packet.emoteId, packet.startTime, packet.emote)
                }
                is ClientboundPacket.PlayerEmoteStopped -> if (!playsLocalDevEmote(packet.player)) {
                    DuoEmoteState.handleSoloStop(packet.player)
                    handleEmoteStop(packet.player)
                }
                is ClientboundPacket.EmotePlay -> handleEmotePlay(packet.player, packet.emoteId, packet.startTime)
                is ClientboundPacket.EmoteStop -> if (!playsLocalDevEmote(packet.player)) handleEmoteStop(packet.player)
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
                CosmeticColors.forget(uuid)
                BonusState.forget(uuid)
                DuoEmoteState.forget(uuid)
                NameplateWearers.forget(uuid)
                PetInteractions.forgetPetPlay(listOf(uuid))
                removed.add(uuid.toString())
                //? if >= 1.21.1 || = 1.8.9 {
                handleEmoteStop(uuid.toString())
                //?}
                //? if >= 1.21.1 || = 1.8.9 {
                PetManager.despawn(uuid)
                //?}
            }
            visiblePlayers.removeAll(removed.toSet())
            unsubscribePlayers(removed)
            if (removed.isNotEmpty()) assetsDeparted()
            Unit
        }

        //? if >= 1.21.1 || = 1.8.9 {
        // pets hold their level, so they must go before it's replaced
        // reconcileVisiblePlayers respawns them in the new one
        eventHandler<WorldEvent.Unload> { PetManager.despawnAll() }
        //?}
    }

    fun equipmentKnown(uuid: UUID): Boolean {
        val player = CosmeticCatalog.accountId(uuid).toString()
        return player in subscribedPlayers && pendingSubscriptions.values.none { player in it.players }
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
        val level = mc.level ?: run {
            subscribeLocalAccount()
            return Result.failure(IllegalStateException("No world is loaded"))
        }

        val visible = LinkedHashSet<String>()
        normalizePlayerUuid(ClientPlatform.localPlayerUuid().toString())?.let(visible::add)
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
        val level = mc.world ?: run {
            subscribeLocalAccount()
            return Result.failure(IllegalStateException("No world is loaded"))
        }

        val visible = LinkedHashSet<String>()
        normalizePlayerUuid(ClientPlatform.localPlayerUuid().toString())?.let(visible::add)
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

        visiblePlayers.retainAll(visible)
        visiblePlayers.addAll(visible)
        val friends = watchedFriends
            .filter { it !in visible }
            .sortedBy { it !in subscribedPlayers }
            .take((MAX_PLAYER_SUBSCRIPTIONS - visible.size).coerceAtLeast(0))
        val wanted = visible + friends
        unsubscribePlayers(subscribedPlayers.filter { it !in wanted }, rewatchFriends = false)
        return subscribePlayers(wanted)
    }

    internal fun nextRequestId(): Long = nextRequestId.incrementAndGet()

    internal fun subscribeLocalAccount(): Result<Unit> =
        subscribePlayers(listOfNotNull(normalizePlayerUuid(ClientPlatform.localPlayerUuid().toString())))

    fun subscribeFriends(players: Collection<String>): Result<Unit> = subscribePlayers(players)

    fun watchFriends(players: Collection<String>): Result<Unit> {
        val friends = players.mapNotNull(::normalizePlayerUuid).toSet()
        val dropped = watchedFriends.filter { it !in friends }
        watchedFriends.retainAll(friends)
        watchedFriends.addAll(friends)
        if (LobbyScene.hasRealPlayer() && !LobbyScene.terrainActive()) return refreshVisibleSubscriptions()
        if (dropped.isNotEmpty()) unsubscribePlayers(dropped)
        return subscribePlayers(friends)
    }

    private fun subscribeVisible(players: Collection<String>): Result<Unit> {
        val normalized = players.mapNotNull(::normalizePlayerUuid)
        visiblePlayers.addAll(normalized)
        val incoming = normalized.filter { it !in subscribedPlayers }
        val overflow = subscribedPlayers.size + incoming.size - MAX_PLAYER_SUBSCRIPTIONS
        if (overflow > 0) {
            unsubscribePlayers(subscribedPlayers.filter { it !in visiblePlayers }.take(overflow), rewatchFriends = false)
        }
        return subscribePlayers(incoming)
    }

    fun syncPetPreferences(play: PetPlay = PolyPlusCosmeticsConfig.petPlay): Result<Unit> =
        PolyConnection.sendPacket(ServerboundPacket.SetPetPreferences(play))

    fun resubscribeVisiblePlayers(): Result<Unit> {
        subscribedPlayers.clear()
        pendingSubscriptions.clear()
        PetInteractions.clearPetPlay()
        EmoteApi.forgetUnanswered()
        DuoEmoteState.forgetUnanswered()
        capWarned = false
        subscribeLocalAccount()
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
        val pending = packet.requestId?.let(pendingSubscriptions::remove)
        if (pending != null && packet.serverTime != null) EmoteServerClock.sample(packet.serverTime, pending.sentAtMs, packet.serverReceivedTime)
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
        CosmeticColors.applySnapshot(
            packet.equipped.keys,
            packet.colors,
            packet.savedColors,
            packet.savedColorsActive,
            pending?.players,
            packet.rejected,
            packet.equipped,
        )
        BonusState.applySnapshot(packet.equipped.keys, packet.bonuses)
        PetInteractions.applyPetPlaySnapshot(
            parseUuids(packet.equipped.keys),
            parseUuids(packet.petInteractionsOff),
            packet.petInteractionsFriends.entries
                .mapNotNull { (player, friends) -> parseUuid(player)?.let { it to parseUuids(friends) } }
                .toMap(),
        )
        val devPlayer = localDevPlayer()
        val sharedEmotes = snapshotSessions(packet.sharedEmotes, devPlayer)
        val activeEmotes = snapshotActive(packet.activeEmotes, packet.activeEmoteStarts, packet.sharedEmotes, devPlayer)
        DuoEmoteState.applySnapshot(
            packet.equipped.keys.filterNot { snapshotSkips(it, devPlayer) },
            activeEmotes,
            packet.activeEmoteStarts,
            packet.activeEmoteAnchors,
            sharedEmotes,
            packet::emoteDefinitionFor,
        )
        //? if >= 1.21.1 || = 1.8.9 {
        for (uuidString in packet.equipped.keys - activeEmotes.keys) {
            if (snapshotSkips(uuidString, devPlayer)) continue
            if (remoteEmotes.containsKey(UUID.fromString(uuidString))) handleEmoteStop(uuidString)
        }
        for ((uuidString, emoteId) in activeEmotes) {
            if (snapshotSkips(uuidString, devPlayer)) continue
            handleEmotePlay(uuidString, emoteId, packet.activeEmoteStarts[uuidString], packet.emoteDefinitionFor(listOf(uuidString), emoteId))
        }
        for (session in sharedEmotes.values.toSet()) {
            for (player in session.players) {
                if (player in activeEmotes || snapshotSkips(player, devPlayer) || runCatching { UUID.fromString(player) }.isFailure) continue
                notePartner(player, session.players)
                handleEmotePlay(player, session.emoteId, session.startTime, packet.emoteDefinitionFor(listOf(player) + session.players, session.emoteId))
            }
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
        CosmeticEquips.changed(uuid, packet.slot, packet.cosmeticId)
        if (packet.slot == BodySlot.Pet) PetEquips.changed(uuid, packet.cosmeticId)
        if (packet.slot == BodySlot.Aura && uuid == ClientPlatform.localPlayerUuid()) CosmeticColors.auraEquipObserved(packet.cosmeticId)
        CosmeticCatalog.applyRemoteEquippedSlot(uuid, packet.slot, packet.cosmeticId)
        applyActiveToPlayer(uuid, packet.cosmeticId?.let(::listOf).orEmpty())
        if (uuid == ClientPlatform.localPlayerUuid() &&
            packet.slot != BodySlot.Unknown &&
            CosmeticCatalog.localEquipped().equipped[packet.slot] != packet.cosmeticId &&
            localResyncPending.compareAndSet(false, true)
        ) {
            PolyPlusClient.SCOPE.launch {
                delay(LOCAL_RESYNC_DELAY_MS)
                localResyncPending.set(false)
                CosmeticService.syncLocalActive()
            }
        }
    }

    private fun handleParticleColorChanged(packet: ClientboundPacket.PlayerParticleColorChanged) {
        val uuid = UUID.fromString(packet.player)
        if (packet.color != null) {
            CosmeticCatalog.setParticleColor(uuid, packet.color)
        } else {
            CosmeticCatalog.clearParticleColor(uuid)
        }
        if (uuid == ClientPlatform.localPlayerUuid()) CosmeticColors.handleParticleEcho(packet.color)
    }

    private fun handleSharedEmoteStart(packet: ClientboundPacket.SharedEmoteStart) {
        val skipped = cancelledStartPlayer(packet)
        //? if >= 1.21.1 || = 1.8.9 {
        for (player in packet.players) {
            val uuid = runCatching { UUID.fromString(player) }.getOrNull() ?: continue
            if (uuid == skipped) continue
            CosmeticCatalog.pinEmote(PlayPin(uuid), packet.emoteId, packet.emote)
        }
        //?}
        DuoEmoteState.handle(packet, skipped)
        //? if >= 1.21.1 || = 1.8.9 {
        for (player in packet.players) {
            val uuid = runCatching { UUID.fromString(player) }.getOrNull() ?: continue
            if (uuid == skipped) continue
            notePartner(player, packet.players)
            handleEmotePlay(player, packet.emoteId, packet.startTime, packet.emote)
        }
        //?}
    }

    internal fun endSharedLocally(players: List<String>) = handleSharedEmoteStop(ClientboundPacket.SharedEmoteStop(players))

    private fun handleSharedEmoteStop(packet: ClientboundPacket.SharedEmoteStop) {
        val session = packet.players.firstNotNullOfOrNull { player ->
            runCatching { UUID.fromString(player) }.getOrNull()?.let(DuoEmoteState::sharedFor)
        }
        DuoEmoteState.handle(packet)
        //? if >= 1.21.1 || = 1.8.9 {
        if (session == null) return
        for (player in packet.players) {
            val uuid = runCatching { UUID.fromString(player) }.getOrNull() ?: continue
            val active = remoteEmotes[uuid] ?: continue
            if (active.emoteId == session.emoteId && active.startedAtMs == session.startTime) handleEmoteStop(player)
        }
        //?}
    }

    private fun localDevPlayer(): UUID? {
        val local = ClientPlatform.localPlayerUuid()
        return local.takeIf { remoteEmotes[it]?.dev == true }
    }

    private fun cancelledStartPlayer(packet: ClientboundPacket.SharedEmoteStart): UUID? {
        val local = localDevPlayer() ?: return null
        val players = packet.players.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (local !in players) return null
        return local.takeIf { DuoEmoteState.cancelledStart(packet.emoteId, players.firstOrNull { it != local }) }
    }

    private fun ignoresCancelledStart(packet: ClientboundPacket.PlayerEmoteStarted): Boolean {
        if (packet.requestId != null) return false
        val local = localDevPlayer() ?: return false
        if (runCatching { UUID.fromString(packet.player) }.getOrNull() != local) return false
        return DuoEmoteState.cancelledStart(packet.emoteId, null)
    }

    private fun handleOwnershipUpdated(packet: ClientboundPacket.OwnershipUpdated) {
        if (UUID.fromString(packet.player) != ClientPlatform.localPlayerUuid()) return
        if (packet.cosmeticIds.isNotEmpty() || packet.emoteIds.isNotEmpty()) {
            PolyPlusClient.refreshCosmetics()
        }
    }

    //? if >= 1.21.1 || = 1.8.9 {
    private fun handleEmotePlay(
        playerUuid: String,
        emoteId: Int,
        startedAtMs: Long? = null,
        emote: EmoteDefinition? = null,
        dev: Boolean = false,
    ) {
        val uuid = UUID.fromString(playerUuid)
        val revision = CosmeticCatalog.pinEmote(PlayPin(uuid), emoteId, emote)
        val known = remoteEmotes[uuid]
        if (startedAtMs == null || known == null || known.emoteId != emoteId || known.startedAtMs != startedAtMs ||
            known.revision != revision || known.dev != dev
        ) {
            val startedAt = startedAtMs ?: EmoteServerClock.now()
            remoteEmotes[uuid] = RemoteEmote(emoteId, startedAt, emoteTokens.incrementAndGet(), startedAtMs, revision, dev)
        }
        whenEmoteLoaded(emoteId, revision) { emoteTargets(uuid).forEach(::syncEmote) }
    }

    private fun whenEmoteLoaded(emoteId: Int, revision: EmoteRevision? = null, block: () -> Unit) {
        // keeps a trim from evicting the emote between its install and this playback
        pendingEmotes.merge(emoteId, 1, Int::plus)
        revision?.let { pendingRevisions.merge(it, 1, Int::plus) }
        PolyPlusClient.SCOPE.launch {
            val loaded = loadEmote(emoteId, revision)
            ClientPlatform.runOnMain {
                pendingEmotes.computeIfPresent(emoteId) { _, count -> (count - 1).takeIf { it > 0 } }
                revision?.let { pendingRevisions.computeIfPresent(it) { _, count -> (count - 1).takeIf { left -> left > 0 } } }
                if (loaded) block()
            }
        }
    }

    private suspend fun loadEmote(emoteId: Int, revision: EmoteRevision?): Boolean =
        if (revision != null && CosmeticCatalog.getEmoteDefinition(revision) != null) {
            CosmeticAssetCache.ensureEmoteLoaded(revision)
        } else {
            CosmeticAssetCache.ensureEmoteLoaded(emoteId)
        }

    private fun playableRevision(active: RemoteEmote): EmoteRevision? =
        active.revision?.takeIf { CosmeticAssetCache.getEmote(it) != null || CosmeticCatalog.getEmoteDefinition(it) != null }

    fun syncEmote(player: AbstractClientPlayer) {
        val active = remoteEmotes[emoteKey(player)] ?: return
        val applied = appliedEmotes[player]
        if (applied != null && applied.token == active.token) {
            val serverStart = active.serverStart ?: return
            val offset = EmoteServerClock.offset()
            if (abs(offset - applied.offsetMs) < RESYNC_OFFSET_MS) return
            applied.offsetMs = offset
            (player as PlayerEmotesAccess).`polyplus$emoteController`().retime(serverStart, EmoteServerClock.elapsedSince(active.startedAtMs))
            return
        }
        if (active.serverStart != null && !EmoteServerClock.synced &&
            System.currentTimeMillis() - active.receivedAtMs < CLOCK_WAIT_MS
        ) {
            return
        }
        val revision = playableRevision(active)
        val emote = if (revision != null) CosmeticAssetCache.getEmote(revision) else CosmeticAssetCache.getEmote(active.emoteId)
        if (emote == null || (revision == null && !CosmeticAssetCache.isEmoteCurrent(active.emoteId))) {
            loadEmoteInBackground(active.emoteId, revision)
            return
        }
        appliedEmotes[player] = AppliedEmote(active.token, EmoteServerClock.offset())
        val elapsedMs = EmoteServerClock.elapsedSince(active.startedAtMs)
        (player as PlayerEmotesAccess).`polyplus$emoteController`()
            .play(emote, elapsedMs, active.emoteId, startTime = active.serverStart, dev = active.dev)
        val serverStart = active.serverStart ?: return
        if (emoteKey(player) == ClientPlatform.localPlayerUuid()) EmoteApi.onLocalApplied(active.emoteId, serverStart)
    }

    internal fun dropLocalEmote(emoteId: Int, startTime: Long) {
        val local = ClientPlatform.localPlayerUuid()
        val active = remoteEmotes[local] ?: return
        if (active.emoteId != emoteId || active.serverStart != startTime) return
        handleEmoteStop(local.toString())
    }

    internal fun playLocalDevEmote(emoteId: Int, anchor: EmoteAnchor?) {
        val player = ClientPlatform.localPlayerUuid().toString()
        DuoEmoteState.handleSoloStart(ClientboundPacket.PlayerEmoteStarted(player, emoteId, anchor = anchor))
        handleEmotePlay(player, emoteId, dev = true)
    }

    internal fun dropLocalDevEmote(emoteId: Int? = null) {
        val local = ClientPlatform.localPlayerUuid()
        val active = remoteEmotes[local] ?: return
        if (!endsDevPlayback(active.emoteId, active.dev, emoteId)) return
        DuoEmoteState.dropSolo(local, active.emoteId)
        handleEmoteStop(local.toString())
    }

    private fun playsLocalDevEmote(playerUuid: String): Boolean =
        snapshotSkips(playerUuid, localDevPlayer())

    private fun emoteKey(player: AbstractClientPlayer): UUID =
        if (player === Minecraft.getInstance().player) ClientPlatform.localPlayerUuid() else player.uuid

    private fun loadEmoteInBackground(emoteId: Int, revision: EmoteRevision? = null) {
        val key: Any = revision ?: emoteId
        if (!emoteLoads.add(key)) return
        PolyPlusClient.SCOPE.launch {
            try {
                loadEmote(emoteId, revision)
            } finally {
                emoteLoads.remove(key)
            }
        }
    }

    private fun emoteTargets(uuid: UUID): List<AbstractClientPlayer> =
        listOfNotNull(findPlayer(uuid), LobbyScene.emoteAvatar(uuid)).distinct()

    private fun handleEmoteStop(playerUuid: String) {
        val uuid = UUID.fromString(playerUuid)
        partnerWatchers.remove(uuid)
        remoteEmotes.remove(uuid)
        CosmeticCatalog.releaseEmotePin(PlayPin(uuid))
        ClientPlatform.runOnMain { stopEmoteOn(uuid) }
    }

    private fun stopEmoteOn(uuid: UUID) {
        for (player in emoteTargets(uuid)) {
            appliedEmotes.remove(player)
            val controller = (player as PlayerEmotesAccess).`polyplus$emoteController`()
            if (controller.networkedEmoteId != null) controller.stop(notifyServer = false)
        }
    }

    private fun forgetEmotes(players: Collection<String>) {
        val gone = HashSet<UUID>()
        for (uuidString in players) {
            val uuid = UUID.fromString(uuidString)
            gone += uuid
            partnerWatchers.remove(uuid)
            remoteEmotes.remove(uuid)
            CosmeticCatalog.releaseEmotePin(PlayPin(uuid))
        }
        val orphaned = ArrayList<UUID>()
        for ((partner, watchers) in partnerWatchers) {
            watchers.removeAll(gone)
            if (watchers.isEmpty()) orphaned += partner
        }
        for (partner in orphaned) {
            partnerWatchers.remove(partner)
            if (partner.toString() !in subscribedPlayers) handleEmoteStop(partner.toString())
        }
    }

    private fun notePartner(player: String, session: List<String>) {
        val uuid = UUID.fromString(player)
        if (uuid.toString() in subscribedPlayers || uuid == ClientPlatform.localPlayerUuid()) {
            partnerWatchers.remove(uuid)
            return
        }
        val watched = session
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .filter { it != uuid && it.toString() in subscribedPlayers }
        if (watched.isEmpty()) return
        partnerWatchers.computeIfAbsent(uuid) { ConcurrentHashMap.newKeySet() }.addAll(watched)
    }
    //?}

    private fun applyActiveToPlayer(uuid: UUID, cosmeticIds: List<Int>) = ClientPlatform.runOnMain {
        val player = findPlayer(uuid)
        if (player == null) {
            //? if >= 1.21.1 || = 1.8.9 {
            // a pet stays in the level without its owner, and reconcileVisiblePlayers only sees loaded owners
            if (CosmeticCatalog.getActiveId(uuid, BodySlot.Pet) == null) PetManager.despawn(uuid)
            //?}
            reconcileNameplate(uuid, CosmeticCatalog.getActiveId(uuid, BodySlot.Nameplate))
            LOGGER.debug("Deferred cosmetic apply for {} ({} id(s)) — player not loaded", uuid, cosmeticIds.size)
            return@runOnMain
        }

        //? if >= 1.21.1 {
        reconcileAttachedCosmetics(player, player.uuid)
        reconcilePet(player.uuid)
        //?} elif = 1.8.9 {
        /*reconcileAttachedCosmetics(player, player.uuid)
        reconcilePet(player.uuid)
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
        BodySlot.Tail,
        BodySlot.Glasses,
        BodySlot.Wings,
        BodySlot.LeftHand,
        BodySlot.RightHand,
        BodySlot.Hat,
        BodySlot.Aura,
        BodySlot.Boots,
        BodySlot.Shoulder,
        BodySlot.Pet,
        BodySlot.EffectA,
        BodySlot.EffectB,
    )

    // loads run in the background, one per cosmetic at a time, and the next reconcile applies whatever has loaded
    private fun reconcileAttachedCosmetics(player: AbstractClientPlayer, uuid: UUID) {
        val equipped = CosmeticCatalog.getRemoteEquipped(uuid).orEmpty()
        reconcileCape(player, equipped[BodySlot.Cape])
        reconcileNameplate(uuid, equipped[BodySlot.Nameplate])
        for (slot in ATTACHED_SLOTS) {
            val desiredId = equipped[slot]
            val current = CosmeticApi.equippedSlot(player, slot)

            if (desiredId == null) {
                if (current != null) CosmeticApi.unequipSlot(player, slot)
                continue
            }

            CosmeticCatalog.requestRefreshForUnknown(desiredId)
            // also picks up a new asset hash for one that is already loaded
            loadInBackground(desiredId)
            val attached = CosmeticAssetCache.getAttachedCosmetic(desiredId)?.copy(slot = slot)
            if (attached == null) {
                if (current != null && current.cosmetic.cosmeticId != desiredId) CosmeticApi.unequipSlot(player, slot)
                continue
            }
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
            if (!uuid.isRealPlayer() && player !== Minecraft.getInstance().player && !DevCrowd.isMember(uuid)) continue
            syncEmote(player)
            if (CosmeticCatalog.getRemoteEquipped(uuid) == null) continue
            reconcileAttachedCosmetics(player, uuid)
            reconcilePet(uuid)
        }
    }
    //?}

    private fun reconcileNameplate(uuid: UUID, cosmeticId: Int?) {
        if (cosmeticId == null) {
            NameplateWearers.forget(uuid)
            return
        }
        val definition = CosmeticCatalog.getCosmeticDefinition(cosmeticId)
        if (definition == null) {
            NameplateWearers.bind(uuid, cosmeticId)
            CosmeticCatalog.requestRefreshForUnknown(cosmeticId)
            return
        }
        if (definition.type != CosmeticType.Nameplate) {
            NameplateWearers.forget(uuid)
            return
        }
        NameplateWearers.bind(uuid, cosmeticId)
        //? if >= 1.21.1 || = 1.8.9 {
        if (!CosmeticAssetCache.isCurrent(cosmeticId)) loadInBackground(cosmeticId)
        //?}
    }

    //? if >= 1.21.1 || = 1.8.9 {
    private fun reconcileCape(player: AbstractClientPlayer, cosmeticId: Int?) {
        val current = CosmeticApi.equippedSlot(player, BodySlot.Cape)
        val attached = cosmeticId?.let(CosmeticAssetCache::getAttachedCosmetic)?.takeIf { it.slot == BodySlot.Cape }
        if (attached == null) {
            if (current != null) CosmeticApi.unequipSlot(player, BodySlot.Cape)
        } else if (current?.cosmetic != attached) {
            CosmeticApi.unequipSlot(player, BodySlot.Cape)
            CosmeticApi.equipLocal(player, attached)
        }
        if (cosmeticId == null || CosmeticAssetCache.isCurrent(cosmeticId)) return
        CosmeticCatalog.requestRefreshForUnknown(cosmeticId)
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

        CosmeticCatalog.requestRefreshForUnknown(desiredId)
        loadInBackground(desiredId)
        // shoulder pets load as attached cosmetics and are equipped like one instead
        PetManager.ensurePet(uuid, desiredId)
    }

    private fun wornCosmetics(): List<AttachedCosmetic> {
        //? if > 1.8.9 {
        val players = Minecraft.getInstance().level?.players().orEmpty()
        //?} else {
        /*val players = Minecraft.getInstance().world?.players.orEmpty()
        *///?}
        val worn = ArrayList<AttachedCosmetic>()
        for (player in players) {
            val access = player as? PlayerCosmeticsAccess ?: continue
            access.`polyplus$cosmeticEquipment`().equipped().mapTo(worn) { it.cosmetic }
        }
        worn += LobbyScene.wornCosmetics()
        worn += PlayerPreviewRenderer.heldCosmetics()
        return worn
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

    internal fun assetsDeparted() {
        //? if >= 1.21.1 || = 1.8.9 {
        trimTimer.departed()
        //?}
    }

    //? if >= 1.21.1 || = 1.8.9 {
    private fun previewUiOpen(): Boolean = ClientPlatform.currentScreen().let { OneConfigUI.isOneConfigScreen(it) || MainMenuReplacement.isMenu(it) }

    private fun trimAssets() {
        // the cosmetics screen can preview anything in the catalog - closing it calls trim again
        if (previewUiOpen()) return
        val keep = CosmeticCatalog.ownedIds() + CosmeticCatalog.localEquipped().ids() +
            CosmeticCatalog.remoteEquippedIds() + pendingEmotes.keys + remoteEmotes.values.map { it.emoteId }
        val inUse = HashSet<Identifier>()
        //? if > 1.8.9 {
        Minecraft.getInstance().level?.entitiesForRendering()?.forEach { entity ->
            when (entity) {
                is AbstractClientPlayer -> {
                    (entity as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped().mapTo(inUse) { it.cosmetic.texture }
                }
            }
        }
        //?} else {
        /*Minecraft.getInstance().world?.players?.forEach { player ->
            if (player is AbstractClientPlayer) {
                (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped().mapTo(inUse) { it.cosmetic.texture }
            }
        }
        *///?}
        inUse += LobbyScene.wornTextures()
        val keepEmotes = remoteEmotes.values.mapNotNullTo(HashSet()) { it.revision }
        keepEmotes += pendingRevisions.keys
        CosmeticAssetCache.trim(keep, inUse, BonusState.activeRefs() + PreviewTryOn.previewBonuses(), keepEmotes)

    }
    //?}

    //? if >= 1.21.1 || = 1.8.9 {
    private fun applyEmote(player: AbstractClientPlayer, cosmeticId: Int) = whenEmoteLoaded(cosmeticId) {
        val emote = CosmeticAssetCache.getEmote(cosmeticId) ?: return@whenEmoteLoaded
        val target = findPlayer(player.uuid) ?: return@whenEmoteLoaded
        (target as PlayerEmotesAccess).`polyplus$emoteController`().play(emote, 0L, cosmeticId)
    }
    //?}

    //? if = 1.8.9 {
    /*private var wasConnected = false

    private fun reconcileVisiblePlayers() {
        val level = Minecraft.getInstance().world ?: return
        for (player in level.players) {
            val uuid = player.uuid
            if (!uuid.isRealPlayer() && player !== Minecraft.getInstance().player && !DevCrowd.isMember(uuid)) continue
            (player as? AbstractClientPlayer)?.let(::syncEmote)
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
            pendingSubscriptions[requestId] = PendingSubscription(chunk, System.currentTimeMillis())
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
        CosmeticColors.handleError(packet)
        EmoteApi.onPlayFailed(packet.requestId)
        ClientPlatform.runOnMain { DuoEmotes.onError(packet.code, packet.message, packet.requestId) }
        val requestId = packet.requestId ?: return
        val players = pendingSubscriptions.remove(requestId)?.players ?: return
        rollBackSubscriptions(players)
        LOGGER.warn(
            "PolyPlus subscription request {} was rejected ({}): rolled back {} player(s).",
            requestId,
            packet.code,
            players.size,
        )
    }

    private fun unsubscribePlayers(players: Iterable<String>, rewatchFriends: Boolean = true): Result<Unit> {
        val removed = players
            .mapNotNull(::normalizePlayerUuid)
            .filter(subscribedPlayers::remove)
        if (removed.isEmpty()) {
            return Result.success(Unit)
        }
        for (uuidString in removed) {
            CosmeticCatalog.clearPolyPlusUser(UUID.fromString(uuidString))
        }
        PetInteractions.forgetPetPlay(parseUuids(removed))
        //? if >= 1.21.1 || = 1.8.9
        forgetEmotes(removed)
        val result = PolyConnection.sendPacket(ServerboundPacket.UnsubscribePlayers(removed))
        if (rewatchFriends) subscribePlayers(removed.filter(watchedFriends::contains))
        return result
    }

    private fun unsubscribeAllPlayers() {
        val players = subscribedPlayers.toList()
        subscribedPlayers.clear()
        visiblePlayers.clear()
        pendingSubscriptions.clear()
        //? if >= 1.21.1 || = 1.8.9 {
        remoteEmotes.clear()
        partnerWatchers.clear()
        CosmeticCatalog.releaseEmotePins { it is PlayPin }
        //?}
        CosmeticColors.reset()
        BonusState.reset()
        DuoEmoteState.leaveServer()
        PetInteractions.clearPetPlay()
        capWarned = false
        if (players.isNotEmpty()) {
            PolyConnection.sendPacket(ServerboundPacket.UnsubscribePlayers(players))
        }
        subscribePlayers(watchedFriends.toList())
        subscribeLocalAccount()
    }

    private fun findPlayer(uuid: UUID): AbstractClientPlayer? {
        //? if > 1.8.9 {
        val level = Minecraft.getInstance().level ?: return null
        return level.players().firstOrNull { it.uuid == uuid } ?: localPlayerFor(uuid)
        //?} else {
        /*val level = Minecraft.getInstance().world ?: return null
        return level.players.firstOrNull { it.uuid == uuid } as? AbstractClientPlayer ?: localPlayerFor(uuid)
        *///?}
    }

    private fun localPlayerFor(uuid: UUID): AbstractClientPlayer? =
        Minecraft.getInstance().player?.takeIf { uuid == ClientPlatform.localPlayerUuid() }

    private fun parseUuid(uuidString: String): UUID? = runCatching { UUID.fromString(uuidString) }.getOrNull()

    private fun parseUuids(uuidStrings: Collection<String>): List<UUID> = uuidStrings.mapNotNull(::parseUuid)

    private fun normalizePlayerUuid(uuidString: String): String? {
        val uuid = runCatching { UUID.fromString(uuidString) }.getOrNull() ?: return null
        return uuid.takeIf { it.isRealPlayer() }?.toString()
    }

    private fun UUID.isRealPlayer(): Boolean = version() == 4
}
