package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OidcAuthorizeRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("code_challenge") val codeChallenge: String,
    @SerialName("code_challenge_method") val codeChallengeMethod: String,
)

@Serializable
data class OidcAuthorizeResponse(
    val code: String,
)

@Serializable
data class OidcTokenRequest(
    @SerialName("grant_type") val grantType: String = "authorization_code",
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("code_verifier") val codeVerifier: String,
)

@Serializable
data class OidcTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class OidcUserinfoResponse(
    val sub: String,
)
