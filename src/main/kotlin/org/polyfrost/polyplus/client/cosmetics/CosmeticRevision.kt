package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import java.security.MessageDigest
import java.util.HexFormat

data class CosmeticRevision(val content: String, val variant: String) {
    val tag: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest("$content|$variant".toByteArray(Charsets.UTF_8))
        "r" + HexFormat.of().formatHex(digest, 0, TAG_BYTES)
    }

    fun sameContent(other: CosmeticRevision?): Boolean = other != null && other.content == content

    companion object {
        const val PLACEHOLDER_HASH = "74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b"
        private const val HASH_HEX = 64
        private const val TAG_BYTES = 8
        private val HEX = Regex("[0-9a-fA-F]+")

        fun of(definition: CosmeticDefinition): CosmeticRevision = of(definition.hash, definition.url, definition.variantKey)

        fun of(hash: String?, url: String?, variantKey: String? = null): CosmeticRevision =
            CosmeticRevision(content(hash, url), variantKey.orEmpty())

        fun isContentHash(hash: String?): Boolean =
            hash != null && hash.length == HASH_HEX && HEX.matches(hash) && !hash.equals(PLACEHOLDER_HASH, ignoreCase = true)

        private val SIGNING_PREFIXES = listOf("x-amz-", "x-goog-", "x-oss-", "x-cos-")
        private val SIGNING_PARAMS = setOf(
            "expires", "signature", "key-pair-id", "policy", "awsaccesskeyid", "googleaccessid", "ossaccesskeyid",
            "sig", "se", "st", "sv", "sp", "sr", "spr", "ss", "srt", "si", "sip", "skoid", "sktid", "skt", "ske", "sks", "skv",
            "token", "verify", "hdnts", "hmac", "exp",
        )

        fun stableUrl(url: String): String {
            val queryStart = url.indexOf('?')
            if (queryStart < 0) return url
            val kept = url.substring(queryStart + 1).split('&')
                .filter { it.isNotEmpty() && !isSigningParam(it.substringBefore('=')) }
                .sorted()
            val base = url.substring(0, queryStart)
            return if (kept.isEmpty()) base else kept.joinToString("&", prefix = "$base?")
        }

        private fun isSigningParam(name: String): Boolean {
            val lower = name.lowercase()
            return lower in SIGNING_PARAMS || SIGNING_PREFIXES.any(lower::startsWith)
        }

        private fun content(hash: String?, url: String?): String = when {
            isContentHash(hash) -> "sha256:${hash!!.lowercase()}"
            url != null -> versionTag(hash)?.let { "url:${stableUrl(url)}|etag:$it" } ?: "url:${stableUrl(url)}"
            else -> "hash:${hash.orEmpty().lowercase()}"
        }

        private fun versionTag(hash: String?): String? =
            hash?.trim()?.takeIf { it.isNotEmpty() && !it.equals(PLACEHOLDER_HASH, ignoreCase = true) }
    }
}
