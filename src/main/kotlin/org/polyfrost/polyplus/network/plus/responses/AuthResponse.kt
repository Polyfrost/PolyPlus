package org.polyfrost.polyplus.network.plus.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    @SerialName("token") val token: String,
)