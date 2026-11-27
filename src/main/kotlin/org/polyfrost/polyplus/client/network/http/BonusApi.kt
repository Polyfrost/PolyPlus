package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.BonusPreviewRequest
import org.polyfrost.polyplus.client.network.http.responses.BonusPreviewResponse
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.network.http.responses.DiscoveredBonus

object BonusApi {
    const val MAX_PREVIEW_ITEMS = 32

    suspend fun preview(cosmeticIds: Collection<Int>): Result<List<BonusRef>> {
        val ids = cosmeticIds.distinct()
        if (ids.isEmpty()) return Result.success(emptyList())
        if (ids.size > MAX_PREVIEW_ITEMS) {
            return Result.failure(IllegalArgumentException("At most $MAX_PREVIEW_ITEMS cosmetics can be previewed at once"))
        }
        return PolyPlusClient.HTTP.postBodyAuthorized<BonusPreviewResponse>("${PolyPlusConfig.apiUrl}/bonuses/preview") {
            contentType(ContentType.Application.Json)
            setBody(BonusPreviewRequest(ids))
        }.map { it.bonuses }
    }

    suspend fun discovered(): Result<List<DiscoveredBonus>> =
        PolyPlusClient.HTTP.getBodyAuthorized<List<DiscoveredBonus>>("${PolyPlusConfig.apiUrl}/bonuses/discovered")
}
