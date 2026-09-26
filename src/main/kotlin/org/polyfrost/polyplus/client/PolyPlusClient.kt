package org.polyfrost.polyplus.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults
import org.polyfrost.polyplus.client.features.AdvancedModCards
import org.polyfrost.polyplus.client.features.DefaultModOrder
import org.polyfrost.polyplus.client.features.DefaultSettings
import org.polyfrost.polyplus.client.features.JvmAdvisor
import org.polyfrost.polyplus.client.features.ModpackDiff
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.VanillaMenuButton
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.launcher.SessionAccounts
import org.polyfrost.polyplus.client.legal.LegalDocuments
import org.polyfrost.polyplus.client.network.http.MinecraftLoginGate
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.pets.PetEntities
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.privacy.PrivacyGate
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

//? if >= 26.2 {
import org.polyfrost.polyplus.compat.RrlsCrashGuard
//?}
//? if wwaypoints {
import org.polyfrost.polyplus.compat.WWaypointsCompat
//?}

//? if >= 1.21.11 {
import org.polyfrost.polyplus.client.gui.panorama.CustomPanorama
//?}

object PolyPlusClient {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)
    private val cosmeticsRefreshInProgress = AtomicBoolean(false)

    private val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, throwable ->
        LOGGER.error("Uncaught exception in PolyPlus coroutine", throwable)
    }

    @JvmField val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default + EXCEPTION_HANDLER)

    @JvmField val JSON = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @JvmField val HTTP = HttpClient(CIO) {
        defaultRequest {
            userAgent("${PolyPlusConstants.NAME}/${PolyPlusConstants.VERSION}")
        }

        install(ContentNegotiation) {
            json(JSON)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, _ -> false }
            retryOnExceptionIf { request, cause ->
                cause is IOException && (request.method == HttpMethod.Get || request.method == HttpMethod.Head)
            }
            constantDelay(millis = 250, randomizationMs = 250)
        }

        install(WebSockets) {
            pingIntervalMillis = 15_000
        }

        install(PrivacyGate)

        HttpResponseValidator {
            validateResponse { response ->
                val status = response.status
                // Only 4xx and 5xx are failures 1xx like the 101 WebSocket upgrade and 3xx are not
                if (status.value < HttpStatusCode.BadRequest.value) return@validateResponse
                if (status == HttpStatusCode.Unauthorized) return@validateResponse
                if (response.request.url.host != apiHost()) return@validateResponse

                val text = runSuspendCatching { response.bodyAsText() }.getOrDefault("")
                throw if (status.value >= 500) {
                    ServerResponseException(response, text)
                } else {
                    ClientRequestException(response, text)
                }
            }
        }
    }

    private fun apiHost(): String? =
        runCatching { Url(PolyPlusConfig.apiUrl.url).host }.getOrNull()

    private inline fun step(name: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            LOGGER.error("PolyPlus init step '{}' failed; continuing without it", name, error)
        }
    }

    fun initialize() {
        step("sentry") { PolyPlusSentry.initialize() }
        step("crash outcome tracker") { CrashOutcomeTracker.installHeartbeat() }
        step("crash log upload") { SCOPE.launch(Dispatchers.IO) { PolyPlusCrashLogUploader.uploadPending() } }
        step("config preload") { PolyPlusConfig.preload() }
        step("main menu config preload") { PolyPlusMainMenuConfig.preload() }
        step("cosmetics config preload") { PolyPlusCosmeticsConfig.preload() }
        step("privacy enforcement") { PrivacyEnforcement.syncConfig() }
        step("legal documents") {
            if (PrivacyConsent.state() == PrivacyConsent.State.ACCEPTED && !PrivacyConsent.managedByLauncher) {
                SCOPE.launch(Dispatchers.IO) { LegalDocuments.load() }
            }
        }
        step("rich text privacy") { RichTextPrivacy.warmUp() }
        step("default settings") { DefaultSettings.initialize() }
        step("default mod order") { DefaultModOrder.initialize() }
        step("advanced mod cards") { AdvancedModCards.initialize() }
        step("onboarding") { OnboardingFeatures.initialize() }
        //? if >= 26.2
        step("rrls crash guard") { RrlsCrashGuard.initialize() }
        step("adaptive blur") { AdaptiveBlurDefaults.initialize() }
        step("jvm advisor") { JvmAdvisor.initialize() }
        step("modpack diff") { ModpackDiff.logAsync() }
        step("login gate") { MinecraftLoginGate.register() }
        step("featured servers") { FeaturedServers.warmUp() }
        //? if wwaypoints
        step("wwaypoints compat") { WWaypointsCompat.initialize() }

        //? if >= 1.21.1
        step("early init CosmeticSync") { CosmeticSync.earlyInitialize() }
        step("early init FriendsRepository") { FriendsRepository.earlyInitialize() }
        step("early init GroupsRepository") { GroupsRepository.earlyInitialize() }
        // global chat is disabled for now
        // step("early init GlobalChatRepository") { GlobalChatRepository.earlyInitialize() }
        step("early init SessionsRepository") { SessionsRepository.earlyInitialize() }
        step("early init P2PSessionManager") { P2PSessionManager.earlyInitialize() }

        //? if >= 1.21.1
        step("pet entities") { PetEntities.register() }
        step("vanilla menu button") { VanillaMenuButton.register() }

        step("websocket") {
            PolyConnection.initialize {
                LOGGER.info("Connected to PolyPlus WebSocket server.")

                SCOPE.launch {
                    PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(ClientPlatform.localPlayerUuid().toString()))
                    //? if >= 1.21.1
                    CosmeticSync.resubscribeVisiblePlayers()
                    if (Minecraft.getInstance().player != null) {
                        refreshCosmetics()
                    }

                    FriendsRepository.refreshAll()
                    GroupsRepository.refreshGroups()
                    // GlobalChatRepository.refreshHistory() // Global chat is disabled for now.
                    SessionsRepository.refreshIncoming()
                }
            }
        }

        step("session accounts") { SessionAccounts.capture() }

        step("cosmetics prefetch") { refreshCosmetics() }
        step("commands") { PolyPlusCommands.register() }
        step("host world") { HostWorldManager.registerLanPublishHook() }
        //? if >= 1.21.11
        step("panorama") { CustomPanorama.initialize() }
    }

    // Full reset of auth caches and API data
    fun refresh() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        LOGGER.info("Refreshing PolyPlus Client...")

        SCOPE.launch {
            runSuspendCatching { PolyAuthorization.reset() }

            runCatching {
                CosmeticCatalog.reset()
                CosmeticAssetCache.reset()
            }

            runCatching { PolyConnection.reconnect() }
            runCatching { P2PSessionManager.reconnect() }
            runCatching { FeaturedServers.refresh(force = true) }

            refreshCosmeticsInternal()
        }
    }

    // Refetches cosmetics without wiping auth or caches
    fun refreshCosmetics() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        if (!cosmeticsRefreshInProgress.compareAndSet(false, true)) {
            return
        }

        SCOPE.launch {
            try {
                refreshCosmeticsInternal()
            } finally {
                cosmeticsRefreshInProgress.set(false)
            }
        }
    }

    // Covers a command running before the join refresh finishes
    fun refreshCosmeticsIfNeeded() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        if (CosmeticCatalog.ownedIds().isNotEmpty() || CosmeticCatalog.allDefinitions().isNotEmpty()) {
            CosmeticLoadProgress.markLoaded()
            return
        }
        refreshCosmetics()
    }

    private suspend fun refreshCosmeticsInternal() {
        LOGGER.info("Refreshing cosmetics catalog and player data...")
        CosmeticLoadProgress.beginRefresh()

        try {
            runSuspendCatching { CosmeticCatalog.refreshCatalog() }
                .onFailure { LOGGER.error("Cosmetic catalog refresh failed", it) }
            runSuspendCatching { CosmeticCatalog.refreshPlayer() }
                .onFailure { LOGGER.error("Player cosmetics refresh failed", it) }
            //? if >= 1.21.1 {
            runSuspendCatching { CosmeticService.syncLocalActive() }
                .onFailure { LOGGER.error("Local active cosmetics sync failed", it) }
            //?} else {
            /*runCatching { CosmeticSync.applyLocalActiveFromCatalog() }
                .onFailure { LOGGER.error("Local active cosmetics apply failed", it) }*/
            //?}
        } finally {
            CosmeticLoadProgress.onMetadataComplete()
        }
    }
}
