package org.polyfrost.polyplus.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
//? if >= 1.21.1 {
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticsInitializer
import org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults
//?}
import java.util.concurrent.atomic.AtomicBoolean
import org.polyfrost.polyplus.client.features.AdvancedModCards
import org.polyfrost.polyplus.client.features.DefaultModOrder
import org.polyfrost.polyplus.client.features.DefaultSettings
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.launcher.SessionAccounts
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.privacy.PrivacyGate
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.pets.PetEntities
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GlobalChatRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.utils.EarlyInitializable

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

                val text = runCatching { response.bodyAsText() }.getOrDefault("")
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
        step("default settings") { DefaultSettings.initialize() }
        step("default mod order") { DefaultModOrder.initialize() }
        step("advanced mod cards") { AdvancedModCards.initialize() }
        step("onboarding") { OnboardingFeatures.initialize() }
        step("adaptive blur") { AdaptiveBlurDefaults.initialize() }

        val earlyHooks: List<EarlyInitializable> = buildList {
            //? if >= 1.21.1
            add(CosmeticsInitializer)
            add(FriendsRepository)
            add(GroupsRepository)
            // Global chat is disabled for now.
            // add(GlobalChatRepository)
            add(SessionsRepository)
            add(P2PSessionManager)
        }
        earlyHooks.forEach { hook ->
            step("early init ${hook.javaClass.simpleName}") { hook.earlyInitialize() }
        }

        //? if >= 1.21.1
        step("pet entities") { PetEntities.register() }
        step("social overlay keybind") { SocialOverlay.registerKeybind() }
        step("vanilla menu button") { org.polyfrost.polyplus.client.gui.VanillaMenuButton.register() }

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
        step("panorama") { org.polyfrost.polyplus.client.gui.panorama.CustomPanorama.initialize() }
    }

    // Full reset of auth caches and API data
    fun refresh() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        LOGGER.info("Refreshing PolyPlus Client...")

        SCOPE.launch {
            runCatching { PolyAuthorization.reset() }

            runCatching {
                CosmeticCatalog.reset()
                CosmeticAssetCache.reset()
            }

            runCatching { PolyConnection.reconnect() }
            runCatching { P2PSessionManager.reconnect() }

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
            runCatching { CosmeticCatalog.refreshCatalog() }
                .onFailure { LOGGER.error("Cosmetic catalog refresh failed", it) }
            runCatching { CosmeticCatalog.refreshPlayer() }
                .onFailure { LOGGER.error("Player cosmetics refresh failed", it) }
            //? if >= 1.21.1 {
            runCatching { CosmeticService.syncLocalActive() }
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
