package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmoteAnchor(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)

@Serializable
data class SharedEmote(
    @SerialName("emote_id") val emoteId: Int,
    val players: List<String>,
    val anchor: EmoteAnchor,
    @SerialName("start_time") val startTime: Long,
)

@Serializable(with = SharedEmoteResult.Serializer::class)
enum class SharedEmoteResult(val serializedName: String) {
    Accepted("accepted"),
    Declined("declined"),
    Expired("expired"),
    Cancelled("cancelled"),
    Unknown("unknown");

    internal object Serializer : KSerializer<SharedEmoteResult> by ApiEnumSerializer(
        "SharedEmoteResult",
        SharedEmoteResult.entries,
        Unknown,
        SharedEmoteResult::serializedName,
    )
}

@Serializable(with = SharedEmotePrivacy.Serializer::class)
enum class SharedEmotePrivacy(val serializedName: String) {
    Everyone("everyone"),
    Friends("friends"),
    Nobody("nobody"),
    Unknown("unknown");

    internal object Serializer : KSerializer<SharedEmotePrivacy> by ApiEnumSerializer(
        "SharedEmotePrivacy",
        SharedEmotePrivacy.entries,
        Unknown,
        SharedEmotePrivacy::serializedName,
    )
}

@Serializable
data class SharedEmotePrivacyBody(val privacy: SharedEmotePrivacy)
