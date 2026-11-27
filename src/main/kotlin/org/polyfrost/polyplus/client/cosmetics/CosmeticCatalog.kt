package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.client.plugins.expectSuccess
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.request.parameter
import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.contentType
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaves
import org.polyfrost.polyplus.client.cosmetics.sets.BonusPreview
import org.polyfrost.polyplus.client.network.http.bodyOrThrow
import org.polyfrost.polyplus.client.network.http.getBodyAuthorized
import org.polyfrost.polyplus.client.network.http.putAuthorized
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupKey
import org.polyfrost.polyplus.client.network.http.responses.CosmeticGroupResponse
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition
import org.polyfrost.polyplus.client.network.http.responses.EquippedCosmetics
import org.polyfrost.polyplus.client.network.http.responses.PartialEquippedCosmetics
import org.polyfrost.polyplus.client.network.http.responses.PlayerCosmetics
import org.polyfrost.polyplus.client.network.http.responses.SetEquippedCosmeticsRequest
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.polyfrost.polyplus.client.utils.runSuspendCatching

object CosmeticCatalog {
    private val LOGGER = LogManager.getLogger()
    private val lock = Mutex()

    private val cosmeticDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()
    private val emoteDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()

    private val groupMeta = ConcurrentHashMap<CosmeticGroupKey, GroupMeta>()
    private val remoteEquipped = ConcurrentHashMap<UUID, Map<BodySlot, Int>>()
    private val particleColors = ConcurrentHashMap<UUID, Int>()
    private val confirmedParticleColors = ConcurrentHashMap<UUID, Int>()
    private val polyPlusUsers = ConcurrentHashMap.newKeySet<UUID>()
    private var localEquipped: EquippedCosmetics = EquippedCosmetics()
    private var serverEquipped: Map<BodySlot, Int> = emptyMap()
    private var localServerRemote: Map<BodySlot, Int> = emptyMap()
    private var selectedEmoteId: Int? = null
    private var ownedCosmeticIds: Set<Int> = emptySet()
    private var ownedEmoteIds: Set<Int> = emptySet()
    private val localDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()
    private val localSlots = ConcurrentHashMap<BodySlot, Int>()
    private val unlistedDefinitions = ConcurrentHashMap<Int, CosmeticDefinition>()
    private val definitionRoute = DefinitionRouteGate()
    private const val MAX_IDS_PER_LOOKUP = 100

    @Volatile
    private var publicIds: Set<Int> = emptySet()

    @Volatile
    private var publicGroups: Map<CosmeticGroupKey, GroupMeta> = emptyMap()

    private const val UNKNOWN_REFRESH_INTERVAL_MS = 60_000L
    private val unknownRefreshes = UnknownRefreshTracker()
    private val unknownRefreshScheduled = AtomicBoolean(false)
    private const val ID_LOOKUP_BATCH_MS = 500L
    private val idLookups = UnknownRefreshTracker(retryBaseMs = 2_000L, retryMaxMs = 60_000L)
    private val idLookupScheduled = AtomicBoolean(false)
    private val catalogFetches = AtomicInteger()
    private val catalogGeneration = AtomicLong()
    private var appliedCatalogGeneration = 0L

    private const val CATALOG_UPDATE_JITTER_MS = 5_000L
    private const val CATALOG_FETCH_WAIT_MS = 250L
    private val catalogUpdateScheduled = AtomicBoolean(false)
    private val catalogUpdates = CatalogUpdateRetry()

    @Volatile
    private var lastUnknownRefreshMs = 0L

    @Volatile
    private var catalogListed = false

    @Volatile
    private var ownershipListed = false

    @Volatile
    private var lastCatalogLoadStartMs = 0L

    @Volatile
    private var lastCatalogUpdateMs = 0L

    @Volatile
    private var lastUnlistedRefreshStartMs = 0L

    @Volatile
    private var unlistedWaitsForRoute = false

    private val pinnedEmotes = ConcurrentHashMap<EmoteRevision, CosmeticDefinition>()
    private val latestPins = ConcurrentHashMap<Int, EmoteRevision>()
    private val emotePinHolders = HashMap<Any, EmoteRevision>()
    private val emotePinCounts = LinkedHashMap<EmoteRevision, Int>()

    fun getDefinition(id: Int): CosmeticDefinition? = cosmeticDefinitions[id] ?: getEmoteDefinition(id)

    fun getCosmeticDefinition(id: Int): CosmeticDefinition? = cosmeticDefinitions[id]

    fun getEmoteDefinition(id: Int): CosmeticDefinition? = emoteDefinitions[id] ?: latestPins[id]?.let(pinnedEmotes::get)

    fun getEmoteDefinition(revision: EmoteRevision): CosmeticDefinition? =
        pinnedEmotes[revision] ?: emoteDefinitions[revision.id]?.takeIf { EmoteRevision.of(it) == revision }

    fun isEmotePinned(id: Int): Boolean = latestPins.containsKey(id)

    fun pinnedEmoteRevisions(): Set<EmoteRevision> = HashSet(pinnedEmotes.keys)

