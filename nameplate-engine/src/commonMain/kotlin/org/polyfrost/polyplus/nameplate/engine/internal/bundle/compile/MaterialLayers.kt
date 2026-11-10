package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialGlsl
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleBytes
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.NameplateMaterialMolang
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialLoad
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialLoader
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialScope
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal object MaterialLayers {
    private val ID = Regex("[a-z0-9_]{1,64}")

    fun validate(
        path: String,
        layer: MaterialLayerJson,
        layers: Map<String, LayerFileJson>,
        manifest: ManifestJson,
        report: IssueSink,
    ) {
        if (!ID.matches(layer.material)) {
            report("$path#material", "material id '${layer.material}' must be 1 to 64 of a-z, 0-9 and _")
        }
        val of = layer.of
        if (of != null && layers[of] !is BodyLayerJson) {
            report("$path#of", "must name a body layer")
        }
        if (layer.mirrorDelay < 0) {
            report("$path#mirrorDelay", "must be 0 or more")
        }
        if (MaterialLayer.CAPABILITY !in manifest.requires) {
            report("$path#kind", "material layers need \"${MaterialLayer.CAPABILITY}\" in the manifest's requires")
        }
    }

    sealed interface Result {
        class Loaded(val layers: Map<String, MaterialLayer>) : Result

        class Invalid(val issues: List<BundleIssue>) : Result
    }

    fun load(
        source: BundleSource,
        bytes: BundleBytes,
        manifest: ManifestJson,
        files: Map<String, LayerFileJson>,
        palette: Palette,
    ): Result {
        val users = manifest.layers.mapNotNull { layer -> (files[layer.id] as? MaterialLayerJson)?.let { layer to it } }
        if (users.isEmpty()) return Result.Loaded(emptyMap())
        val names = LinkedHashSet(manifest.params.keys)
        manifest.variants.values.forEach { names.addAll(it.params.keys) }
        val scope = MaterialScope(palette, names, manifest.params, manifest.fps, manifest.loop)
        val loader = MaterialLoader(NameplateMaterialMolang(names, manifest.loop, manifest.fps))
        val issues = ArrayList<BundleIssue>()
        val loaded = HashMap<Int, Map<String, Material>>()
        for ((key, group) in users.groupBy { (layer, json) -> clockKey(manifest, layer, json) }) {
            val cycle = if (key == DRIFT) manifest.loop / group.first().first.step else key
            when (val load = loader.load(source, bytes, scope, group.map { it.second.material }, cycle, periodic = key != DRIFT)) {
                is MaterialLoad.Loaded -> loaded[key] = load.materials
                is MaterialLoad.Invalid -> issues.addAll(load.issues)
            }
        }
        if (issues.isNotEmpty()) return Result.Invalid(issues.distinct())
        val shaders = HashMap<Material, MaterialShader>()
        val layers = LinkedHashMap<String, MaterialLayer>()
        for ((layer, json) in users) {
            val cycle = manifest.loop / layer.step
            val material = loaded.getValue(clockKey(manifest, layer, json)).getValue(json.material)
            if (material.animated && !json.drift && cycle > BundleLimits.MAX_CACHED_STATES) {
                issues.add(
                    BundleIssue(
                        BundleFiles.layerPath(layer.id),
                        "shows $cycle different frames; at most ${BundleLimits.MAX_CACHED_STATES} (raise its step)",
                        IssueCode.OVER_STATES,
                        issueValues("frames" to cycle, "limit" to BundleLimits.MAX_CACHED_STATES),
                    ),
                )
            }
            val shader = shaders.getOrPut(material) { MaterialGlsl.generate(material, GlslDialect.CORE_330) }
            layers[layer.id] = MaterialLayer(material, json.of, shader, json.drift, json.mirror, json.mirrorDelay, json.mirrorSeed)
        }
        return if (issues.isEmpty()) Result.Loaded(layers) else Result.Invalid(issues)
    }

    private const val DRIFT = -1

    private fun clockKey(manifest: ManifestJson, layer: LayerJson, json: MaterialLayerJson): Int =
        if (json.drift) DRIFT else manifest.loop / layer.step
}
