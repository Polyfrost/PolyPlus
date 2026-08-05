package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.OidcAuthorizeRequest
import org.polyfrost.polyplus.client.network.http.responses.OidcAuthorizeResponse
import org.polyfrost.polyplus.client.network.http.responses.OidcTokenRequest
import org.polyfrost.polyplus.client.network.http.responses.OidcTokenResponse

object OidcApi {
    suspend fun authorize(clientId: String, redirectUri: String, codeChallenge: String): Result<OidcAuthorizeResponse> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/oidc/authorize") {
            contentType(ContentType.Application.Json)
            setBody(OidcAuthorizeRequest(clientId, redirectUri, codeChallenge, "S256"))
        }

    suspend fun token(clientId: String, redirectUri: String, code: String, codeVerifier: String): Result<OidcTokenResponse> = runCatching {
        PolyPlusClient.HTTP.post("${PolyPlusConfig.apiUrl}/oidc/token") {
            contentType(ContentType.Application.Json)
            setBody(OidcTokenRequest(code = code, clientId = clientId, redirectUri = redirectUri, codeVerifier = codeVerifier))
        }.bodyOrThrow()
    }
}
