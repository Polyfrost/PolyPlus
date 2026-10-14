package org.polyfrost.polyplus.client.cosmetics.outfits

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.cosmetics.MojangSkinException
import org.polyfrost.polyplus.client.network.http.requestBodyAuthorized
import org.polyfrost.polyplus.client.privacy.OnlineServicesDisabledException
import org.polyfrost.polyplus.privacy.PrivacyConsent

internal object OutfitApi {
    const val PRIVACY_MESSAGE =
        "Outfits need the Terms of Service and Privacy Policy. Accept them under Privacy in the PolyPlus settings."
    private const val OFFLINE_MESSAGE = "Couldn't reach the PolyPlus servers."

    fun url(path: String): String = "${PolyPlusConfig.apiUrl}$path"

    fun allowed(): Boolean = PrivacyConsent.allowsOnlineServices()

    fun <T> privacyFailure(): Result<T> = Result.failure(OutfitException(PRIVACY_MESSAGE))

    suspend inline fun <reified T> send(
        method: HttpMethod,
        path: String,
        body: JsonObject? = null,
    ): Result<T> = PolyPlusClient.HTTP.requestBodyAuthorized<T>(url(path), method) {
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    fun failureMessage(error: Throwable, notFound: String, conflict: String, badRequest: String): String = when (error) {
        is OutfitException -> error.message ?: OFFLINE_MESSAGE
        is MojangSkinException -> error.message ?: OFFLINE_MESSAGE
        is OnlineServicesDisabledException -> PRIVACY_MESSAGE
        is ClientRequestException -> when (error.response.status) {
            HttpStatusCode.NotFound -> notFound
            HttpStatusCode.Conflict -> conflict
            HttpStatusCode.BadRequest -> backendText(error) ?: badRequest
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                "Couldn't sign in to PolyPlus. Try again in a moment."
            HttpStatusCode.TooManyRequests -> "Too many changes at once. Try again in a moment."
            else -> backendText(error) ?: OFFLINE_MESSAGE
        }
        is ServerResponseException -> "The PolyPlus servers are having trouble. Try again later."
        else -> OFFLINE_MESSAGE
    }

    private fun backendText(error: ClientRequestException): String? =
        error.message.substringAfter("Text: \"", "").substringBeforeLast('"').trim().takeIf { it.isNotBlank() }
}
