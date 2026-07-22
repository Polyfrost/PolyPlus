package org.polyfrost.polyplus.client.launcher

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object MicrosoftAuth {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    private const val CLIENT_ID = "9419b7ee-1448-4d1b-b52a-550d8f36ab56"
    private const val SCOPES = "XboxLive.SignIn XboxLive.offline_access"
    private const val AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

    private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L

    private val HTTP get() = PolyPlusClient.HTTP
    private val B64URL = Base64.getUrlEncoder().withoutPadding()
    private val ERR_JSON = Json { ignoreUnknownKeys = true }

    class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val expiresIn: Long,
        val interval: Long,
        val message: String,
    )

    class MicrosoftLoginSession internal constructor(
        val browserAuthUrl: String,
        val userCode: String,
        val verificationUri: String,
        internal val server: HttpServer,
        internal val redirectUri: String,
        internal val verifier: String,
        internal val device: DeviceCode,
        internal val codeResult: CompletableDeferred<String>,
    )

    suspend fun beginLogin(): MicrosoftLoginSession {
        val verifier = randomToken()
        val challenge = pkceChallenge(verifier)
        val csrfState = randomToken()

        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val redirectUri = "http://127.0.0.1:${server.address.port}"
        val codeResult = CompletableDeferred<String>()

        server.createContext("/") { exchange -> handleRedirect(exchange, csrfState, codeResult) }
        server.executor = null
        server.start()

        val device = try {
            beginDeviceLogin()
        } catch (e: Throwable) {
            server.stop(0)
            throw e
        }

        return MicrosoftLoginSession(
            browserAuthUrl = authorizeUrl(redirectUri, csrfState, challenge),
            userCode = device.userCode,
            verificationUri = device.verificationUri,
            server = server,
            redirectUri = redirectUri,
            verifier = verifier,
            device = device,
            codeResult = codeResult,
        )
    }

    suspend fun finishLogin(session: MicrosoftLoginSession): LauncherAccountStore.StoredAccount {
        try {
            val timeout = session.device.expiresIn.coerceAtMost(LOGIN_TIMEOUT_MS / 1000).seconds
            val msa = withTimeout(timeout) { raceLogin(session) }
            return accountFromMsa(msa)
        } finally {
            session.server.stop(0)
        }
    }

    fun cancelLogin(session: MicrosoftLoginSession) {
        session.server.stop(0)
    }

    private suspend fun raceLogin(session: MicrosoftLoginSession): MsaToken = coroutineScope {
        val browser = async {
            runCatching {
                val code = session.codeResult.await()
                exchangeAuthCode(code, session.redirectUri, session.verifier)
            }
        }
        val device = async { runCatching { pollDeviceToken(session.device) } }
        try {
            var browserDone = false
            var deviceDone = false
            var lastError: Throwable? = null
            while (true) {
                val result: Result<MsaToken> = select {
                    if (!browserDone) browser.onAwait { browserDone = true; it }
                    if (!deviceDone) device.onAwait { deviceDone = true; it }
                }
                result.onSuccess { return@coroutineScope it }
                result.onFailure { lastError = it }
                if (browserDone && deviceDone) break
            }
            throw lastError ?: MicrosoftAuthErrors.deviceExpired()
        } finally {
            browser.cancel()
            device.cancel()
        }
    }

    suspend fun refreshAccount(account: LauncherAccountStore.StoredAccount): LauncherAccountStore.StoredAccount {
        val msa = refreshMsaToken(account.refreshToken)
        return accountFromMsa(msa)
    }

    private suspend fun refreshMsaToken(refreshToken: String): MsaToken {
        if (refreshToken.isBlank()) throw MicrosoftAuthErrors.forStep(MsaAuthStep.AuthCodeExchange)
        val response = try {
            HTTP.post(TOKEN_URL) {
                accept(ContentType.Application.Json)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", CLIENT_ID)
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                            append("scope", SCOPES)
                        },
                    ),
                )
            }
        } catch (e: IOException) {
            throw MicrosoftAuthErrors.network(e)
        } catch (e: java.nio.channels.UnresolvedAddressException) {
            throw MicrosoftAuthErrors.network(e)
        }
        if (!response.status.isSuccess()) {
            throw MicrosoftAuthErrors.forStep(MsaAuthStep.AuthCodeExchange)
        }
        val body: MsaTokenResponse = response.body()
        return MsaToken(body.accessToken, body.refreshToken.ifBlank { refreshToken }, body.expiresIn, Instant.now())
    }

    private suspend fun beginDeviceLogin(): DeviceCode {
        val response = try {
            HTTP.post(DEVICE_CODE_URL) {
                accept(ContentType.Application.Json)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", CLIENT_ID)
                            append("scope", SCOPES)
                        },
                    ),
                )
            }
        } catch (e: IOException) {
            throw MicrosoftAuthErrors.network(e)
        } catch (e: java.nio.channels.UnresolvedAddressException) {
            throw MicrosoftAuthErrors.network(e)
        }
        if (!response.status.isSuccess()) {
            throw MicrosoftAuthErrors.forStep(MsaAuthStep.DeviceCodeRequest)
        }
        val body: DeviceCodeResponse = response.body()
        return DeviceCode(
            userCode = body.userCode,
            verificationUri = body.verificationUri,
            deviceCode = body.deviceCode,
            expiresIn = body.expiresIn,
            interval = body.interval,
            message = body.message,
        )
    }

    private suspend fun pollDeviceToken(device: DeviceCode): MsaToken {
        var intervalSeconds = device.interval.coerceAtLeast(5)
        while (true) {
            val response = try {
                HTTP.post(TOKEN_URL) {
                    accept(ContentType.Application.Json)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", DEVICE_GRANT_TYPE)
                                append("client_id", CLIENT_ID)
                                append("device_code", device.deviceCode)
                            },
                        ),
                    )
                }
            } catch (e: IOException) {
                throw MicrosoftAuthErrors.network(e)
            } catch (e: java.nio.channels.UnresolvedAddressException) {
                throw MicrosoftAuthErrors.network(e)
            }
            if (response.status.isSuccess()) {
                val body: MsaTokenResponse = response.body()
                return MsaToken(body.accessToken, body.refreshToken, body.expiresIn, Instant.now())
            }
            val error = runCatching {
                ERR_JSON.decodeFromString(OAuthErrorResponse.serializer(), response.bodyAsText())
            }.getOrNull()
            when (error?.error) {
                "authorization_pending" -> delay(intervalSeconds.seconds)
                "slow_down" -> {
                    intervalSeconds += 5
                    delay(intervalSeconds.seconds)
                }
                "expired_token" -> throw MicrosoftAuthErrors.deviceExpired()
                null -> throw MicrosoftAuthErrors.forStep(MsaAuthStep.DeviceCodePoll)
                else -> throw MicrosoftAuthErrors.deviceFailed(error.errorDescription ?: error.error)
            }
        }
    }

    private fun authorizeUrl(redirectUri: String, state: String, challenge: String): String {
        val params = Parameters.build {
            append("client_id", CLIENT_ID)
            append("response_type", "code")
            append("redirect_uri", redirectUri)
            append("response_mode", "query")
            append("scope", SCOPES)
            append("state", state)
            append("code_challenge", challenge)
            append("code_challenge_method", "S256")
            append("prompt", "select_account")
        }
        return "$AUTHORIZE_URL?${params.formUrlEncode()}"
    }

    private fun handleRedirect(
        exchange: HttpExchange,
        expectedState: String,
        result: CompletableDeferred<String>,
    ) {
        val query = exchange.requestURI.rawQuery?.let(::parseQuery).orEmpty()
        val page: RedirectPage = when {
            query["error"] != null -> {
                result.completeExceptionally(
                    MicrosoftAuthException(
                        "The Microsoft sign-in was cancelled or rejected in your browser (${query["error"]}).",
                        listOf(
                            "Start the sign-in again",
                            "Complete the Microsoft sign-in in your browser without closing the window",
                            "If your browser blocked the redirect back, allow it and try once more",
                        ),
                    ),
                )
                RedirectPage.FAILED
            }
            query["state"] != null && query["state"] != expectedState -> RedirectPage.FAILED
            query["code"] != null -> {
                result.complete(query.getValue("code"))
                RedirectPage.SUCCESS
            }
            else -> RedirectPage.WAITING
        }
        respond(exchange, page)
    }

    private suspend fun exchangeAuthCode(
        code: String,
        redirectUri: String,
        verifier: String,
    ): MsaToken {
        val response = HTTP.post(TOKEN_URL) {
            accept(ContentType.Application.Json)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", CLIENT_ID)
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", redirectUri)
                        append("code_verifier", verifier)
                        append("scope", SCOPES)
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw MicrosoftAuthErrors.forStep(MsaAuthStep.AuthCodeExchange)
        }
        val body: MsaTokenResponse = response.body()
        return MsaToken(body.accessToken, body.refreshToken, body.expiresIn, Instant.now())
    }

    private suspend fun accountFromMsa(msa: MsaToken): LauncherAccountStore.StoredAccount {
        val xblToken = xblAuthenticate("d=${msa.accessToken}")
        val xsts = xstsAuthorize(xblToken)
        val xstsToken = xsts.token ?: throw MicrosoftAuthErrors.forStep(MsaAuthStep.XstsAuthorize)
        val uhs = xsts.displayClaims?.xui?.firstOrNull()?.uhs
            ?: throw MicrosoftAuthErrors.forStep(MsaAuthStep.XstsAuthorize)

        val mcToken = minecraftLogin(uhs, xstsToken)
        minecraftEntitlements(mcToken)
        val profile = minecraftProfile(mcToken)

        return LauncherAccountStore.StoredAccount(
            id = normalizeUuid(profile.id).toString(),
            username = profile.name,
            accessToken = mcToken,
            refreshToken = msa.refreshToken,
            expires = msa.obtainedAt.plusSeconds(msa.expiresIn).toString(),
            kind = "microsoft",
        )
    }

    private suspend fun xblAuthenticate(rpsTicket: String): String {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", rpsTicket)
            }
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val response = HTTP.post("https://user.auth.xboxlive.com/user/authenticate") {
            accept(ContentType.Application.Json)
            header("x-xbl-contract-version", "1")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val res = parseXboxResponse(response, MsaAuthStep.XblAuthenticate)
        return res.token ?: throw MicrosoftAuthErrors.forStep(MsaAuthStep.XblAuthenticate)
    }

    private suspend fun xstsAuthorize(userToken: String): XboxTokenResponse {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                putJsonArray("UserTokens") { add(userToken) }
            }
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }
        val response = HTTP.post("https://xsts.auth.xboxlive.com/xsts/authorize") {
            accept(ContentType.Application.Json)
            header("x-xbl-contract-version", "1")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return parseXboxResponse(response, MsaAuthStep.XstsAuthorize)
    }

    private suspend fun parseXboxResponse(response: HttpResponse, step: MsaAuthStep): XboxTokenResponse {
        val text = response.bodyAsText()
        if (response.status.isSuccess()) {
            return ERR_JSON.decodeFromString(XboxTokenResponse.serializer(), text)
        }
        val xerr = runCatching {
            ERR_JSON.decodeFromString(XboxErrorResponse.serializer(), text).xErr
        }.getOrNull()
        if (xerr != null && xerr != 0L) {
            throw MicrosoftAuthErrors.forXerr(xerr)
        }
        throw MicrosoftAuthErrors.forService(step, response.status.value)
    }

    private suspend fun minecraftLogin(uhs: String, xstsToken: String): String {
        val body = buildJsonObject { put("identityToken", "XBL3.0 x=$uhs;$xstsToken") }
        val response = HTTP.post("https://api.minecraftservices.com/authentication/login_with_xbox") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw MicrosoftAuthErrors.forService(MsaAuthStep.MinecraftToken, response.status.value)
        }
        return response.body<MinecraftTokenResponse>().accessToken
    }

    private suspend fun minecraftEntitlements(token: String) {
        runCatching {
            HTTP.get("https://api.minecraftservices.com/entitlements/mcstore") {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }.onFailure { LOGGER.debug("entitlements check failed", it) }
    }

    private suspend fun minecraftProfile(token: String): MinecraftProfile {
        val response = HTTP.get("https://api.minecraftservices.com/minecraft/profile") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw MicrosoftAuthErrors.forService(MsaAuthStep.MinecraftProfile, response.status.value)
        }
        return response.body()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return B64URL.encodeToString(bytes)
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return B64URL.encodeToString(digest)
    }

    private fun normalizeUuid(id: String): UUID = runCatching { UUID.fromString(id) }.getOrElse {
        val hex = id.replace("-", "")
        UUID.fromString(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20)}",
        )
    }

    private fun parseQuery(raw: String): Map<String, String> = raw.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
        val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
        key to value
    }.toMap()

    private enum class RedirectPage(val accent: String, val glyph: String, val heading: String, val detail: String) {
        WAITING(
            "#2567d8",
            """<div class="ring"></div>""",
            "Signing you in",
            "Finish signing in with Microsoft in this window.",
        ),
        SUCCESS(
            "#45de2b",
            """<svg viewBox="0 0 24 24" class="glyph"><path d="M20 6 9 17l-5-5"/></svg>""",
            "You're signed in!",
            "You can close this tab and return to OneClient.",
        ),
        FAILED(
            "#ff0000",
            """<svg viewBox="0 0 24 24" class="glyph"><path d="M18 6 6 18M6 6l12 12"/></svg>""",
            "Sign-in failed",
            "Something went wrong. Close this tab and try again from OneClient.",
        ),
    }

    private fun respond(exchange: HttpExchange, page: RedirectPage) {
        val body = """
            <!doctype html><html lang="en"><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>OneClient</title>
            <style>
            *{box-sizing:border-box;margin:0;padding:0}
            :root{--accent:${page.accent}}
            html,body{height:100%}
            body{
              font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
              color:#111827;
              background:#ffffff;
              display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;
            }
            .card{
              position:relative;width:100%;max-width:420px;text-align:center;
              padding:44px 36px 40px;border-radius:20px;
              background:#ffffff;
              border:1px solid #e5e7eb;
              box-shadow:0 10px 30px rgba(17,24,39,.10);
              animation:rise .5s cubic-bezier(.2,.8,.2,1) both;
            }
            .badge{width:76px;height:76px;margin:0 auto 26px;border-radius:50%;
              display:flex;align-items:center;justify-content:center;
              background:color-mix(in srgb, var(--accent) 12%, transparent);
              border:1px solid color-mix(in srgb, var(--accent) 40%, transparent)}
            .glyph{width:34px;height:34px;fill:none;stroke:var(--accent);stroke-width:2.4;
              stroke-linecap:round;stroke-linejoin:round;animation:pop .4s .15s cubic-bezier(.2,1.4,.3,1) both}
            .ring{width:34px;height:34px;border-radius:50%;
              border:3px solid rgba(37,103,216,.22);border-top-color:#2567d8;animation:spin .8s linear infinite}
            h1{font-size:21px;font-weight:600;color:#111827;margin-bottom:10px}
            p{font-size:14px;line-height:1.55;color:#6b7280}
            @keyframes spin{to{transform:rotate(360deg)}}
            @keyframes rise{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
            @keyframes pop{from{opacity:0;transform:scale(.6)}to{opacity:1;transform:none}}
            </style></head>
            <body>
              <main class="card">
                <div class="badge">${page.glyph}</div>
                <h1>${page.heading}</h1>
                <p>${page.detail}</p>
              </main>
            </body></html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        runCatching {
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private class MsaToken(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val obtainedAt: Instant,
    )

    @Serializable
    private data class DeviceCodeResponse(
        @SerialName("user_code") val userCode: String,
        @SerialName("device_code") val deviceCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Long = 900,
        @SerialName("interval") val interval: Long = 5,
        @SerialName("message") val message: String = "",
    )

    @Serializable
    private data class OAuthErrorResponse(
        @SerialName("error") val error: String,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private data class MsaTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    @Serializable
    private data class XboxTokenResponse(
        @SerialName("Token") val token: String? = null,
        @SerialName("DisplayClaims") val displayClaims: DisplayClaims? = null,
    )

    @Serializable
    private data class XboxErrorResponse(
        @SerialName("XErr") val xErr: Long = 0,
        @SerialName("Message") val message: String = "",
        @SerialName("Redirect") val redirect: String? = null,
    )

    @Serializable
    private data class DisplayClaims(val xui: List<Xui> = emptyList())

    @Serializable
    private data class Xui(val uhs: String)

    @Serializable
    private data class MinecraftTokenResponse(@SerialName("access_token") val accessToken: String)

    @Serializable
    private data class MinecraftProfile(val id: String, val name: String)
}
