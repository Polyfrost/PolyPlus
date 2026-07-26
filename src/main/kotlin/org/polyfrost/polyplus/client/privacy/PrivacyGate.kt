package org.polyfrost.polyplus.client.privacy

import io.ktor.client.plugins.api.createClientPlugin
import org.polyfrost.polyplus.privacy.PrivacyConsent

class OnlineServicesDisabledException(url: String) : IllegalStateException(
    "PolyPlus online features are disabled (Terms of Service / Privacy Policy not accepted): $url",
) {
    override fun fillInStackTrace(): Throwable = this
}

val PrivacyGate = createClientPlugin("PolyPlusPrivacyGate") {
    onRequest { request, _ ->
        if (!PrivacyConsent.allowsOnlineServices()) {
            throw OnlineServicesDisabledException(request.url.buildString())
        }
    }
}
