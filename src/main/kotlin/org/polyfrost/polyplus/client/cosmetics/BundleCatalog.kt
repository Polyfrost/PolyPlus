package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.BundleSearchResponse
import org.polyfrost.polyplus.client.network.http.responses.BundleViewResponse

object BundleCatalog {
    private val LOGGER = LogManager.getLogger()

    const val MAX_PAGE_SIZE = 100

    suspend fun search(
        page: Int = 1,
        perPage: Int = 50,
        text: String? = null,
    ): Result<BundleSearchResponse> = runCatching {
        PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/bundles/search") {
            parameter("page", page.coerceAtLeast(1))
            parameter("nb", perPage.coerceIn(1, MAX_PAGE_SIZE))
            if (!text.isNullOrBlank()) parameter("text", text)
        }.body<BundleSearchResponse>()
    }.onFailure { LOGGER.error("Failed to search bundles", it) }

    suspend fun view(id: Int): Result<BundleViewResponse> = runCatching {
        PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/bundles/view/$id").body<BundleViewResponse>()
    }.onFailure { LOGGER.error("Failed to view bundle {}", id, it) }
}