    fun pinEmote(holder: Any, emoteId: Int, emote: EmoteDefinition?): EmoteRevision? {
        synchronized(emotePinHolders) {
            val previous = emotePinHolders[holder]
            val wanted = emote?.takeIf { it.id == emoteId }?.asCosmeticDefinition()
            val revision = when {
                wanted != null -> EmoteRevision.of(wanted).also { pinnedEmotes[it] = wanted }
                previous?.id == emoteId -> previous
                else -> emoteDefinitions[emoteId]?.let { listed -> EmoteRevision.of(listed).also { pinnedEmotes.putIfAbsent(it, listed) } }
                    ?: latestPins[emoteId]
            }
            if (previous != null && previous != revision) {
                emotePinHolders.remove(holder)
                unpinEmote(previous)
            }
            if (revision == null) return emoteDefinitions[emoteId]?.let(EmoteRevision::of)
            if (previous != revision) {
                emotePinHolders[holder] = revision
                emotePinCounts.merge(revision, 1, Int::plus)
            }
            latestPins[emoteId] = revision
            return revision
        }
    }

    fun releaseEmotePin(holder: Any) {
        synchronized(emotePinHolders) {
            emotePinHolders.remove(holder)?.let(::unpinEmote)
        }
    }

    fun releaseEmotePins(predicate: (Any) -> Boolean) {
        synchronized(emotePinHolders) {
            val iterator = emotePinHolders.entries.iterator()
            while (iterator.hasNext()) {
                val (holder, revision) = iterator.next()
                if (!predicate(holder)) continue
                iterator.remove()
                unpinEmote(revision)
            }
        }
    }

    private fun unpinEmote(revision: EmoteRevision) {
        if (emotePinCounts.computeIfPresent(revision) { _, count -> (count - 1).takeIf { it > 0 } } != null) return
        pinnedEmotes.remove(revision)
        if (latestPins[revision.id] != revision) return
        val other = emotePinCounts.keys.lastOrNull { it.id == revision.id }
        if (other == null) latestPins.remove(revision.id) else latestPins[revision.id] = other
    }

    fun allDefinitions(): Collection<CosmeticDefinition> = cosmeticDefinitions.values + emoteDefinitions.values

    fun allEmoteDefinitions(): Collection<CosmeticDefinition> = emoteDefinitions.values

    internal fun accountId(uuid: UUID): UUID =
        if (uuid == Minecraft.getInstance().player?.uuid) ClientPlatform.localPlayerUuid() else uuid

    fun getRemoteEquipped(uuid: UUID): Map<BodySlot, Int>? = remoteEquipped[accountId(uuid)]

    fun remoteEquippedIds(): Set<Int> = remoteEquipped.values.flatMapTo(HashSet()) { it.values }

    fun getParticleColor(uuid: UUID): Int? = particleColors[accountId(uuid)]

    fun confirmedParticleColor(uuid: UUID): Int? = confirmedParticleColors[accountId(uuid)]

    fun setParticleColor(uuid: UUID, color: Int) {
        particleColors[uuid] = color
        confirmedParticleColors[uuid] = color
    }

    fun clearParticleColor(uuid: UUID) {
        particleColors.remove(uuid)
        confirmedParticleColors.remove(uuid)
    }

    fun previewParticleColor(uuid: UUID, color: Int?) {
        if (color != null) particleColors[uuid] = color else particleColors.remove(uuid)
    }

    fun confirmParticleColor(uuid: UUID, color: Int?) {
        if (color != null) confirmedParticleColors[uuid] = color else confirmedParticleColors.remove(uuid)
    }

    fun isPolyPlusUser(uuid: UUID): Boolean = accountId(uuid) in polyPlusUsers

    fun setPolyPlusUser(uuid: UUID, online: Boolean) {
        if (online) polyPlusUsers.add(uuid) else polyPlusUsers.remove(uuid)
    }

    fun clearPolyPlusUser(uuid: UUID) {
        polyPlusUsers.remove(uuid)
    }

    fun getActiveId(uuid: UUID, slot: BodySlot): Int? =
        remoteEquipped[accountId(uuid)]?.get(slot)

    fun localEquipped(): EquippedCosmetics = localEquipped

    fun selectedEmoteId(): Int? = selectedEmoteId

    fun setSelectedEmote(id: Int?) {
        selectedEmoteId = id?.takeIf { it in ownedEmoteIds }
    }

    fun ownedIds(): Set<Int> = ownedCosmeticIds + ownedEmoteIds

    fun ownedCosmeticIds(): Set<Int> = ownedCosmeticIds

    fun ownedEmoteIds(): Set<Int> = ownedEmoteIds

    suspend fun refreshCatalog() {
        catalogFetches.incrementAndGet()
        lastUnknownRefreshMs = System.currentTimeMillis()
        try {
            loadCatalog(background = false)
        } finally {
            catalogFetches.decrementAndGet()
        }
    }

