package org.polyfrost.polyplus.client.party

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object VoicePassVerifier {
    private val LOGGER = LogManager.getLogger()

    const val AUDIENCE = "polyplus-party-voice"
    private const val REFETCH_MS = 60_000L

    @Serializable
    private data class Jwk(val kid: String? = null, val kty: String? = null, val n: String? = null, val e: String? = null)

    @Serializable
    private data class Jwks(val keys: List<Jwk> = emptyList())

    private val keys = ConcurrentHashMap<String, PublicKey>()
    private val fetchLock = Mutex()
    @Volatile private var lastFetchMs = 0L

    private val decoder = Base64.getUrlDecoder()

    private fun issuer(): String = PolyPlusConfig.apiUrl.toString().trimEnd('/')

    suspend fun verify(pass: String, partyId: String, remotePuid: String): Boolean = runSuspendCatching {
        val parts = pass.split('.')
        if (parts.size != 3) return@runSuspendCatching false
        val header = PolyPlusClient.JSON.decodeFromString(JsonObject.serializer(), String(decoder.decode(parts[0])))
        if (header["alg"]?.jsonPrimitive?.content != "RS256") return@runSuspendCatching false
        val kid = header["kid"]?.jsonPrimitive?.content ?: return@runSuspendCatching false
        val key = keyFor(kid) ?: return@runSuspendCatching false
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initVerify(key)
            update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        }
        if (!signature.verify(decoder.decode(parts[2]))) return@runSuspendCatching false
        val claims = PolyPlusClient.JSON.decodeFromString(JsonObject.serializer(), String(decoder.decode(parts[1])))
        val audience = claims["aud"]?.jsonPrimitive?.content
        val issuer = claims["iss"]?.jsonPrimitive?.content?.trimEnd('/')
        val expires = claims["exp"]?.jsonPrimitive?.longOrNull ?: 0L
        val party = claims["party"]?.jsonPrimitive?.content
        val puid = claims["puid"]?.jsonPrimitive?.content
        audience == AUDIENCE &&
            issuer == issuer() &&
            expires * 1000L > System.currentTimeMillis() &&
            party.equals(partyId, ignoreCase = true) &&
            puid.equals(remotePuid, ignoreCase = true)
    }.onFailure { LOGGER.warn("Rejected a party voice pass", it) }.getOrDefault(false)

    private suspend fun keyFor(kid: String): PublicKey? {
        keys[kid]?.let { return it }
        fetchLock.withLock {
            keys[kid]?.let { return it }
            val now = System.currentTimeMillis()
            if (now - lastFetchMs < REFETCH_MS && lastFetchMs != 0L) return null
            lastFetchMs = now
            val jwks = runSuspendCatching {
                PolyPlusClient.HTTP.get("${issuer()}/.well-known/jwks.json").body<Jwks>()
            }.onFailure { LOGGER.warn("Could not fetch the backend's signing keys", it) }.getOrNull() ?: return null
            jwks.keys.forEach { jwk ->
                val id = jwk.kid ?: return@forEach
                if (jwk.kty != "RSA" || jwk.n == null || jwk.e == null) return@forEach
                val spec = RSAPublicKeySpec(BigInteger(1, decoder.decode(jwk.n)), BigInteger(1, decoder.decode(jwk.e)))
                keys[id] = KeyFactory.getInstance("RSA").generatePublic(spec)
            }
        }
        return keys[kid]
    }

    fun expiresAt(pass: String): Long? = runCatching {
        val claims = PolyPlusClient.JSON.decodeFromString(JsonObject.serializer(), String(decoder.decode(pass.split('.')[1])))
        claims["exp"]?.jsonPrimitive?.longOrNull?.times(1000L)
    }.getOrNull()
}
