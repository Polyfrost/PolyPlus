package org.polyfrost.polyplus.client.legal

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.userAgent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File

@Serializable
data class LegalDocument(
    val version: Int = 1,
    @SerialName("privacy_version") val privacyVersion: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val terms: String = "",
    val privacy: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null,
    @SerialName("privacy_url") val privacyUrl: String? = null,
) {
    val resolvedPrivacyVersion: Int get() = privacyVersion ?: version
    val resolvedTermsUrl: String get() = termsUrl?.takeIf { it.isNotBlank() } ?: LegalDocuments.TERMS_URL
    val resolvedPrivacyUrl: String get() = privacyUrl?.takeIf { it.isNotBlank() } ?: LegalDocuments.PRIVACY_URL
    val privacyBody: String? get() = privacy?.takeIf { it.isNotBlank() }
}

object LegalDocuments {
    const val TERMS_URL = "https://polyfrost.org/legal/terms"
    const val PRIVACY_URL = "https://polyfrost.org/legal/privacy"

    private const val DOCUMENT_URL = "https://data-v2.polyfrost.org/oneclient/tos.json"

    private val LOGGER = LogManager.getLogger("PolyPlus/Legal")

    private val client by lazy {
        HttpClient(CIO) {
            defaultRequest { userAgent("${PolyPlusConstants.NAME}/${PolyPlusConstants.VERSION}") }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
        }
    }

    private val cacheFile: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/terms.json")

    suspend fun load(): Result<LegalDocument> {
        val remote = runCatching {
            val body = client.get(DOCUMENT_URL).bodyAsText()
            val document = PolyPlusClient.JSON.decodeFromString(LegalDocument.serializer(), body)
            require(document.terms.isNotBlank()) { "Terms document has no body" }
            cache(body)
            document
        }
        remote.onFailure { LOGGER.warn("Could not fetch the Terms of Service document; trying the cache", it) }
        return remote.recoverCatching { error ->
            cached() ?: throw error
        }
    }

    private fun cache(body: String) {
        runCatching {
            val target = cacheFile
            target.parentFile?.mkdirs()
            target.writeText(body)
        }.onFailure { LOGGER.warn("Could not cache the Terms of Service document", it) }
    }

    private fun cached(): LegalDocument? = runCatching {
        cacheFile.takeIf { it.isFile }?.readText()?.let {
            PolyPlusClient.JSON.decodeFromString(LegalDocument.serializer(), it)
        }
    }.getOrNull()
}
