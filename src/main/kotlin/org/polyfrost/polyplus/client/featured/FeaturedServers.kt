package org.polyfrost.polyplus.client.featured

import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object FeaturedServers {
    private const val PRODUCTION_URL = "https://data-v2.polyfrost.org/oneclient/servers.json"
    private const val DEVELOPMENT_FILE_PROPERTY = "polyplus.featuredServers.file"
    private const val MAX_CATALOG_BYTES = 1_048_576

    private val logger = LogManager.getLogger("PolyPlus/FeaturedServers")
    private val lock = Any()
    private val refreshing = AtomicBoolean(false)
    private val lastAttemptMillis = AtomicLong(0L)
    private val _state = MutableStateFlow(FeaturedServersSnapshot())
    @Volatile
    private var loaded = false
    @Volatile
    private var committed = FeaturedServersSnapshot()
    private var expiryJob: Job? = null

    @JvmField
    val state: StateFlow<FeaturedServersSnapshot> = _state.asStateFlow()

    private val cacheFile: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/featured_servers_cache.json")

    private val dismissalFile: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/featured_server_dismissals.json")

    @JvmStatic
    fun warmUp() {
        ensureLoaded()
        if (PrivacyConsent.allowsOnlineServices()) refresh()
    }

    @JvmStatic
    fun applyConsent() {
        ensureLoaded()
        if (!PrivacyConsent.allowsOnlineServices()) {
            val snapshot = synchronized(lock) {
                expiryJob?.cancel()
                commitLocked(
                    emptyList(),
                    0L,
                    committed.mainMenuDismissedCampaignIds,
                    committed.multiplayerDismissedCampaignIds,
                )
            }
            publish(snapshot)
            return
        }
        restoreCache()
        refresh(force = true)
    }

    @JvmStatic
    fun refresh(force: Boolean = false) {
        ensureLoaded()
        if (!PrivacyConsent.allowsOnlineServices()) return
        val now = System.currentTimeMillis()
        while (true) {
            val previous = lastAttemptMillis.get()
            if (!FeaturedServerCachePolicy.shouldRefresh(previous, now, force)) return
            if (lastAttemptMillis.compareAndSet(previous, now)) break
        }
        if (!refreshing.compareAndSet(false, true)) return

        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                val body = fetchCatalogBody()
                require(body.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "catalog exceeds 1 MiB" }
                val decoded = FeaturedServerCatalogCodec.decode(body)
                decoded.warnings.forEach { logger.warn(it) }
                val fetchedAt = System.currentTimeMillis()
                val snapshot = synchronized(lock) {
                    if (!PrivacyConsent.allowsOnlineServices()) return@launch
                    commitLocked(
                        decoded.servers,
                        FeaturedServerCachePolicy.expiresAt(fetchedAt),
                        committed.mainMenuDismissedCampaignIds,
                        committed.multiplayerDismissedCampaignIds,
                    )
                }
                publish(snapshot)
                persistCache(CacheEnvelope(fetchedAt, body))
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                logger.warn("Could not refresh featured servers; retaining valid cache", error)
            } finally {
                refreshing.set(false)
            }
        }
    }

    @JvmStatic
    fun dismissMainMenu(campaignId: String) {
        if (campaignId.isBlank()) return
        ensureLoaded()
        val snapshot = synchronized(lock) {
            val current = committed
            if (campaignId in current.mainMenuDismissedCampaignIds) return
            if (!current.isMainMenuDismissible(campaignId)) return
            val mainMenuDismissed = current.mainMenuDismissedCampaignIds + campaignId
            val next = commitLocked(
                current.servers,
                current.expiresAtMillis,
                mainMenuDismissed,
                current.multiplayerDismissedCampaignIds,
            )
            persistDismissals(Dismissals(mainMenuDismissed, current.multiplayerDismissedCampaignIds))
            next
        }
        publish(snapshot)
    }

    @JvmStatic
    @JvmOverloads
    fun dismissMultiplayer(campaignId: String, force: Boolean = false) {
        if (campaignId.isBlank()) return
        ensureLoaded()
        val snapshot = synchronized(lock) {
            val current = committed
            if (campaignId in current.multiplayerDismissedCampaignIds) return
            if (!force && !current.isMultiplayerDismissible(campaignId)) return
            val multiplayerDismissed = current.multiplayerDismissedCampaignIds + campaignId
            val next = commitLocked(
                current.servers,
                current.expiresAtMillis,
                current.mainMenuDismissedCampaignIds,
                multiplayerDismissed,
            )
            persistDismissals(Dismissals(current.mainMenuDismissedCampaignIds, multiplayerDismissed))
            next
        }
        publish(snapshot)
    }

    @JvmStatic
    fun restoreMultiplayer(campaignId: String) {
        if (campaignId.isBlank()) return
        ensureLoaded()
        val snapshot = synchronized(lock) {
            val current = committed
            if (campaignId !in current.multiplayerDismissedCampaignIds) return
            val multiplayerDismissed = current.multiplayerDismissedCampaignIds - campaignId
            val next = commitLocked(
                current.servers,
                current.expiresAtMillis,
                current.mainMenuDismissedCampaignIds,
                multiplayerDismissed,
            )
            persistDismissals(Dismissals(current.mainMenuDismissedCampaignIds, multiplayerDismissed))
            next
        }
        publish(snapshot)
    }

    @JvmStatic
    fun isMultiplayerRestorable(server: FeaturedServer): Boolean = snapshot().isMultiplayerRestorable(server)

    @JvmStatic
    fun dismissMultiplayerByAddress(address: String) {
        if (address.isBlank()) return
        val normalized = normalizeServerAddress(address)
        val campaign = snapshot().visibleServers()
            .firstOrNull { normalizeServerAddress(it.address) == normalized }
            ?.featured ?: return
        dismissMultiplayer(campaign.campaignId, force = true)
    }

    @JvmStatic
    fun iconUrl(serverId: String): String = iconUrl(endpoint(), serverId)

    internal fun iconUrl(catalogUrl: String, serverId: String): String {
        val id = URLEncoder.encode(serverId, StandardCharsets.UTF_8).replace("+", "%20")
        return "${catalogUrl.removeSuffix(".json")}/$id.png"
    }

    @JvmStatic
    fun snapshot(): FeaturedServersSnapshot {
        ensureLoaded()
        return committed
    }

    @JvmStatic
    fun revision(): Long = snapshot().revision

    private fun ensureLoaded() {
        if (loaded) return
        val initial = synchronized(lock) {
            if (loaded) return
            val dismissals = runCatching {
                dismissalFile.takeIf(File::isFile)?.readText()?.let {
                    PolyPlusClient.JSON.decodeFromString(Dismissals.serializer(), it)
                }
            }.getOrNull() ?: Dismissals()
            val first = commitLocked(
                emptyList(),
                0L,
                dismissals.mainMenuCampaignIds,
                dismissals.multiplayerCampaignIds,
            )
            loaded = true
            first
        }
        publish(initial)
        if (PrivacyConsent.allowsOnlineServices()) restoreCache()
    }

    private fun restoreCache() {
        val envelope = runCatching {
            cacheFile.takeIf(File::isFile)?.readText()?.let {
                PolyPlusClient.JSON.decodeFromString(CacheEnvelope.serializer(), it)
            }
        }.getOrNull() ?: return
        val expiresAt = FeaturedServerCachePolicy.expiresAt(envelope.fetchedAtMillis)
        if (!FeaturedServerCachePolicy.isFresh(envelope.fetchedAtMillis, System.currentTimeMillis())) return
        val decoded = runCatching { FeaturedServerCatalogCodec.decode(envelope.body) }
            .onFailure { logger.warn("Ignoring invalid featured-server cache", it) }
            .getOrNull() ?: return
        decoded.warnings.forEach { logger.warn(it) }
        val snapshot = synchronized(lock) {
            commitLocked(
                decoded.servers,
                expiresAt,
                committed.mainMenuDismissedCampaignIds,
                committed.multiplayerDismissedCampaignIds,
            )
        }
        publish(snapshot)
    }

    private fun publish(snapshot: FeaturedServersSnapshot) {
        _state.update { current -> if (snapshot.revision > current.revision) snapshot else current }
    }

    private fun commitLocked(
        servers: List<FeaturedServer>,
        expiresAtMillis: Long,
        mainMenuDismissed: Set<String>,
        multiplayerDismissed: Set<String>,
    ): FeaturedServersSnapshot {
        val snapshot = FeaturedServersSnapshot(
            servers.toList(),
            mainMenuDismissed.toSet(),
            multiplayerDismissed.toSet(),
            expiresAtMillis,
            committed.revision + 1,
        )
        committed = snapshot
        expiryJob?.cancel()
        val delayMillis = expiresAtMillis - System.currentTimeMillis()
        if (servers.isNotEmpty() && delayMillis > 0) {
            val expectedExpiry = expiresAtMillis
            expiryJob = PolyPlusClient.SCOPE.launch {
                delay(delayMillis)
                val expired = synchronized(lock) {
                    if (committed.expiresAtMillis != expectedExpiry) return@synchronized null
                    commitLocked(
                        emptyList(),
                        0L,
                        committed.mainMenuDismissedCampaignIds,
                        committed.multiplayerDismissedCampaignIds,
                    )
                }
                if (expired != null) publish(expired)
            }
        } else {
            expiryJob = null
        }
        return snapshot
    }

    private fun persistCache(envelope: CacheEnvelope) = runCatching {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(PolyPlusClient.JSON.encodeToString(CacheEnvelope.serializer(), envelope))
    }.onFailure { logger.warn("Could not persist featured-server cache", it) }

    private fun persistDismissals(dismissals: Dismissals) = runCatching {
        dismissalFile.parentFile?.mkdirs()
        dismissalFile.writeText(PolyPlusClient.JSON.encodeToString(Dismissals.serializer(), dismissals))
    }.onFailure { logger.warn("Could not persist featured-server dismissals", it) }

    private fun endpoint(): String {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return PRODUCTION_URL
        return System.getProperty("polyplus.featuredServers.url")?.takeIf(String::isNotBlank) ?: PRODUCTION_URL
    }

    private suspend fun fetchCatalogBody(): String {
        developmentCatalogFile()?.let { file ->
            logger.info("Loading development featured-server catalog from {}", file)
            return file.readText()
        }

        val response = PolyPlusClient.HTTP.get(endpoint())
        require(response.status.value in 200..299) { "HTTP ${response.status.value}" }
        return response.bodyAsText()
    }

    private fun developmentCatalogFile(): File? {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return null
        System.getProperty(DEVELOPMENT_FILE_PROPERTY)?.takeIf(String::isNotBlank)?.let { configuredPath ->
            return File(configuredPath).also {
                require(it.isFile) { "development featured-server catalog does not exist: $it" }
            }
        }

        return generateSequence(FabricLoader.getInstance().gameDir.toFile(), File::getParentFile)
            .take(6)
            .mapNotNull { directory ->
                directory.parentFile?.resolve("DataStorageV2/data/oneclient/servers.json")
            }
            .firstOrNull(File::isFile)
    }

    @Serializable
    private data class CacheEnvelope(
        @SerialName("fetched_at") val fetchedAtMillis: Long,
        val body: String,
    )

    @Serializable
    private data class Dismissals(
        @SerialName("main_menu_campaign_ids") val mainMenuCampaignIds: Set<String> = emptySet(),
        @SerialName("multiplayer_campaign_ids") val multiplayerCampaignIds: Set<String> = emptySet(),
    )
}

object FeaturedServerCachePolicy {
    const val CACHE_TTL_MILLIS: Long = 24L * 60L * 60L * 1_000L
    const val REFRESH_INTERVAL_MILLIS: Long = 15L * 60L * 1_000L

    fun expiresAt(fetchedAtMillis: Long): Long = fetchedAtMillis + CACHE_TTL_MILLIS

    fun isFresh(fetchedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= fetchedAtMillis && nowMillis < expiresAt(fetchedAtMillis)

    fun shouldRefresh(lastAttemptMillis: Long, nowMillis: Long, force: Boolean): Boolean =
        force || lastAttemptMillis == 0L || nowMillis - lastAttemptMillis >= REFRESH_INTERVAL_MILLIS
}