    private suspend fun loadCatalog(background: Boolean): Boolean {
        val startedMs = System.currentTimeMillis()
        val generation = catalogGeneration.incrementAndGet()
        val cosmetics = runSuspendCatching {
            PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics") {
                expectSuccess = true
            }.bodyOrThrow<CosmeticList>()
        }.onFailure { reportCatalogFailure(it) }
            .getOrElse {
                if (background) return false
                CosmeticLoadProgress.fail(
                    if (isRuntimeMismatch(it)) CosmeticLoadProgress.OUTDATED_RUNTIME else it.message ?: it::class.java.simpleName,
                )
                return false
            }
        if (!background) {
            CosmeticLoadProgress.onCatalogFetched()
            lastUnknownRefreshMs = System.currentTimeMillis()
        }

        // Drop groups whose type this client version does not recognize
        val knownGroups = cosmetics.contents.filter { it.type != CosmeticType.Unknown }
        val skipped = cosmetics.contents.size - knownGroups.size
        if (skipped > 0) {
            LOGGER.warn("Ignored {} cosmetic group(s) with unknown type/slot", skipped)
        }

        val (emoteGroups, cosmeticGroups) = knownGroups.partition { it.type == CosmeticType.Emote }
        val flattened = cosmeticGroups.flatMap { it.flatten() }
        val flattenedEmotes = emoteGroups.flatMap { it.flatten() }

        val applied = lock.withLock {
            if (generation < appliedCatalogGeneration) return@withLock false
            appliedCatalogGeneration = generation
            val listed = flattened.mapTo(HashSet()) { it.id }
            flattenedEmotes.mapTo(listed) { it.id }
            publicIds = listed
            publicGroups = cosmeticGroups.associate { it.key to it.toMeta() }
            unlistedDefinitions.keys.removeAll(listed)
            if (background) dropUnlisted(flattened, flattenedEmotes, cosmeticGroups)
            if (!background) {
                ownershipListed = false
                cosmeticDefinitions.clear()
            }
            for (definition in flattened) {
                cosmeticDefinitions[definition.id] = definition
            }
            if (!background) {
                for (definition in unlistedInUse()) cosmeticDefinitions.putIfAbsent(definition.id, definition)
            }
            if (!background) groupMeta.clear()
            for (group in cosmeticGroups) {
                val public = group.toMeta()
                val hidden = groupMeta[group.key]?.variantIds.orEmpty().filter { it !in listed && cosmeticDefinitions.containsKey(it) }
                groupMeta[group.key] = public.copy(variantIds = mergeGroupVariants(public.variantIds, hidden))
            }
            if (!background) emoteDefinitions.clear()
            for (definition in flattenedEmotes) {
                emoteDefinitions[definition.id] = definition
            }
            applyLocal()
            catalogListed = true
            true
        }
        if (!applied) {
            LOGGER.info("Discarded a cosmetic catalog response older than the one already applied")
            return true
        }
        unknownRefreshes.forgetKnown(::isListed)
        idLookups.forgetKnown(::isListed)

