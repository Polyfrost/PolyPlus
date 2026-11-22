package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.VariantOverrides
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Edge
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor

internal object LayerTargets {
    val FOLLOWS: Set<String> = setOf("outside", "line")

    private const val BODY_RULE = "must name a body layer"

    fun spriteOf(path: String, layer: SpriteLayerJson, layers: Map<String, LayerFileJson>, report: IssueSink) {
        val of = layer.of ?: return
        when {
            of == NAME_TARGET -> if (layer.path != null) {
                report.issue("$path#of", "a path can't run along just the name; use a body or the whole tag", IssueCode.NAME_TARGET)
            }
            layers[of] !is BodyLayerJson -> report("$path#of", BODY_RULE)
        }
    }

    fun bodyOf(path: String, layer: BodyLayerJson, report: IssueSink) {
        val of = layer.of ?: return
        if (of != NAME_TARGET) {
            report.issue("$path#of", "a body can only wrap $NAME_TARGET; leave of out to wrap the whole tag", IssueCode.NAME_TARGET)
        }
    }

    fun spriteFollow(path: String, layer: SpriteLayerJson, report: IssueSink) {
        val follow = layer.follow ?: return
        val sink = report.coded(IssueCode.FOLLOW)
        when {
            follow !in FOLLOWS -> sink("$path#follow", "must be one of ${FOLLOWS.joinToString()}")
            OrnamentAnchor.isName(layer.anchor) -> sink("$path#follow", "a name anchor sits on the name, so it has no edge to follow")
            layer.path != null -> sink("$path#follow", "a layer on a path sets follow on its path")
        }
        val edge = layer.repeat?.let { Edge.named(it.edge) }
        if (edge == Edge.LEFT || edge == Edge.RIGHT) {
            sink("$path#repeat.edge", "repeats that follow the body's edge only run along top or bottom")
        }
    }

    fun badges(manifest: ManifestJson, layers: Map<String, LayerFileJson>, report: IssueSink) {
        val sink = report.coded(IssueCode.BADGE)
        val base = manifest.badge.of
        if (base != null && layers[base] !is BodyLayerJson) {
            sink("${BundleFiles.MANIFEST}#badge.of", BODY_RULE)
        }
        for ((key, variant) in manifest.variants) {
            val of = merged(manifest.badge, variant)?.of ?: continue
            val path = "${BundleFiles.MANIFEST}#variants.$key.badge.of"
            val swap = variant.swap[of]
            when {
                of != base && layers[of] !is BodyLayerJson -> sink(path, BODY_RULE)
                swap != null && layers[swap] !is BodyLayerJson ->
                    sink(path, "'$of' is swapped for '$swap' here, which isn't a body; the badge needs a body to sit on")
            }
        }
    }

    private fun merged(badge: BadgeJson, variant: VariantJson): BadgeJson? = try {
        VariantOverrides.badge(badge, variant.badge)
    } catch (_: IllegalArgumentException) {
        null
    }
}
