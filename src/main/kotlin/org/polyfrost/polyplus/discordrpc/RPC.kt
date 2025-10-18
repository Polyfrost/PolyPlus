package org.polyfrost.polyplus.discordrpc

import de.jcm.discordgamesdk.Core
import de.jcm.discordgamesdk.CreateParams
import de.jcm.discordgamesdk.DiscordEventAdapter
import de.jcm.discordgamesdk.activity.Activity
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.PolyPlus.Companion.logger
import org.polyfrost.polyplus.client.Config.rpcEnabled
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

object RPC {
    val running: AtomicBoolean = AtomicBoolean(false)

    var core: Core? = null

    fun start() {
        if (!rpcEnabled || running.getAndSet(true)) return
        PolyPlus.scope.launch { run().onFailure {
            logger.warning("Failed to start Discord RPC: ${it.message}")
            running.set(false)
        } }
    }

    fun stop() {
        running.set(false)
    }

    suspend fun run(): Result<Unit> = runCatching {
        val library = DownloadSDK.download() ?: return Result.failure(Exception("Failed to download Discord Game SDK"))

        Core.init(library)

        val params = CreateParams()
        params.clientID = 1426999264633946334; // get id
        params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD)

        params.registerEventHandler(object : DiscordEventAdapter() {
            override fun onActivityJoin(secret: String?) {
                // print?
            }
        })

        val core = Core(params)
        this.core = core
        while (rpcEnabled && this.running.get()) {
            core.runCallbacks()
            delay(Duration.ofMillis(16))
            update(core)
        }
        core.activityManager().clearActivity()
    }

    fun update(core: Core) = runCatching {
        core.activityManager().updateActivity(Activity().apply {
            state = "In Game"
            details = "Playing the game"
            timestamps().start = PolyPlus.launch
            assets().largeText = "WOWOWOWOW"
            assets().largeImage = "ferris"
        })
    }
}