package org.polyfrost.polyplus.client.network.http

import io.ktor.client.call.body
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.AuthResponse
import org.polyfrost.polyplus.client.utils.ClientPlatform
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

    suspend fun reset() {
        LOCK.withLock {
            cachedResponse = null
            currentJob = null
        }
    }

    private suspend fun authorize(): AuthResponse {
        val serverId = generateServerId()
        authorizeSessionService(serverId)
        val playerName = ClientPlatform.localPlayerName()
        val response = PolyPlusClient.HTTP
            .post("${PolyPlusConfig.apiUrl}/account/login?server_id=$serverId&username=$playerName")
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
            val client = Minecraft.getInstance()
            client.services().sessionService.joinServer(
                ClientPlatform.localPlayerUuid(),
                client.user.accessToken,
                serverId,
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to authenticate with Mojang", e)
        }
    }
}
