package org.polyfrost.polyplus.network.plus

import dev.deftu.omnicore.api.client.client
import dev.deftu.omnicore.api.client.player.uuid
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.PolyPlus.Companion.logger
import org.polyfrost.polyplus.client.Config
import org.polyfrost.polyplus.network.plus.responses.AuthResponse
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object Auth {
    val authLock = Mutex()
    var authRes: AuthResponse? = null
    var authJob: Deferred<AuthResponse>? = null

    suspend fun getToken(): String {
        val token = authLock.withLock { authRes?.token }
        return token ?: refreshToken()
    }

    suspend fun refreshToken(): String {
        authJob?.let { return it.await().token }

        val lockedJob = authLock.withLock {
            authJob?.let { return it.await().token }

            val job = PolyPlus.scope.async(start = CoroutineStart.LAZY) {
                runAuth().also {
                    authLock.withLock {
                        authRes = it
                        authJob = null
                    }
                }
            }

            authJob = job
            job.start()
            job
        }

        return lockedJob.await().token
    }

    suspend inline fun HttpClient.authRequest(method: HttpMethod, url: String, noinline builder: HttpRequestBuilder.() -> Unit): Result<HttpResponse> = runCatching {
        var response = this.request {
            apply(builder)
            url(url)
            this.method = method
            bearerAuth(getToken())
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            response = this.request {
                apply(builder)
                url(url)
                this.method = method
                bearerAuth(refreshToken())
            }
        }

        return@runCatching response
    }

    suspend fun runAuth() = withContext(Dispatchers.IO) {
        val serverId = genServerId()
        mojangAuth(serverId)
        val auth: AuthResponse = PolyPlus.client.post(Url(Config.apiUrl + "account/login?server_id=$serverId&username=${client.session.username}")).body()
        logger.info("Successfully authenticated with PolyPlus")
        auth
    }

    suspend inline fun <reified T> HttpClient.authRequest(method: HttpMethod, url: String) = runCatching {
        logger.info("Requesting: $url")
        val response = this.authRequest(method, url) {}
        response.getOrElse { return Result.failure<T>(it) }.body<T>()
    }
}

fun genServerId(): String {
    val chars = ('a'..'z') + ('A'..'Z')
    return (0..<32).joinToString("") { "${chars.random()}" }
}

fun mojangAuth(serverId: String) {
    try {
        //#if MC >= 1.20.4
        //$$ val profile = client.uuid
        //#else
        val profile = client.session.profile
        //#endif
        client.sessionService.joinServer(profile, client.session.token, serverId)
    } catch (e: Exception) {
        logger.warning("Failed to authenticate with Mojang: ${e.message}")
    }
}