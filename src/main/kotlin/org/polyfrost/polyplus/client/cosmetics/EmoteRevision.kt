package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import java.security.MessageDigest
import java.util.HexFormat

data class EmoteRevision(val id: Int, val hash: String) {
    val tag: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(hash.lowercase().toByteArray(Charsets.UTF_8))
        HexFormat.of().formatHex(digest, 0, TAG_BYTES)
    }

    companion object {
        private const val TAG_BYTES = 8

        fun of(definition: CosmeticDefinition): EmoteRevision = EmoteRevision(definition.id, definition.hash)
    }
}
