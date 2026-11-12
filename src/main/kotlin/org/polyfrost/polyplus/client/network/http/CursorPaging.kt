package org.polyfrost.polyplus.client.network.http

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.libs.ktor.client.HttpClient
import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.client.request.HttpRequestBuilder
import org.polyfrost.polyplus.libs.ktor.client.request.parameter
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode

@Serializable
data class CursorPage<T>(val items: List<T>, val cursor: String? = null)

const val PAGE_LIMIT = 50
private const val DEFAULT_RETRY_SECONDS = 5L
private const val MAX_RETRY_SECONDS = 3600L
private const val MILLIS_PER_SECOND = 1000L

suspend inline fun <reified T> HttpClient.pageAuthorized(
    url: String,
    cursor: String?,
    limit: Int = PAGE_LIMIT,
    noinline block: HttpRequestBuilder.() -> Unit = {},
): Result<CursorPage<T>> = getBodyAuthorized(url) {
    parameter("limit", limit)
    cursor?.let { parameter("cursor", it) }
    apply(block)
}

fun Throwable.retryAfterSeconds(): Long? {
    val error = this as? ClientRequestException ?: return null
    if (error.response.status != HttpStatusCode.TooManyRequests) return null
    val seconds = error.response.headers["Retry-After"]?.trim()?.toLongOrNull() ?: DEFAULT_RETRY_SECONDS
    return seconds.coerceIn(1L, MAX_RETRY_SECONDS)
}

suspend fun <T> respectingRateLimit(attempts: Int = 2, request: suspend () -> Result<T>): Result<T> {
    var result = request()
    repeat(attempts - 1) {
        val wait = result.exceptionOrNull()?.retryAfterSeconds() ?: return result
        delay(wait * MILLIS_PER_SECOND)
        result = request()
    }
    return result
}
