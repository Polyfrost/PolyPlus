package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticList(@SerialName("cosmetics") val contents: List<CosmeticGroupResponse>)

@Serializable
data class EmoteList(@SerialName("emotes") val contents: List<EmoteDefinition>)
