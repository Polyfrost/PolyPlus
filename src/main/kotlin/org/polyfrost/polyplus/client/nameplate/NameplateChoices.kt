package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
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
    private class CatalogEntry(val hash: String, val choice: NameplateChoice)

    private val catalog = ConcurrentHashMap<Int, CatalogEntry>()

    fun choice(id: Int): NameplateChoice? = catalogChoice(id)

    fun isCatalogNameplate(id: Int): Boolean = CosmeticCatalog.getCosmeticDefinition(id)?.type == CosmeticType.Nameplate

    suspend fun ensureLoaded(id: Int) {
        if (!isCatalogNameplate(id) || catalogChoice(id) != null) return
        CosmeticAssetCache.ensureCosmeticLoaded(id)
    }

    fun groupChoices(id: Int): List<NameplateChoice> {
        catalogChoice(id) ?: return emptyList()
        return groupIds(id).mapNotNull(::catalogChoice).distinctBy { it.variant }
    }

    fun groupIds(id: Int): List<Int> {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return emptyList()
        return CosmeticCatalog.allDefinitions()
            .filter { it.type == CosmeticType.Nameplate && it.groupId == definition.groupId }
            .map { it.id }
            .sorted()
    }

    private fun catalogChoice(id: Int): NameplateChoice? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        if (definition.type != CosmeticType.Nameplate) return null
        catalog[id]?.let { entry ->
            if (entry.hash == definition.hash && NameplateRegistry.isLoaded(id)) return entry.choice
            catalog.remove(id)
        }
        val bundle = bundleFor(id, definition) ?: return null
        val choice = NameplateChoice(id, bundle, bundle.variant(definition.variantKey), definition.variantName)
        catalog[id] = CatalogEntry(definition.hash, choice)
        return choice
    }

    private fun bundleFor(id: Int, definition: CosmeticDefinition): NameplateBundle? =
        if (NameplateRegistry.isLoaded(id, definition.hash)) NameplateRegistry.installedBundle(id) else null
}
