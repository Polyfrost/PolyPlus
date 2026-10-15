package org.polyfrost.polyplus.client.cosmetics.outfits

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

enum class SkinModel(val serializedName: String) {
    Classic("classic"),
    Slim("slim");

    internal companion object {
        fun fromSerialized(value: String?): SkinModel =
            if (value.equals(Slim.serializedName, ignoreCase = true)) Slim else Classic
    }
}

data class PlayerSkinInfo(
    val id: Int,
    val name: String,
    val model: SkinModel,
    val url: String,
    val hash: String,
    val favorite: Boolean,
    val createdAt: String,
)

data class OutfitInfo(
    val id: Int,
    val name: String,
    val favorite: Boolean,
    val skinId: Int?,
    val mojangCapeId: String?,
    val particleColor: Int?,
    val equipped: Map<BodySlot, Int>,
    val createdAt: String,
    val updatedAt: String,
    val blank: Boolean = false,
)

sealed interface LibraryState {
    data object Idle : LibraryState
    data object Loading : LibraryState
    data object Loaded : LibraryState
    data class Unavailable(val reason: String) : LibraryState
}

class OutfitException(message: String) : Exception(message)

@Serializable
internal data class SkinResponse(
    val id: Int,
    val name: String = "",
    val model: String = SkinModel.Classic.serializedName,
    val url: String = "",
    val hash: String = "",
    val favorite: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
) {
    fun toInfo(): PlayerSkinInfo = PlayerSkinInfo(
        id = id,
        name = name,
        model = SkinModel.fromSerialized(model),
        url = url,
        hash = hash.lowercase(),
        favorite = favorite,
        createdAt = createdAt,
    )
}

@Serializable
internal data class OutfitResponse(
    val id: Int,
    val name: String = "",
    val favorite: Boolean = false,
    @SerialName("skin_id") val skinId: Int? = null,
    @SerialName("mojang_cape_id") val mojangCapeId: String? = null,
    @SerialName("particle_color") val particleColor: Int? = null,
    val blank: Boolean = false,
    val equipped: Map<BodySlot, Int> = emptyMap(),
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun toInfo(): OutfitInfo = OutfitInfo(
        id = id,
        name = name,
        favorite = favorite,
        skinId = skinId,
        mojangCapeId = mojangCapeId,
        particleColor = particleColor,
        equipped = equipped.filterKeys { it != BodySlot.Unknown },
        createdAt = createdAt,
        updatedAt = updatedAt,
        blank = blank,
    )
}

@Serializable
internal data class OutfitListResponse(
    val outfits: List<OutfitResponse> = emptyList(),
    @SerialName("active_outfit_id") val activeOutfitId: Int? = null,
    val limit: Int = LIBRARY_LIMIT,
)

@Serializable
internal data class SkinListResponse(
    val skins: List<SkinResponse> = emptyList(),
    val limit: Int = LIBRARY_LIMIT,
)

@Serializable
internal data class ApplyOutfitResponse(
    val outfit: OutfitResponse,
    val skin: SkinResponse? = null,
    val adopted: Boolean = false,
)

internal const val LIBRARY_LIMIT = 128
