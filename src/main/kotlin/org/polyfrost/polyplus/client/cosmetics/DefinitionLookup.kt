package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticList
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

internal class DefinitionRouteGate(private val retryMs: Long = 30 * 60_000L) {
    private var missingUntil = Long.MIN_VALUE

    @Synchronized
    fun available(now: Long): Boolean = now >= missingUntil

    @Synchronized
    fun waitMs(now: Long): Long = if (now >= missingUntil) 0L else missingUntil - now

    @Synchronized
    fun missing(now: Long) {
        missingUntil = now + retryMs
    }
}

internal fun requestedDefinitions(list: CosmeticList, wanted: Set<Int>): List<CosmeticDefinition> =
    list.contents
        .filter { it.type != CosmeticType.Unknown && it.type != CosmeticType.Emote }
        .flatMap { it.flatten() }
        .filter { it.id in wanted }

internal fun missingFromListing(owned: Set<Int>, listed: Set<Int>, local: Set<Int>): Set<Int> = owned - listed - local

internal fun definitionRouteMissing(status: Int?): Boolean = status == 404 || status == 405

internal fun mergeGroupVariants(public: List<Int>, owned: List<Int>): List<Int> {
    val merged = public.toMutableList()
    val present = public.toHashSet()
    val leading = ArrayList<Int>()
    var after = -1
    for (id in owned) {
        if (id in present) {
            if (after < 0 && leading.isNotEmpty()) {
                merged.addAll(merged.indexOf(id), leading)
                present += leading
                leading.clear()
            }
            after = merged.indexOf(id)
            continue
        }
        if (after < 0) {
            if (id !in leading) leading += id
            continue
        }
        after += 1
        merged.add(after, id)
        present += id
    }
    merged += leading
    return merged
}
