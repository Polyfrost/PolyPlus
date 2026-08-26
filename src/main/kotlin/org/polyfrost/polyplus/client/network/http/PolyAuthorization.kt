package org.polyfrost.polyplus.client.network.http

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.network.http.responses.AuthResponse
import org.polyfrost.polyplus.client.privacy.OnlineServicesDisabledException
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object PolyAuthorization {
    private val LOGGER = LogManager.getLogger()
    private val LOCK = Mutex()

    private const val TOKEN_TTL_MS = 2 * 60 * 60 * 1000L
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L
    private const val LOGIN_GATE_TIMEOUT_MS = 30 * 1000L

    private var cachedResponse: AuthResponse? = null
    private var cachedExpiresAtMs = 0L
    private var cachedProfileId: UUID? = null
    private var currentJob: Deferred<Authorized>? = null
    private var currentJobProfileId: UUID? = null

    suspend fun current(): String {
        requireOnlineServices()

        val profileId = localProfileId()
        val token = LOCK.withLock {
            cachedResponse?.token?.takeIf {
                System.currentTimeMillis() < cachedExpiresAtMs && cachedProfileId == profileId
            }
        }
        return token ?: refresh()
    }

    suspend fun refresh(): String {
        requireOnlineServices()

        val profileId = localProfileId()
        val job = LOCK.withLock {
            currentJob?.takeIf { currentJobProfileId == profileId } ?: startAuthorization(profileId)
        }
        return job.await().response.token
    }

    suspend fun reset() {
        LOCK.withLock {
            cachedResponse = null
            cachedExpiresAtMs = 0L
            cachedProfileId = null
            currentJob = null
            currentJobProfileId = null
        }
    }

    private fun requireOnlineServices() {
        if (!PrivacyConsent.allowsOnlineServices()) {
            throw OnlineServicesDisabledException("${PolyPlusConfig.apiUrl}/account/login")
        }
    }

    private fun localProfileId(): UUID? =
        runCatching { Minecraft.getInstance().user.profileId }.getOrNull()

    // Must be called with LOCK held
    private fun startAuthorization(profileId: UUID?): Deferred<Authorized> {
        lateinit var job: Deferred<Authorized>
        job = PolyPlusClient.SCOPE.async(start = CoroutineStart.LAZY) {
            try {
                authorize().also {
                    LOCK.withLock {
                        cachedResponse = it.response
                        cachedProfileId = it.profileId
                        cachedExpiresAtMs = System.currentTimeMillis() + TOKEN_TTL_MS - REFRESH_MARGIN_MS
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    LOCK.withLock {
                        if (currentJob === job) {
                            currentJob = null
                            currentJobProfileId = null
                        }
                    }
                }
            }
        }
        currentJob = job
        currentJobProfileId = profileId
        job.start()
        return job
    }

    private suspend fun authorize(): Authorized = MinecraftLoginGate.whileNotLoggingIn(LOGIN_GATE_TIMEOUT_MS) {
        val user = Minecraft.getInstance().user
        val profileId = user.profileId
        val playerName = user.name
        val serverId = generateServerId()
        authorizeSessionService(serverId, profileId, user.accessToken)
        val response = PolyPlusClient.HTTP
            .post("${PolyPlusConfig.apiUrl}/account/login") {
                expectSuccess = true
                parameter("server_id", serverId)
                parameter("username", playerName)
                parameter("client_version", PolyPlusConstants.VERSION)
                parameter("minecraft_version", minecraftVersion())
                parameter("loader", LOADER)
                parameter("os", System.getProperty("os.name") ?: "unknown")
                parameter("os_version", System.getProperty("os.version") ?: "unknown")
                parameter("java_version", System.getProperty("java.version") ?: "unknown")
            }
            .bodyOrThrow<AuthResponse>()
        LOGGER.info("Successfully authorized as $playerName")
        Authorized(profileId, response)
    }

    private const val LOADER =
        //? if fabric {
        "fabric"
    //?} else {
    /*"neoforge"
    *///?}

    private fun minecraftVersion(): String =
        runCatching { HostWorldManager.clientVersionName }.getOrDefault("unknown")

    private fun generateServerId(): String {
        val chars = ('a'..'z') + ('A'..'Z')
        return (0..<32).joinToString("") { "${chars.random()}" }
    }

    private fun authorizeSessionService(serverId: String, profileId: UUID, accessToken: String) {
        try {
            Minecraft.getInstance().
                //?if >= 1.21.10 {
             services().sessionService
            //?} else {
                /*minecraftSessionService
            *///?}
                .joinServer(profileId, accessToken, serverId)
        } catch (e: Exception) {
            LOGGER.error("Failed to authenticate with Mojang", e)
        }
    }

    private class Authorized(val profileId: UUID, val response: AuthResponse)
}
