package org.polyfrost.polyplus.client.discord

import de.jcm.discordgamesdk.Core
import de.jcm.discordgamesdk.CreateParams
import de.jcm.discordgamesdk.activity.Activity
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

object DiscordPresence {
    private const val CLIENT_ID = 1436363089456140459
    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME} Discord Rich Presence")

    private var startTime: Instant? = null

    val running: AtomicBoolean = AtomicBoolean(false)
    var core: Core? = null

    fun initialize() {
        this.startTime = Instant.now()
        start()
    }

    fun start() {
        if (!PolyPlusConfig.isDiscordEnabled || running.getAndSet(true)) {
            return
        }

        PolyPlusClient.SCOPE.launch {
            tick().onFailure { throwable ->
                LOGGER.error("An error occurred while running Discord RPC!", throwable)
            }

            running.set(false)
        }
    }

    fun stop() {
        running.set(false)
    }

    private suspend fun tick(): Result<Unit> = runCatching {
        val library = DiscordSDK.download()
            ?: throw IllegalStateException("Failed to download Discord Game SDK")

        Core.init(library)

        val params = createSdkParams()
        val core = Core(params)
        this.core = core

        try {
            while (PolyPlusConfig.isDiscordEnabled && this.running.get()) {
                core.runCallbacks()
                delay(Duration.ofMillis(16))
                updateActivity(core)
            }

            core.activityManager().clearActivity()
        } finally {
            try {
                core.close()
            } catch (t: Throwable) {
                LOGGER.warn("Failed to close Discord Core cleanly", t)
            }

            try {
                params.close()
            } catch (t: Throwable) {
                LOGGER.warn("Failed to close Discord CreateParams cleanly", t)
            }

            this.core = null
        }
    }

    private fun createSdkParams(): CreateParams {
        return CreateParams().apply {
            clientID = CLIENT_ID
            setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD)
            registerEventHandler(PolyPlusDiscord())
        }
    }

    /**
     * TODO: Dynamic presence updates
     */
    private fun updateActivity(core: Core) = runCatching {
        val startedAt = startTime ?: Instant.now()

        runCatching {
            core.activityManager().updateActivity(Activity().apply {
                state = "In Game"
                details = "Playing the game"
                timestamps().start = startedAt
                assets().largeText = "WOWOWOWOW"
                assets().largeImage = "ferris"
            })
        }.onFailure { throwable ->
            LOGGER.debug("Failed to update Discord activity", throwable)
        }
    }
}
