package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson

internal object VariantOverrides {
    const val NO_BODY = "owns the background but shows no body"

    private val JSON = Json { ignoreUnknownKeys = true }

    fun ownsBackground(manifest: ManifestJson, variant: VariantJson): Boolean =
        variant.ownsBackground ?: manifest.ownsBackground

    fun params(manifest: ManifestJson, variant: VariantJson): Map<String, Double> = manifest.params + variant.params

    fun text(base: TextJson, override: JsonObject?): TextJson = merge(TextJson.serializer(), base, override)

    fun badge(base: BadgeJson, override: JsonObject?): BadgeJson = merge(BadgeJson.serializer(), base, override)

    fun visibleLayers(manifest: ManifestJson, variant: VariantJson): List<String> {
        val hidden = variant.hide.toSet()
        return manifest.layers.filter { it.id !in hidden }.map { variant.swap[it.id] ?: it.id }
    }

    fun bodyIssues(manifest: ManifestJson, layers: Map<String, LayerFileJson>, variants: Map<String, VariantJson>): List<BundleIssue> {
        if (layers.values.none { it is BodyLayerJson }) return emptyList()

        val issues = ArrayList<BundleIssue>()
        for ((key, variant) in variants) {
            val showsBody = visibleLayers(manifest, variant).any { layers[it] is BodyLayerJson }
            if (ownsBackground(manifest, variant) && !showsBody) {
                issues.add(BundleIssue("${BundleFiles.MANIFEST}#variants.$key.ownsBackground", NO_BODY))
            }
        }
        return issues
    }

    private fun <T> merge(serializer: KSerializer<T>, base: T, override: JsonObject?): T {
        if (override == null) return base

        val fields = LinkedHashMap(JSON.encodeToJsonElement(serializer, base) as JsonObject)
        fields.putAll(override)
        return JSON.decodeFromJsonElement(serializer, JsonObject(fields))
    }
}
