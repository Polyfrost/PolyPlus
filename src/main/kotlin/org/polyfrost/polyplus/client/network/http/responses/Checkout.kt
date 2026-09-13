package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class CreateCheckoutRequest(
    val player: String,
    val products: List<String>,
    val buyer: String? = null,
)

@Serializable
data class CreateCheckoutResponse(
    val url: String,
)
