package org.polyfrost.polyplus

enum class BackendUrl(private val defaultUrl: String) {
    PRODUCTION("https://plus.polyfrost.org"),
    STAGING("https://plus-staging.polyfrost.org"),
    LOCAL("http://localhost:8080");

    val url: String get() = OVERRIDE ?: defaultUrl

    operator fun plus(other: String): String {
        return this + other
    }

    override fun toString(): String {
        return url
    }

    companion object {
        private val OVERRIDE: String? =
            System.getProperty("polyplus.apiUrl")?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/')
    }
}
