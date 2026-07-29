package org.polyfrost.polyplus.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
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
//?}
import java.util.concurrent.atomic.AtomicBoolean
import org.polyfrost.polyplus.client.features.DefaultSettings
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.launcher.SessionAccounts
import org.polyfrost.polyplus.client.network.http.PolyAuthorization
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.privacy.PrivacyGate
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
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
    }

    fun initialize() {
        PolyPlusSentry.initialize()
        SCOPE.launch(Dispatchers.IO) { PolyPlusCrashLogUploader.uploadPending() }
        PolyPlusConfig.preload()
        PrivacyEnforcement.syncConfig()
        DefaultSettings.initialize()
        OnboardingFeatures.initialize()
        org.polyfrost.polyplus.client.features.AdaptiveBlurDefaults.initialize()

        val earlyHooks: List<EarlyInitializable> = buildList {
            //? if >= 1.21.1
            add(CosmeticsInitializer)
        }
        earlyHooks.forEach(EarlyInitializable::earlyInitialize)

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

        SessionAccounts.capture()

        refreshCosmetics()
        PolyPlusCommands.register()
        org.polyfrost.polyplus.client.host.HostWorldManager.registerLanPublishHook()
        //? if >= 1.21.11
        org.polyfrost.polyplus.client.gui.panorama.CustomPanorama.initialize()
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
