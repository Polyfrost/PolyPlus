package org.polyfrost.polyplus.client.cosmetics

import java.util.concurrent.ConcurrentHashMap

internal class ParseFailures {
    private val failed = ConcurrentHashMap<String, String>()

    fun record(cacheKey: String, hash: String, variant: String?, bundleWide: Boolean) {
        failed[if (bundleWide) cacheKey else variantKey(cacheKey, variant)] = hash
    }

    fun failed(cacheKey: String, hash: String, variant: String?): Boolean =
        failed[cacheKey] == hash || failed[variantKey(cacheKey, variant)] == hash

    private fun variantKey(cacheKey: String, variant: String?): String = "$cacheKey|${variant ?: DEFAULT_VARIANT}"

    fun forget(cacheKey: String) {
        failed.keys.removeIf { it == cacheKey || it.startsWith("$cacheKey|") }
    }

    fun clear() = failed.clear()

    private companion object {
        const val DEFAULT_VARIANT = "*default"
    }
}
