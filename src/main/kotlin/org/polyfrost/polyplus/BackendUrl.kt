package org.polyfrost.polyplus

import dev.deftu.omnicore.api.commands.CommandCompletable

enum class BackendUrl(val url: String) : CommandCompletable {
    PRODUCTION("https://plus.polyfrost.org"),
    STAGING("https://plus-staging.polyfrost.org"),
    LOCAL("http://localhost:8080");

    override val id: String
        get() = name.lowercase()

    operator fun plus(other: String): String {
        return this + other
    }

    override fun toString(): String {
        return url
    }
}
