package org.polyfrost.polyplus.client.network.eos

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.http.OidcApi

object EosConnectAuth {
    private val LOGGER = LogManager.getLogger()
    private val LOCK = Mutex()
    private val RANDOM = SecureRandom()
    private val urlSafeNoPad = Base64.getUrlEncoder().withoutPadding()

    private const val CLIENT_ID = "polyplus-eos-connect"
    private const val REDIRECT_URI = "polyplus://oidc-callback"

    private var loginResult: Result<EosProductUserId>? = null

    suspend fun ensureLoggedIn(bridge: EosSdkBridge): Result<EosProductUserId> {
        bridge.localUser?.let { return Result.success(it) }

        return LOCK.withLock {
            bridge.localUser?.let { return@withLock Result.success(it) }
            loginResult?.let { cached -> if (cached.isSuccess) return@withLock cached }

            val result = login(bridge)
            loginResult = result
            result
        }
    }

    suspend fun forceLogin(bridge: EosSdkBridge): Result<EosProductUserId> = LOCK.withLock {
        val result = login(bridge)
        loginResult = result
        result
    }

    private suspend fun login(bridge: EosSdkBridge): Result<EosProductUserId> {
        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)

        val authorization = OidcApi.authorize(CLIENT_ID, REDIRECT_URI, challenge)
            .getOrElse {
                LOGGER.error("Failed to mint an OIDC authorization code", it)
                return Result.failure(it)
            }

        val token = OidcApi.token(CLIENT_ID, REDIRECT_URI, authorization.code, verifier)
            .getOrElse {
                LOGGER.error("Failed to redeem OIDC authorization code", it)
                return Result.failure(it)
            }

        return bridge.connectLogin(token.accessToken)
            .onSuccess { LOGGER.info("Logged into EOS Connect as {}", it) }
            .onFailure { LOGGER.error("EOS Connect login failed", it) }
    }

    suspend fun reset() {
        LOCK.withLock { loginResult = null }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return urlSafeNoPad.encodeToString(bytes)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return urlSafeNoPad.encodeToString(digest)
    }
}
