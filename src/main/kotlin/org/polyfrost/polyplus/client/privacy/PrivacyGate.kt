package org.polyfrost.polyplus.client.privacy

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.privacy.PrivacyConsent

class OnlineServicesDisabledException(url: String) : IllegalStateException(
    "PolyPlus online features are disabled (Terms of Service / Privacy Policy not accepted): $url",
) {
    override fun fillInStackTrace(): Throwable = this
}

private const val POLYFROST_DOMAIN = "polyfrost.org"

fun isPolyfrostHost(host: String, backendHost: String?): Boolean {
    val normalized = host.lowercase().trimEnd('.')
    if (normalized == backendHost?.lowercase()?.trimEnd('.')) return true
    return normalized == POLYFROST_DOMAIN || normalized.endsWith(".$POLYFROST_DOMAIN")
}

private fun backendHost(): String? =
    runCatching { Url(PolyPlusConfig.apiUrl.url).host }.getOrNull()

val PrivacyGate = createClientPlugin("PolyPlusPrivacyGate") {
    onRequest { request, _ ->
        if (!isPolyfrostHost(request.url.host, backendHost())) return@onRequest
        if (!PrivacyConsent.allowsOnlineServices()) {
            throw OnlineServicesDisabledException(request.url.buildString())
        }
    }
}