        LOGGER.info(
            "Loaded {} cosmetic group(s) ({} variant(s)) and {} emote definition(s) from API",
            cosmeticGroups.size,
            flattened.size,
            flattenedEmotes.size,
        )
        synchronized(this) {
            if (startedMs > lastCatalogLoadStartMs) lastCatalogLoadStartMs = startedMs
        }
        BonusPreview.clear()
        PolyPlusClient.SCOPE.launch {
            runSuspendCatching { CosmeticAssetCache.reloadChanged(allDefinitions()) }
                .onFailure { LOGGER.error("Failed to reload changed cosmetic assets", it) }
            runSuspendCatching { CosmeticAssetCache.evictObsoleteDirs() }
                .onFailure { LOGGER.error("Failed to remove outdated cosmetic downloads", it) }
        }
        return true
    }

    private fun dropUnlisted(
        cosmetics: List<CosmeticDefinition>,
        emotes: List<CosmeticDefinition>,
        groups: List<CosmeticGroupResponse>,
    ) {
        val kept = ownedCosmeticIds + ownedEmoteIds + localDefinitions.keys + unlistedInUse().map { it.id }
        val listed = cosmetics.mapTo(HashSet()) { it.id }
        val listedEmotes = emotes.mapTo(HashSet()) { it.id }
        cosmeticDefinitions.keys.removeIf { it !in listed && it !in kept }
        emoteDefinitions.keys.removeIf { it !in listedEmotes && it !in kept }
        val keptGroups = retainedGroupKeys(groups.map { it.key }, cosmeticDefinitions.values, listed)
        groupMeta.keys.removeIf { it !in keptGroups }
    }

    internal fun retainedGroupKeys(
        incoming: Collection<CosmeticGroupKey>,
        definitions: Collection<CosmeticDefinition>,
        listed: Set<Int>,
    ): Set<CosmeticGroupKey> {
        val kept = HashSet(incoming)
        for (definition in definitions) {
            if (definition.id !in listed) kept += definition.groupKey
        }
        return kept
    }

    fun requestRefreshForUnknown(id: Int) {
        if (isListed(id)) return
        val now = System.currentTimeMillis()
        if (definitionRoute.available(now)) {
            if (idLookups.request(id, now)) scheduleIdLookup()
            return
        }
        if (catalogFetches.get() > 0) return
        if (!unknownRefreshes.request(id, now)) return
        scheduleUnknownRefresh()
    }

    private fun unlistedInUse(): List<CosmeticDefinition> {
        val inUse = remoteEquippedIds() + localEquipped.ids()
        unlistedDefinitions.keys.retainAll(inUse)
        return unlistedDefinitions.values.toList()
    }

    private enum class DefinitionFetch { Done, Failed, Unsupported }

    private suspend fun fetchByIds(ids: Collection<Int>): DefinitionFetch {
        if (!definitionRoute.available(System.currentTimeMillis())) return DefinitionFetch.Unsupported
        for (chunk in ids.distinct().chunked(MAX_IDS_PER_LOOKUP)) {
            val list = runSuspendCatching {
                PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics/definitions") {
                    expectSuccess = true
                    parameter("ids", chunk.joinToString(","))
                }.bodyOrThrow<CosmeticList>()
            }.getOrElse { error ->
                if (definitionRouteMissing((error as? ClientRequestException)?.response?.status?.value)) {
                    definitionRoute.missing(System.currentTimeMillis())
                    LOGGER.info("The backend can't look up cosmetics by id yet, so unlisted cosmetics fall back to the catalog")
                    return DefinitionFetch.Unsupported
                }
                LOGGER.warn("Couldn't look up {} unlisted cosmetic(s)", chunk.size, error)
                return DefinitionFetch.Failed
            }
            val found = requestedDefinitions(list, chunk.toSet())
            lock.withLock {
                val listed = publicIds
                for (definition in found) {
                    if (definition.id in listed || localDefinitions.containsKey(definition.id)) continue
                    unlistedDefinitions[definition.id] = definition
                    cosmeticDefinitions[definition.id] = definition
                }
            }
        }
        return DefinitionFetch.Done
    }

    private suspend fun refreshUnknown(ids: List<Int>): Boolean = when (fetchByIds(ids)) {
        DefinitionFetch.Done -> true
        DefinitionFetch.Failed -> false
        DefinitionFetch.Unsupported -> loadCatalog(background = true)
    }

    private suspend fun refreshUnlistedAfterUpdate(): Boolean {
        val startedMs = System.currentTimeMillis()
        val owned = missingFromListing(ownedIds(), publicIds, localDefinitions.keys)
        val changed = ArrayList<CosmeticDefinition>()
        var complete = true
        if (owned.isNotEmpty()) {
            val refreshed = refreshOwnedDefinitions(owned)
            if (refreshed == null) complete = false else changed += refreshed
        }
        val others = unlistedDefinitions.keys - owned
        unlistedWaitsForRoute = false
        if (others.isNotEmpty()) {
            when (fetchByIds(others)) {
                DefinitionFetch.Done -> changed += others.mapNotNull(cosmeticDefinitions::get)
                DefinitionFetch.Failed -> complete = false
                DefinitionFetch.Unsupported -> {
                    unlistedWaitsForRoute = true
                    complete = false
                }
            }
        }
        if (changed.isNotEmpty()) {
            runSuspendCatching { CosmeticAssetCache.reloadChanged(changed) }
                .onFailure { LOGGER.error("Failed to reload changed unlisted cosmetic assets", it) }
        }
        if (!complete) {
            LOGGER.warn("Couldn't refresh every unlisted cosmetic after the catalog changed, retrying later")
            return false
        }
        synchronized(this) {
            if (startedMs > lastUnlistedRefreshStartMs) lastUnlistedRefreshStartMs = startedMs
        }
        return true
    }

    private suspend fun refreshOwnedDefinitions(missing: Set<Int>): List<CosmeticDefinition>? {
        val player = PolyPlusClient.HTTP
            .getBodyAuthorized<PlayerCosmetics>("${PolyPlusConfig.apiUrl}/cosmetics/player")
            .onFailure { LOGGER.warn("Couldn't refresh owned cosmetics missing from the catalog", it) }
            .getOrNull() ?: return null
        val groups = player.owned
            .filter { it.type != CosmeticType.Unknown && it.variants.any { variant -> variant.id in missing } }
        val cosmetics = groups
            .flatMap { it.flatten() }
            .filter { it.id in missing }
        val emotes = player.emotes.map { it.asCosmeticDefinition() }.filter { it.id in missing }
        lock.withLock {
            for (definition in cosmetics) cosmeticDefinitions[definition.id] = definition
            for (group in groups) groupMeta[group.key] = ownedGroupMeta(group)
            for (definition in emotes) emoteDefinitions[definition.id] = definition
            applyLocal()
        }
        return cosmetics + emotes
    }

    private fun ownedGroupMeta(group: CosmeticGroupResponse): GroupMeta {
        val owned = group.toMeta()
        val public = publicGroups[group.key] ?: return owned
        return public.copy(variantIds = mergeGroupVariants(public.variantIds, owned.variantIds))
    }

    private fun isListed(id: Int): Boolean =
        cosmeticDefinitions.containsKey(id) || emoteDefinitions.containsKey(id) || latestPins.containsKey(id)

    private fun scheduleIdLookup() {
        if (!idLookupScheduled.compareAndSet(false, true)) return
        PolyPlusClient.SCOPE.launch {
            try {
                val wait = maxOf(System.currentTimeMillis() + ID_LOOKUP_BATCH_MS, idLookups.nextRefreshAtMs()) - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                val ids = idLookups.takePending()
                if (ids.isEmpty()) return@launch
                var fetch = DefinitionFetch.Failed
                try {
                    fetch = runSuspendCatching { fetchByIds(ids) }
                        .onFailure { LOGGER.error("Looking up unlisted cosmetics by id failed", it) }
                        .getOrDefault(DefinitionFetch.Failed)
                } finally {
                    if (fetch == DefinitionFetch.Unsupported) {
                        idLookups.drop(ids)
                        val now = System.currentTimeMillis()
                        val handedOver = ids.filter { !isListed(it) && unknownRefreshes.request(it, now) }
                        if (handedOver.isNotEmpty()) scheduleUnknownRefresh()
                    } else {
                        idLookups.completed(ids, fetch == DefinitionFetch.Done, System.currentTimeMillis(), ::isListed)
                    }
                }
            } finally {
                idLookupScheduled.set(false)
                if (idLookups.hasPending()) scheduleIdLookup()
            }
        }
    }

    private fun scheduleUnknownRefresh() {
        if (!unknownRefreshScheduled.compareAndSet(false, true)) return
        PolyPlusClient.SCOPE.launch {
            try {
                val wait = maxOf(lastUnknownRefreshMs + UNKNOWN_REFRESH_INTERVAL_MS, unknownRefreshes.nextRefreshAtMs()) - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastUnknownRefreshMs = System.currentTimeMillis()
                val ids = unknownRefreshes.takePending()
                if (ids.isEmpty()) return@launch
                LOGGER.info("Refreshing the cosmetic catalog for equipped cosmetic(s) it did not list")
                catalogFetches.incrementAndGet()
                var loaded = false
                try {
                    loaded = runSuspendCatching { refreshUnknown(ids) }
                        .onFailure { LOGGER.error("Cosmetic catalog refresh for unknown cosmetics failed", it) }
                        .getOrDefault(false)
                } finally {
                    catalogFetches.decrementAndGet()
                    unknownRefreshes.completed(ids, loaded, System.currentTimeMillis(), ::isListed)
                }
            } finally {
                unknownRefreshScheduled.set(false)
                if (unknownRefreshes.hasPending()) scheduleUnknownRefresh()
            }
        }
    }

    fun onCatalogUpdated() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        lastCatalogUpdateMs = System.currentTimeMillis()
        BonusPreview.clear()
        scheduleCatalogUpdate()
    }

    private fun catalogCurrent(): Boolean = lastCatalogLoadStartMs >= lastCatalogUpdateMs

    private fun unlistedCurrent(): Boolean = lastUnlistedRefreshStartMs >= lastCatalogUpdateMs

    private fun updateCurrent(): Boolean = catalogCurrent() && unlistedCurrent()

    private fun scheduleCatalogUpdate() {
        if (!catalogUpdateScheduled.compareAndSet(false, true)) return
        PolyPlusClient.SCOPE.launch {
            try {
                catalogUpdates.run(
                    current = ::updateCurrent,
                    allowed = PrivacyConsent::allowsOnlineServices,
                    fetch = ::refreshUpdatedCatalog,
                    pause = { delay(it) },
                    hold = ::unlistedRouteWaitMs,
                    updates = { lastCatalogUpdateMs },
                )
            } finally {
                catalogUpdateScheduled.set(false)
                if (!updateCurrent() && PrivacyConsent.allowsOnlineServices()) scheduleCatalogUpdate()
            }
        }
    }

    private fun unlistedRouteWaitMs(): Long =
        if (unlistedWaitsForRoute) definitionRoute.waitMs(System.currentTimeMillis()) else 0L

    private suspend fun refreshUpdatedCatalog(): CatalogRefresh {
        delay(Random.nextLong(CATALOG_UPDATE_JITTER_MS + 1))
        while (catalogFetches.get() > 0) delay(CATALOG_FETCH_WAIT_MS)
        if (updateCurrent()) return CatalogRefresh.Done
        catalogFetches.incrementAndGet()
        try {
            return refreshAfterUpdate(
                catalogCurrent = ::catalogCurrent,
                unlistedCurrent = ::unlistedCurrent,
                loadCatalog = {
                    lastUnknownRefreshMs = System.currentTimeMillis()
                    LOGGER.info("Refreshing the cosmetic catalog after it changed")
                    val loaded = runSuspendCatching { loadCatalog(background = true) }
                        .onFailure { LOGGER.error("Cosmetic catalog refresh after a change failed", it) }
                        .getOrDefault(false)
                    if (!loaded) LOGGER.warn("Couldn't refresh the changed cosmetic catalog, retrying later")
                    loaded
                },
                refreshUnlisted = {
                    runSuspendCatching { refreshUnlistedAfterUpdate() }
                        .onFailure { LOGGER.error("Refreshing unlisted cosmetics after a change failed", it) }
                        .getOrDefault(false)
                },
            )
        } finally {
            catalogFetches.decrementAndGet()
        }
    }

    internal fun isRuntimeMismatch(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is LinkageError) return true
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    private fun reportCatalogFailure(error: Throwable) {
        if (isRuntimeMismatch(error)) {
            LOGGER.error(
                "Failed to decode the cosmetic catalog: the kotlinx-serialization runtime on the " +
                    "classpath is older than PolyPlus needs. Update fabric-language-kotlin.",
                error,
            )
            return
        }
        LOGGER.error("Failed to fetch cosmetic catalog", error)
    }

    suspend fun refreshPlayer(): Result<Unit> {
        val seenGeneration = lock.withLock { appliedCatalogGeneration }
        val seenChoice = ColourSaves.particleChoices.seen()
        val player = PolyPlusClient.HTTP
            .getBodyAuthorized<PlayerCosmetics>("${PolyPlusConfig.apiUrl}/cosmetics/player")
            .onFailure { LOGGER.error("Failed to fetch player cosmetics", it) }
            .getOrElse { return Result.failure(it) }

        val ownedGroups = player.owned.filter { it.type != CosmeticType.Unknown }
        val ownedDefs = ownedGroups.flatMap { it.flatten() }
        val equippedKnown = player.equipped.filterKeys { it != BodySlot.Unknown }

        val localUuid = ClientPlatform.localPlayerUuid()
        val color = player.particleColor
        when {
            ColourSaves.particleChoices.since(seenChoice) -> Unit
            color != null -> setParticleColor(localUuid, color)
            else -> clearParticleColor(localUuid)
        }

        val owned = lock.withLock {
            val catalogMoved = appliedCatalogGeneration != seenGeneration
            serverEquipped = equippedKnown
            localEquipped = EquippedCosmetics(equippedKnown)
            ownedCosmeticIds = ownedDefs.map { it.id }.toSet()
            ownedEmoteIds = player.emotes.map { it.id }.toSet()
            selectedEmoteId?.let {
                if (it !in ownedEmoteIds) {
                    selectedEmoteId = null
                }
            }
            val ownedCosmetics = ownedDefs.map { definition ->
                if (catalogMoved) {
                    cosmeticDefinitions.putIfAbsent(definition.id, definition) ?: definition
                } else {
                    cosmeticDefinitions[definition.id] = definition
                    definition
                }
            }
            for (group in ownedGroups) {
                groupMeta[group.key] = ownedGroupMeta(group)
            }
            val ownedEmotes = player.emotes.map { emote ->
                val definition = emote.asCosmeticDefinition()
                if (catalogMoved) {
                    emoteDefinitions.putIfAbsent(definition.id, definition) ?: definition
                } else {
                    emoteDefinitions[definition.id] = definition
                    definition
                }
            }
            applyLocal()
            ownershipListed = true
            ownedCosmetics + ownedEmotes
        }

        //? if >= 1.21.1 || = 1.8.9 {
        val equippedIds = equippedKnown.values.toSet()
        val (equipped, rest) = owned.partition { it.id in equippedIds }
        val assetsToken = CosmeticLoadProgress.beginAssets(
            equipped.size * CosmeticAssetCache.PRELOAD_STEPS_PER_DEFINITION,
        )
        PolyPlusClient.SCOPE.launch {
            try {
                runSuspendCatching { CosmeticAssetCache.preloadDefinitions(equipped, trackProgress = true) }
                    .onFailure { LOGGER.error("Failed to preload equipped cosmetic assets", it) }
            } finally {
                CosmeticLoadProgress.onAssetsComplete(assetsToken)
            }
            runSuspendCatching { CosmeticAssetCache.preloadDefinitions(rest) }
                .onFailure { LOGGER.error("Failed to preload owned cosmetic assets", it) }
            runSuspendCatching { CosmeticAssetCache.evictObsoleteDirs() }
                .onFailure { LOGGER.error("Failed to remove outdated cosmetic downloads", it) }
        }
        //?}
        return Result.success(Unit)
    }

    fun serverEquipment(): Map<BodySlot, Int> = serverEquipped

    internal suspend fun assumeServerEquipped(equipped: Map<BodySlot, Int>) {
        lock.withLock {
            serverEquipped = equipped.filterKeys { it != BodySlot.Unknown }
            overlayLocal()
        }
    }

    suspend fun setEquipped(partial: PartialEquippedCosmetics): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.putAuthorized("${PolyPlusConfig.apiUrl}/cosmetics/player") {
            contentType(ContentType.Application.Json)
            setBody(SetEquippedCosmeticsRequest(partial.equipped))
        }
        Unit
    }.onFailure {
        LOGGER.error("Failed to set equipped cosmetics", it)
    }

    fun applyRemoteActive(uuid: UUID, cosmeticIds: List<Int>) {
        val map = mutableMapOf<BodySlot, Int>()
        for (id in cosmeticIds) {
            val definition = getCosmeticDefinition(id) ?: continue
            val slot = definition.preferredSlot() ?: continue
            map[slot] = id
        }
        applyRemoteEquipped(uuid, map)
    }

    fun applyRemoteEquipped(uuid: UUID, equipment: Map<BodySlot, Int>) {
        val known = equipment.filterKeys { it != BodySlot.Unknown }
        if (uuid != ClientPlatform.localPlayerUuid()) return storeRemote(uuid, known)
        val previous = localServerRemote
        localServerRemote = known.mapNotNull { (slot, id) ->
            (if (localDefinitions.containsKey(id)) previous[slot] else id)?.let { slot to it }
        }.toMap()
        storeRemote(uuid, localServerRemote + localSlots)
    }

    private fun storeRemote(uuid: UUID, known: Map<BodySlot, Int>) {
        if (known.isEmpty()) {
            remoteEquipped.remove(uuid)
        } else {
            remoteEquipped[uuid] = known
        }
    }

    fun applyRemoteEquippedSlot(uuid: UUID, slot: BodySlot, cosmeticId: Int?) {
        if (slot == BodySlot.Unknown) return
        val next = remoteEquipped[uuid].orEmpty().toMutableMap()
        if (cosmeticId == null) {
            next.remove(slot)
        } else {
            next[slot] = cosmeticId
        }
        applyRemoteEquipped(uuid, next)
    }

    fun removeRemote(uuid: UUID) {
        if (uuid == ClientPlatform.localPlayerUuid()) localServerRemote = emptyMap()
        remoteEquipped.remove(uuid)
        particleColors.remove(uuid)
        confirmedParticleColors.remove(uuid)
        polyPlusUsers.remove(uuid)
    }

    // One entry per buyable cosmetic emotes are excluded and have their own list
    fun cosmeticGroupViews(): List<CosmeticGroupView> =
        groupMeta.values.mapNotNull { meta ->
            val variants = meta.variantIds.mapNotNull { cosmeticDefinitions[it] }
            if (variants.isEmpty()) {
                null
            } else {
                CosmeticGroupView(meta.key.id, meta.type, meta.name, meta.allowedSlots, variants, meta.key.grouped)
            }
        }

    fun groupContaining(cosmeticId: Int): CosmeticGroupView? =
        groupFor(cosmeticId, cosmeticDefinitions[cosmeticId]?.groupKey, cosmeticGroupViews())

    internal fun groupFor(cosmeticId: Int, key: CosmeticGroupKey?, groups: List<CosmeticGroupView>): CosmeticGroupView? =
        key?.let { wanted -> groups.firstOrNull { it.key == wanted && it.variants.any { variant -> variant.id == cosmeticId } } }
            ?: groups.firstOrNull { group -> group.variants.any { it.id == cosmeticId } }

    // Label to representative variant id in backend order with the slim/wide axis collapsed away
    fun userFacingVariants(group: CosmeticGroupView): List<Pair<String, Int>> {
        val byLabel = LinkedHashMap<String, Int>()
        for (variant in group.variants) {
            byLabel.getOrPut(variant.variantName) { variant.id }
        }
        return byLabel.map { it.key to it.value }
    }

    fun ownedCosmeticGroups(): List<CosmeticGroupView> =
        cosmeticGroupViews().filter { group -> group.variants.any { it.id in ownedCosmeticIds } }

    fun resolveVariantForSkin(variantId: Int, slim: Boolean): Int {
        val definition = cosmeticDefinitions[variantId] ?: return variantId
        if (definition.model == null) return variantId
        val meta = groupMeta[definition.groupKey] ?: return variantId
        val owned = ownedCosmeticIds
        val worn = serverEquipped
        val usable: ((Int) -> Boolean)? = if (variantId in owned) { id -> id in owned || worn.containsValue(id) } else null
        return skinVariant(definition, meta.variantIds.mapNotNull { cosmeticDefinitions[it] }, slim, usable)
    }

    internal fun skinVariant(
        definition: CosmeticDefinition,
        group: List<CosmeticDefinition>,
        slim: Boolean,
        usable: ((Int) -> Boolean)? = null,
    ): Int {
        val wanted = if (slim) "slim" else "wide"
        if (definition.model == null || definition.model == wanted) return definition.id
        val siblings = group.filter {
            it.variantName == definition.variantName && it.model == wanted && (usable == null || usable(it.id))
        }
        val slots = definition.allowedSlots.toSet()
        val sibling = siblings.firstOrNull { it.allowedSlots.toSet() == slots }
            ?: definition.preferredSlot()?.let { slot -> siblings.firstOrNull { it.preferredSlot() == slot && it.allowsSlot(slot) } }
        return sibling?.id ?: definition.id
    }

    fun addLocal(definition: CosmeticDefinition, slot: BodySlot?) {
        localDefinitions[definition.id] = definition
        if (slot != null) localSlots[slot] = definition.id
        applyLocal()
    }

    fun isLocal(cosmeticId: Int): Boolean = localDefinitions.containsKey(cosmeticId)

    fun hasLocalSlot(slot: BodySlot): Boolean = localSlots.containsKey(slot)

    fun setLocalSlot(slot: BodySlot, cosmeticId: Int?) {
        if (cosmeticId == null) localSlots.remove(slot) else localSlots[slot] = cosmeticId
        overlayLocal()
    }

    fun clearLocal(): Int {
        val ids = localDefinitions.keys.toSet()
        if (ids.isEmpty()) return 0
        val keys = localDefinitions.values.mapTo(HashSet()) { it.groupKey }
        localDefinitions.clear()
        localSlots.clear()
        for (id in ids) {
            cosmeticDefinitions.remove(id)
            emoteDefinitions.remove(id)
        }
        groupMeta.keys.removeAll(keys)
        ownedCosmeticIds = ownedCosmeticIds - ids
        ownedEmoteIds = ownedEmoteIds - ids
        if (selectedEmoteId in ids) selectedEmoteId = null
        overlayLocal()
        return ids.size
    }

    private fun overlayLocal() {
        localEquipped = EquippedCosmetics(serverEquipped + localSlots)
        storeRemote(ClientPlatform.localPlayerUuid(), localServerRemote + localSlots)
    }

    private fun applyLocal() {
        if (localDefinitions.isEmpty()) return
        for (definition in localDefinitions.values) {
            if (definition.type == CosmeticType.Emote) {
                emoteDefinitions[definition.id] = definition
            } else {
                cosmeticDefinitions[definition.id] = definition
                groupMeta[definition.groupKey] = GroupMeta(
                    definition.groupKey,
                    definition.type,
                    definition.groupName,
                    definition.allowedSlots,
                    listOf(definition.id),
                )
            }
        }
        val (emotes, cosmetics) = localDefinitions.values.partition { it.type == CosmeticType.Emote }
        ownedCosmeticIds = ownedCosmeticIds + cosmetics.map { it.id }
        ownedEmoteIds = ownedEmoteIds + emotes.map { it.id }
        if (localSlots.isEmpty()) return
        overlayLocal()
    }

    fun isCatalogListed(): Boolean = catalogListed

    fun isOwnershipListed(): Boolean = ownershipListed

    fun reset() {
        catalogListed = false
        ownershipListed = false
        unknownRefreshes.clear()
        idLookups.clear()
        cosmeticDefinitions.clear()
        unlistedDefinitions.clear()
        publicIds = emptySet()
        publicGroups = emptyMap()
        emoteDefinitions.clear()
        groupMeta.clear()
        remoteEquipped.clear()
        particleColors.clear()
        confirmedParticleColors.clear()
        polyPlusUsers.clear()
        localEquipped = EquippedCosmetics()
        serverEquipped = emptyMap()
        localServerRemote = emptyMap()
        selectedEmoteId = null
        ownedCosmeticIds = emptySet()
        ownedEmoteIds = emptySet()
    }

    private data class GroupMeta(
        val key: CosmeticGroupKey,
        val type: CosmeticType,
        val name: String,
        val allowedSlots: List<BodySlot>,
        val variantIds: List<Int>,
    )

    private fun CosmeticGroupResponse.toMeta(): GroupMeta =
        GroupMeta(key, type, name, allowedSlots, variants.map { it.id })
}

