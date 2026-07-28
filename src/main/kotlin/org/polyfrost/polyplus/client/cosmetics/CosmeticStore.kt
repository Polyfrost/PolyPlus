package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.privacy.OnlineServicesDisabledException
import org.polyfrost.polyplus.client.network.http.responses.CosmeticSearchResponse
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreView

object CosmeticStore {
    private val LOGGER = LogManager.getLogger()

    const val MAX_PAGE_SIZE = 100

    enum class Sort(val serializedName: String) {
        Newest("newest"),
        Oldest("oldest"),
        Ascending("ascending"),
        Descending("descending"),
        Popularity("popularity"),
    }

    suspend fun search(
        page: Int = 1,
        perPage: Int = 12,
        text: String? = null,
        sort: Sort = Sort.Newest,
        types: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        collection: Int? = null,
    ): Result<CosmeticSearchResponse> = runCatching {
        PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics/search") {
            parameter("page", page.coerceAtLeast(1))
            parameter("nb", perPage.coerceIn(1, MAX_PAGE_SIZE))
            parameter("sort", sort.serializedName)
            if (!text.isNullOrBlank()) parameter("text", text)
            if (types.isNotEmpty()) parameter("types", types.joinToString(","))
            if (tags.isNotEmpty()) parameter("tags", tags.joinToString(","))
            if (collection != null) parameter("collection", collection)
        }.body<CosmeticSearchResponse>()
    }.onFailure { reportFailure("Failed to search cosmetics", it) }

    private var cachedStockedTypes: List<CosmeticType>? = null

    suspend fun stockedTypes(): List<CosmeticType> {
        cachedStockedTypes?.let { return it }
        val types = CosmeticType.entries.filter { it != CosmeticType.Unknown }
        var anySucceeded = false
        val stocked = coroutineScope {
            types.map { type ->
                async {
                    val result = search(page = 1, perPage = 1, types = listOf(type.serializedName))
                    if (result.isSuccess) anySucceeded = true
                    val count = result.getOrNull()?.pagination?.totalItems
                    type.takeIf { count == null || count > 0 }
                }
            }.awaitAll()
        }.filterNotNull()
        if (!anySucceeded) return stocked
        return stocked.also { cachedStockedTypes = it }
    }

    suspend fun view(id: Int): Result<CosmeticStoreView> = runCatching {
        PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/cosmetics/view/$id").body<CosmeticStoreView>()
    }.onFailure { reportFailure("Failed to view cosmetic $id", it) }

    private fun reportFailure(message: String, error: Throwable) {
        if (error is CancellationException) return
        if (error is OnlineServicesDisabledException) {
            LOGGER.debug("{}: online services are disabled", message)
            return
        }
        LOGGER.error(message, error)
        PolyPlusSentry.capture(error)
    }
}
