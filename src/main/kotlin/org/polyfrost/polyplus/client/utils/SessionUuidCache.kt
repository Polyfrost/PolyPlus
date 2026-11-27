package org.polyfrost.polyplus.client.utils

import java.util.UUID

class SessionUuidCache {
    private var input: String? = null
    private var uuid: UUID? = null
    private var failure: IllegalArgumentException? = null

    fun lookup(candidate: String): UUID? {
        if (candidate != input) return null
        failure?.let { throw it }
        return uuid
    }

    fun store(candidate: String, parsed: UUID) {
        input = candidate
        uuid = parsed
        failure = null
    }

    fun storeFailure(candidate: String, error: IllegalArgumentException) {
        input = candidate
        uuid = null
        failure = error
    }
}
