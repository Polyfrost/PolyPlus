package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NameplateWearers {
    private class Resolved(val revision: Int, val equip: NameplateEquip)

    private class Bound(val cosmeticId: Int) {
        @Volatile
        var resolved: Resolved? = null
    }

    private val wearers = ConcurrentHashMap<UUID, Bound>()

    fun cosmeticId(wearer: UUID): Int? = wearers[wearer]?.cosmeticId

    fun resolve(wearer: UUID): NameplateEquip? {
        val bound = wearers[wearer] ?: return null
        val revision = NameplateBundles.revision()
        val cached = bound.resolved
        if (cached != null && cached.revision == revision) return cached.equip
        val id = bound.cosmeticId
        NameplateBundles.get(id)?.let {
            bound.resolved = Resolved(revision, it)
            return it
        }
        if (CosmeticCatalog.getCosmeticDefinition(id) == null) CosmeticCatalog.requestRefreshForUnknown(id)
        return null
    }

    fun wearers(): Set<UUID> = wearers.keys

    internal fun bind(wearer: UUID, cosmeticId: Int) {
        val current = wearers[wearer]
        if (current != null && current.cosmeticId == cosmeticId) return
        wearers[wearer] = Bound(cosmeticId)
        NameplateBundles.listener?.wearerBound(wearer, NameplateBundles.get(cosmeticId))
    }

    internal fun forget(wearer: UUID) {
        if (wearers.remove(wearer) != null) NameplateBundles.listener?.wearerForgotten(wearer)
    }

    internal fun forgetAll() {
        val forgotten = wearers.keys.toList()
        wearers.clear()
        forgotten.forEach { NameplateBundles.listener?.wearerForgotten(it) }
    }
}