internal enum class CatalogRefresh { Done, PublicOnly, Failed }

internal suspend fun refreshAfterUpdate(
    catalogCurrent: () -> Boolean,
    unlistedCurrent: () -> Boolean,
    loadCatalog: suspend () -> Boolean,
    refreshUnlisted: suspend () -> Boolean,
): CatalogRefresh {
    if (!catalogCurrent() && !loadCatalog()) return CatalogRefresh.Failed
    if (unlistedCurrent()) return CatalogRefresh.Done
    return if (refreshUnlisted()) CatalogRefresh.Done else CatalogRefresh.PublicOnly
}

internal class CatalogUpdateRetry(
    private val baseMs: Long = 5_000L,
    private val maxMs: Long = 300_000L,
) {
    suspend fun run(
        current: () -> Boolean,
        allowed: () -> Boolean,
        fetch: suspend () -> CatalogRefresh,
        pause: suspend (Long) -> Unit,
        hold: () -> Long = { 0L },
        updates: () -> Long = { 0L },
    ) {
        var publicBackoff = baseMs
        var hiddenBackoff = baseMs
        while (allowed() && !current()) {
            val seen = updates()
            val wait = when (fetch()) {
                CatalogRefresh.Done -> {
                    publicBackoff = baseMs
                    hiddenBackoff = baseMs
                    continue
                }
                CatalogRefresh.PublicOnly -> {
                    publicBackoff = baseMs
                    maxOf(hiddenBackoff, hold()).also { hiddenBackoff = (hiddenBackoff * 2).coerceAtMost(maxMs) }
                }
                CatalogRefresh.Failed -> publicBackoff.also { publicBackoff = (publicBackoff * 2).coerceAtMost(maxMs) }
            }
            var waited = 0L
            while (waited < wait && allowed() && updates() == seen) {
                val step = minOf(baseMs, wait - waited)
                pause(step)
                waited += step
            }
        }
    }
}

data class CosmeticGroupView(
    val groupId: Int,
    val type: CosmeticType,
    val name: String,
    val allowedSlots: List<BodySlot>,
    val variants: List<CosmeticDefinition>,
    val grouped: Boolean = false,
) {
    val key: CosmeticGroupKey get() = CosmeticGroupKey(groupId, grouped)
}
