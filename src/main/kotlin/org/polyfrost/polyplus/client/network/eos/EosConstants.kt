package org.polyfrost.polyplus.client.network.eos

import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusConfig

object EosConstants {
    const val PRODUCT_NAME: String = "PolyPlus"
    const val PRODUCT_VERSION: String = "1.0"
}

enum class EosMode(
    val productId: String,
    val sandboxId: String,
    val deploymentId: String,
    val clientId: String,
    val clientSecret: String,
) {
    PRODUCTION(
        productId = "aabe44fc0a0d44018e62a0f9e5258df9",
        sandboxId = "8c5ac0222b2d482b93266380379f8ce4",
        deploymentId = "a2bda6c9ef96449d8f9bdfc5da802691",
        clientId = "xyza7891YWUuhIbR2cLTLQE1fgZHc9yU",
        clientSecret = "63otFXsi5+NEuM9HbNaL8DTZTpJ0b4d8qPtlwXdXfH8",
    ),
    TESTING(
        productId = "d15c21eafb304dc5a53ad6d08c39f370",
        sandboxId = "f7f54e093c944f31992f6fbea65a5d62",
        deploymentId = "d4bce4f9c8d948a49bfbc2ed778c1577",
        clientId = "xyza7891lRRPANUx4UBvPk7sPQKW4Bj3",
        clientSecret = "Yg0LrWERplfk73bGx4UObG2Rt+sqRRYbIZyPpmIZeBw",
    );

    companion object {
        private const val STAGING_URL = "https://plus-staging.polyfrost.org"

        val current: EosMode by lazy {
            when (System.getProperty("polyplus.eosMode")?.trim()?.lowercase()) {
                "production" -> PRODUCTION
                "testing" -> TESTING
                else -> if (PolyPlusConstants.IS_TESTER || PolyPlusConfig.apiUrl.url == STAGING_URL) TESTING else PRODUCTION
            }
        }
    }
}
