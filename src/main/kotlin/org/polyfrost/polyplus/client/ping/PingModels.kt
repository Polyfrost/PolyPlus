package org.polyfrost.polyplus.client.ping

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PingKind {
    @SerialName("go") GO,
    @SerialName("enemy") ENEMY,
    @SerialName("look") LOOK,
    @SerialName("loot") LOOT,
    @SerialName("danger") DANGER,
}

@Serializable
enum class PingTargetType {
    @SerialName("block") BLOCK,
    @SerialName("entity") ENTITY,
    @SerialName("player") PLAYER,
    @SerialName("item") ITEM,
}

@Serializable
enum class PingReply {
    @SerialName("seen") SEEN,
    @SerialName("coming") COMING,
    @SerialName("cant") CANT,
    @SerialName("none") NONE,
}

data class PingResponse(
    val from: String,
    val reply: PingReply,
    val at: Long,
)

@Serializable
data class PingTarget(
    val type: PingTargetType,
    val id: String? = null,
    val entity: String? = null,
)

data class Ping(
    val id: String,
    val from: String,
    val own: Boolean,
    val kind: PingKind,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val target: PingTarget?,
    val label: String?,
    val sentAt: Long,
    val expiresAt: Long,
    val replies: List<PingResponse> = emptyList(),
)
