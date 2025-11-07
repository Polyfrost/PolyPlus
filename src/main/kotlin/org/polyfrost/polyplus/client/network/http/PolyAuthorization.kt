package org.polyfrost.polyplus.client.network.http

import dev.deftu.omnicore.api.client.client
import dev.deftu.omnicore.api.client.player.playerName
import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.AuthResponse
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object PolyAuthorization {
    private val LOGGER = LogManager.getLogger()
    private val LOCK = Mutex()

    private var cachedResponse: AuthResponse? = null
    private var currentJob: Deferred<AuthResponse>? = null

    suspend fun current(): String {
        val token = LOCK.withLock { cachedResponse?.token }
        return token ?: refresh()
    }

    suspend fun refresh(): String {
        currentJob?.let { return it.await().token }

        val lockedJob = LOCK.withLock {
            currentJob?.let { return it.await().token }

            val job = PolyPlusClient.SCOPE.async(start = CoroutineStart.LAZY) {
                authorize().also {
                    LOCK.withLock {
                        cachedResponse = it
                        currentJob = null
                    }
                }
            }

            currentJob = job
            job.start()
            job
        }

        return lockedJob.await().token
    }

    private suspend fun authorize(): AuthResponse {
        val serverId = generateServerId()
        authorizeSessionService(serverId)
        val response = PolyPlusClient.HTTP
            .post("${PolyPlusConfig.apiUrl}/account/login?server_id=$serverId&username=${playerName}")
            .body<AuthResponse>()
        LOGGER.info("Successfully authorized as $playerName")
        return response
    }

    private fun generateServerId(): String {
        val chars = ('a'..'z') + ('A'..'Z')
        return (0..<32).joinToString("") { "${chars.random()}" }
    }

    private fun authorizeSessionService(serverId: String) {
        try {
            //#if MC >= 1.20.4
            //$$ val profile = client.uuid
            //#else
            val profile = client.session.profile
            //#endif

            client.sessionService.joinServer(profile, client.session.token, serverId)
        } catch (e: Exception) {
            LOGGER.error("Failed to authenticate with Mojang", e)
        }
    }
}