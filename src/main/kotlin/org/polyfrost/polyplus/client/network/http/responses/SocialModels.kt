package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = RelationshipKind.Serializer::class)
enum class RelationshipKind(val serializedName: String) {
    Friend("friend"),
    BestFriend("best_friend"),
    Unknown("unknown");

    internal object Serializer : KSerializer<RelationshipKind> by ApiEnumSerializer(
        "RelationshipKind",
        RelationshipKind.entries,
        Unknown,
        RelationshipKind::serializedName,
    )
}

@Serializable
data class Friend(
    val player: String,
    val kind: RelationshipKind,
    val since: String,
    val online: Boolean = false,
)

@Serializable
data class FriendRequest(
    val id: Int,
    val player: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BlockedPlayer(
    val player: String,
    val since: String,
)
