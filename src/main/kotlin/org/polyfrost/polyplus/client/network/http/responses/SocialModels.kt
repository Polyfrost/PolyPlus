package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RelationshipKind.Serializer::class)
enum class RelationshipKind {
    Friend,
    BestFriend,
    Unknown;

    val serializedName: String
        get() = when (this) {
            Friend -> "friend"
            BestFriend -> "best_friend"
            Unknown -> "unknown"
        }

    companion object {
        fun fromSerializedName(name: String): RelationshipKind? =
            entries.firstOrNull { it != Unknown && it.serializedName == name }
    }

    internal object Serializer : KSerializer<RelationshipKind> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RelationshipKind", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: RelationshipKind) {
            encoder.encodeString(value.serializedName)
        }

        override fun deserialize(decoder: Decoder): RelationshipKind =
            fromSerializedName(decoder.decodeString()) ?: Unknown
    }
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
