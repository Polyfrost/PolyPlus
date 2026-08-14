package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    @SerialName("eos_session_id") val eosSessionId: String? = null,
)

@Serializable
data class UpdateSessionRequest(
    @SerialName("eos_session_id") val eosSessionId: String,
)

@Serializable
data class SessionResponse(
    val id: String,
    @SerialName("eos_session_id") val eosSessionId: String? = null,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class SessionInvite(
    val id: Int,
    @SerialName("session_id") val sessionId: String,
    val sender: String,
    @SerialName("eos_product_user_id") val eosProductUserId: String? = null,
    @SerialName("eos_session_id") val eosSessionId: String? = null,
    @SerialName("created_at") val createdAt: String,
)
