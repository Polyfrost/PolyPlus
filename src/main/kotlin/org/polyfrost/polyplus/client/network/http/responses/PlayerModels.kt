package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class ResolvedPlayer(
    val id: String,
    val username: String,
)

@Serializable
data class ResolvePlayersRequest(
    val ids: List<String>,
)

@Serializable
data class ResolvePlayersResponse(
    val players: List<ResolvedPlayer>,
)

@Serializable
data class PlayerLookupResponse(
    val id: String,
    val username: String,
)
