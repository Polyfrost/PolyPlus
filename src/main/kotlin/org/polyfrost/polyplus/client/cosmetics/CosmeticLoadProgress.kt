package org.polyfrost.polyplus.client.cosmetics

import java.util.concurrent.atomic.AtomicInteger

object CosmeticLoadProgress {
    private const val CATALOG_WEIGHT = 0.10f

    private const val METADATA_WEIGHT = 0.15f

    private const val ASSET_WEIGHT = 0.75f

    data class Snapshot(
        val started: Boolean,
        val ready: Boolean,
        val fraction: Float?,
        val label: String,
        val failure: String?,
    )

    @Volatile
    private var started = false

    @Volatile
    private var catalogFetched = false

    @Volatile
    private var metadataComplete = false

    @Volatile
    private var assetsStarted = false

    @Volatile
    private var assetsComplete = false

    @Volatile
    private var failure: String? = null

    private val assetsDone = AtomicInteger()
    private val assetsTotal = AtomicInteger()

    private val generation = AtomicInteger()

    val isReady: Boolean
        get() = started && failure == null && metadataComplete && assetsComplete

    fun snapshot(): Snapshot {
        val failed = failure
        val ready = isReady
        return Snapshot(
            started = started,
            ready = ready,
            fraction = fraction(ready),
            label = label(ready),
            failure = failed,
        )
    }

    private fun fraction(ready: Boolean): Float? {
        if (ready) return 1f
        if (!started || failure != null) return null
        var value = 0f
        if (catalogFetched) value += CATALOG_WEIGHT
        if (metadataComplete) value += METADATA_WEIGHT
        val total = assetsTotal.get()
        value += ASSET_WEIGHT * when {
            assetsComplete -> 1f
            total > 0 -> (assetsDone.get().toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }
        return value.coerceIn(0f, 1f)
    }

    private fun label(ready: Boolean): String = when {
        ready -> "Ready"
        failure != null -> "Couldn't load cosmetics"
        !catalogFetched -> "Fetching the cosmetic catalog..."
        !metadataComplete -> "Loading your wardrobe..."
        !assetsComplete -> {
            val total = assetsTotal.get()
            if (total > 0) {
                "Downloading your cosmetics (${assetsDone.get().coerceAtMost(total)}/$total)..."
            } else {
                "Downloading your cosmetics..."
            }
        }
        else -> "Almost there..."
    }

    fun beginRefresh() {
        generation.incrementAndGet()
        started = true
        catalogFetched = false
        metadataComplete = false
        assetsStarted = false
        assetsComplete = false
        failure = null
        assetsDone.set(0)
        assetsTotal.set(0)
    }

    fun markLoaded() {
        if (started) return
        started = true
        catalogFetched = true
        metadataComplete = true
        assetsStarted = true
        assetsComplete = true
        failure = null
    }

    fun onCatalogFetched() {
        catalogFetched = true
    }

    fun beginAssets(total: Int): Int {
        assetsDone.set(0)
        assetsTotal.set(total)
        assetsStarted = true
        if (total <= 0) assetsComplete = true
        return generation.get()
    }

    fun stepAssets() {
        assetsDone.incrementAndGet()
    }

    fun onAssetsComplete(token: Int) {
        if (token != generation.get()) return
        assetsComplete = true
    }

    fun onMetadataComplete() {
        if (!assetsStarted) assetsComplete = true
        metadataComplete = true
    }

    fun fail(reason: String) {
        failure = reason
        metadataComplete = true
        assetsComplete = true
    }
}
