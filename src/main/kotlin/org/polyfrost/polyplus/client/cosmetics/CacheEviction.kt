package org.polyfrost.polyplus.client.cosmetics

internal object CacheEviction {
    private val MANAGED_PREFIXES = listOf("emote-", "nameplate-", "bundle-", "bonus-")
    private val LEGACY_KEY = Regex("cosmetic-[0-9]+")

    fun managed(name: String): Boolean = MANAGED_PREFIXES.any(name::startsWith) || LEGACY_KEY.matches(name)

    fun obsolete(names: Collection<String>, keep: Set<String>, recent: (String) -> Boolean): List<String> =
        names.filter { managed(it) && it !in keep && !recent(it) }
}
