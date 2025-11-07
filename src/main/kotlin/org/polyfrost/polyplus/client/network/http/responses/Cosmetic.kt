package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class Cosmetic(val type: String, val id: Int, val url: String, val hash: String)
