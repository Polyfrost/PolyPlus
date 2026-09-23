package org.polyfrost.polyplus.client.network.eos

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.http.OidcApi
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EosConnectAuth {
    private val LOGGER = LogManager.getLogger()
    private val LOCK = Mutex()
    private val RANDOM = SecureRandom()
    private val urlSafeNoPad = Base64.getUrlEncoder().withoutPadding()

    private const val CLIENT_ID = "polyplus-eos-connect"
    private const val REDIRECT_URI = "polyplus://oidc-callback"

    // also refreshes the auth of an already logged in bridge
    suspend fun login(bridge: EosSdkBridge): Result<EosProductUserId> = LOCK.withLock {
        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)

        val authorization = OidcApi.authorize(CLIENT_ID, REDIRECT_URI, challenge)
            .getOrElse { return@withLock Result.failure(IllegalStateException("Failed to mint an OIDC authorization code", it)) }

        val token = OidcApi.token(CLIENT_ID, REDIRECT_URI, authorization.code, verifier)
            .getOrElse { return@withLock Result.failure(IllegalStateException("Failed to redeem OIDC authorization code", it)) }

        bridge.connectLogin(token.accessToken)
            .onSuccess { LOGGER.info("Logged into EOS Connect as {}", it) }
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
