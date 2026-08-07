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
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
//? if >= 1.21.1 {
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticsInitializer
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
//?}
import java.util.concurrent.atomic.AtomicBoolean
import org.polyfrost.polyplus.client.features.AdvancedModCards
import org.polyfrost.polyplus.client.features.DefaultModOrder
import org.polyfrost.polyplus.client.features.DefaultSettings
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.launcher.SessionAccounts
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.privacy.PrivacyGate
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.pets.PetEntities
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.utils.EarlyInitializable

object PolyPlusClient {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)
    private val cosmeticsRefreshInProgress = AtomicBoolean(false)

    private val EXCEPTION_HANDLER = CoroutineExceptionHandler { _, throwable ->
        LOGGER.error("Uncaught exception in PolyPlus coroutine", throwable)
        PolyPlusSentry.capture(throwable)
    }

    @JvmField val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.Default + EXCEPTION_HANDLER)

    @JvmField val JSON = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
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
                // Only 4xx/5xx are failures; 1xx (such as the 101 WebSocket upgrade) and 3xx are not.
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
            runCatching { PolyPlusSentry.capture(error) }
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
        step("adaptive blur") { org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults.initialize() }

        val earlyHooks: List<EarlyInitializable> = buildList {
            //? if >= 1.21.1
            add(CosmeticsInitializer)
        }
        earlyHooks.forEach { hook ->
            step("early init ${hook.javaClass.simpleName}") { hook.earlyInitialize() }
        }

        //? if >= 1.21.1
        step("pet entities") { PetEntities.register() }
        //? if >= 1.21.1
        step("emote wheel keybind") { EmoteWheelKeybind.register() }

        step("websocket") {
            PolyConnection.initialize {
                LOGGER.info("Connected to PolyPlus WebSocket server.")

                SCOPE.launch {
                    PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(ClientPlatform.localPlayerUuid().toString()))
                    //? if >= 1.21.1
                    CosmeticSync.resubscribeVisiblePlayers()
                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                        refreshCosmetics()
                    }
                }
            }
        }

        step("session accounts") { SessionAccounts.capture() }

        step("cosmetics prefetch") { refreshCosmetics() }
        step("commands") { PolyPlusCommands.register() }
        step("host world") { org.polyfrost.polyplus.client.host.HostWorldManager.registerLanPublishHook() }
        //? if >= 1.21.11
        step("panorama") { org.polyfrost.polyplus.client.gui.panorama.CustomPanorama.initialize() }
    }

    /** Full reset (auth, caches, API data). Used when the API URL changes or via `/polyplus refresh`. */
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

            refreshCosmeticsInternal()
        }
    }

    /** Fetches catalog + player cosmetics and applies active loadout (no auth/cache wipe). */
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

    /** Loads cosmetics when the locker is empty but the player is in a world (e.g. command before join refresh finishes). */
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
                .onFailure { LOGGER.error("Cosmetic catalog refresh failed", it); PolyPlusSentry.capture(it) }
            runCatching { CosmeticCatalog.refreshPlayer() }
                .onFailure { LOGGER.error("Player cosmetics refresh failed", it); PolyPlusSentry.capture(it) }
            //? if >= 1.21.1 {
            runCatching { CosmeticService.syncLocalActive() }
                .onFailure { LOGGER.error("Local active cosmetics sync failed", it); PolyPlusSentry.capture(it) }
            //?} else {
            /*runCatching { CosmeticSync.applyLocalActiveFromCatalog() }
                .onFailure { LOGGER.error("Local active cosmetics apply failed", it) }*/
            //?}
        } finally {
            CosmeticLoadProgress.onMetadataComplete()
        }
    }
}
