package org.polyfrost.polyplus

enum class BackendUrl(val url: String) {
    PRODUCTION("https://plus.polyfrost.org"),
    STAGING("https://plus-staging.polyfrost.org"),
    LOCAL("http://localhost:8080");

    operator fun plus(other: String): String {
        return this + other
    }

    override fun toString(): String {
        return url
    }
}
