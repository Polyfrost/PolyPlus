package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import java.util.concurrent.ConcurrentHashMap

class NameplateChoice(
    val id: Int,
    val bundle: NameplateBundle,
    val variant: NameplateBundle.Variant,
    val label: String,
)

object NameplateChoices {
    private val catalog = ConcurrentHashMap<Int, NameplateChoice>()

    fun choice(id: Int): NameplateChoice? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id)?.takeIf { it.type == CosmeticType.Nameplate }
        val bundle = definition?.let { NameplateRegistry.installedBundle(id, it.hash) }
        if (definition == null || bundle == null) {
            catalog.remove(id)
            return null
        }
        catalog[id]?.takeIf { it.bundle === bundle }?.let { return it }
        return NameplateChoice(id, bundle, bundle.variant(definition.variantKey), definition.variantName).also { catalog[id] = it }
    }

    suspend fun ensureLoaded(id: Int) {
        if (CosmeticCatalog.getCosmeticDefinition(id)?.type != CosmeticType.Nameplate || choice(id) != null) return
        CosmeticAssetCache.ensureCosmeticLoaded(id)
    }

    fun groupChoices(id: Int): List<NameplateChoice> {
        choice(id) ?: return emptyList()
        return groupIds(id).mapNotNull(::choice).distinctBy { it.variant }
    }

    fun groupIds(id: Int): List<Int> {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return emptyList()
        return CosmeticCatalog.allDefinitions()
            .filter { it.type == CosmeticType.Nameplate && it.groupId == definition.groupId }
            .map { it.id }
            .sorted()
    }
}
