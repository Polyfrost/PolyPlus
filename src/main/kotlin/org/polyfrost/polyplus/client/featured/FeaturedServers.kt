package org.polyfrost.polyplus.client.featured

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object FeaturedServers {
    private const val PRODUCTION_URL = "https://data-v2.polyfrost.org/oneclient/servers.json"
    private const val DEVELOPMENT_FILE_PROPERTY = "polyplus.featuredServers.file"
    private const val MAX_CATALOG_BYTES = 1_048_576

    private val logger = LogManager.getLogger("PolyPlus/FeaturedServers")
    private val lock = Any()
    private val refreshing = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<Runnable>()
    private val _state = MutableStateFlow(FeaturedServersSnapshot())
    private var loaded = false
    private var revision = 0L
    private var lastAttemptMillis = 0L
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
            synchronized(lock) {
                expiryJob?.cancel()
                publishLocked(
                    emptyList(),
                    0L,
                    _state.value.mainMenuDismissedCampaignIds,
                    _state.value.multiplayerDismissedCampaignIds,
                )
            }
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
        synchronized(lock) {
            if (!FeaturedServerCachePolicy.shouldRefresh(lastAttemptMillis, now, force)) return
            lastAttemptMillis = now
        }
        if (!refreshing.compareAndSet(false, true)) return

        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                val body = fetchCatalogBody()
                require(body.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) { "catalog exceeds 1 MiB" }
                val decoded = FeaturedServerCatalogCodec.decode(body)
                decoded.warnings.forEach { logger.warn(it) }
                if (!PrivacyConsent.allowsOnlineServices()) return@launch
                val fetchedAt = System.currentTimeMillis()
                synchronized(lock) {
                    publishLocked(
                        decoded.servers,
                        FeaturedServerCachePolicy.expiresAt(fetchedAt),
                        _state.value.mainMenuDismissedCampaignIds,
                        _state.value.multiplayerDismissedCampaignIds,
                    )
                }
                persistCache(CacheEnvelope(fetchedAt, body))
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
        synchronized(lock) {
            if (campaignId in _state.value.mainMenuDismissedCampaignIds) return
            val mainMenuDismissed = _state.value.mainMenuDismissedCampaignIds + campaignId
            publishLocked(
                _state.value.servers,
                _state.value.expiresAtMillis,
                mainMenuDismissed,
                _state.value.multiplayerDismissedCampaignIds,
            )
            persistDismissals(Dismissals(mainMenuDismissed, _state.value.multiplayerDismissedCampaignIds))
        }
    }

    @JvmStatic
    fun dismissMultiplayer(campaignId: String) {
        if (campaignId.isBlank()) return
        ensureLoaded()
        synchronized(lock) {
            if (campaignId in _state.value.multiplayerDismissedCampaignIds) return
            val multiplayerDismissed = _state.value.multiplayerDismissedCampaignIds + campaignId
            publishLocked(
                _state.value.servers,
                _state.value.expiresAtMillis,
                _state.value.mainMenuDismissedCampaignIds,
                multiplayerDismissed,
            )
            persistDismissals(Dismissals(_state.value.mainMenuDismissedCampaignIds, multiplayerDismissed))
        }
    }

    @JvmStatic
    fun snapshot(): FeaturedServersSnapshot {
        ensureLoaded()
        return _state.value
    }

    @JvmStatic
    fun revision(): Long = snapshot().revision

    @JvmStatic
    fun addListener(listener: Runnable) {
        listeners += listener
    }

    @JvmStatic
    fun removeListener(listener: Runnable) {
        listeners -= listener
    }

    private fun ensureLoaded() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val dismissals = runCatching {
                dismissalFile.takeIf(File::isFile)?.readText()?.let {
                    PolyPlusClient.JSON.decodeFromString(Dismissals.serializer(), it)
                }
            }.getOrNull() ?: Dismissals()
            publishLocked(
                emptyList(),
                0L,
                dismissals.mainMenuCampaignIds,
                dismissals.multiplayerCampaignIds,
            )
        }
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
        synchronized(lock) {
            publishLocked(
                decoded.servers,
                expiresAt,
                _state.value.mainMenuDismissedCampaignIds,
                _state.value.multiplayerDismissedCampaignIds,
            )
        }
    }

    private fun publishLocked(
        servers: List<FeaturedServer>,
        expiresAtMillis: Long,
        mainMenuDismissed: Set<String>,
        multiplayerDismissed: Set<String>,
    ) {
        revision++
        _state.value = FeaturedServersSnapshot(
            servers.toList(),
            mainMenuDismissed.toSet(),
            multiplayerDismissed.toSet(),
            expiresAtMillis,
            revision,
        )
        listeners.forEach { listener ->
            runCatching(listener::run).onFailure { logger.warn("Featured-server listener failed", it) }
        }
        expiryJob?.cancel()
        val delayMillis = expiresAtMillis - System.currentTimeMillis()
        if (servers.isNotEmpty() && delayMillis > 0) {
            val expectedExpiry = expiresAtMillis
            expiryJob = PolyPlusClient.SCOPE.launch {
                delay(delayMillis)
                synchronized(lock) {
                    if (_state.value.expiresAtMillis == expectedExpiry) {
                        publishLocked(
                            emptyList(),
                            0L,
                            _state.value.mainMenuDismissedCampaignIds,
                            _state.value.multiplayerDismissedCampaignIds,
                        )
                    }
                }
            }
        } else {
            expiryJob = null
        }
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
        @kotlinx.serialization.SerialName("fetched_at") val fetchedAtMillis: Long,
        val body: String,
    )

    @Serializable
    private data class Dismissals(
        @kotlinx.serialization.SerialName("main_menu_campaign_ids") val mainMenuCampaignIds: Set<String> = emptySet(),
        @kotlinx.serialization.SerialName("multiplayer_campaign_ids") val multiplayerCampaignIds: Set<String> = emptySet(),
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
