@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.molang.MolangLimits
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.anim.LayerMasks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.CostBudget
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.FlashResult
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.FlashValidator
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.MaterialFlashValidator
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget.ReviewWarnings

@JsExport
object NameplateLimits {
    val quads: Int = BundleLimits.MAX_QUADS
    val cacheBytes: Int = BundleLimits.MAX_CACHED_BYTES
    val fallbackBytes: Int = BundleLimits.MAX_FALLBACK_BYTES
    val cpu: Int = MolangLimits.CPU_BUDGET
    val margin: Int = BundleLimits.MAX_DECORATION_MARGIN
    val states: Int = BundleLimits.MAX_CACHED_STATES
    val flashes: Int = BundleLimits.MAX_FLASHES
    val flashArea: Int = BundleLimits.MAX_FLASH_AREA
}

@JsExport
class NameplateLayerCost internal constructor(val id: String, val states: Int, val margin: Int)

@JsExport
class NameplateCost internal constructor(
    val quads: Int,
    val cacheBytes: Int,
    val cpu: Int,
    val layers: Array<NameplateLayerCost>,
    val flash: String?,
    val flashTimes: Int?,
    val flashPixels: Int?,
    val issues: Array<NameplateIssue>,
    val warnings: Array<NameplateIssue>,
    val materialBuildOps: Double,
    val fallbacks: Array<NameplateFallbackCost>,
    val infos: Array<NameplateIssue>,
) {
    val fallbackBytes: Double
        get() = fallbacks.sumOf { it.bytes }

    val states: Int
        get() = layers.maxOfOrNull { it.states } ?: 0

    val totalStates: Int
        get() = layers.sumOf { it.states }

    val margin: Int
        get() = layers.maxOfOrNull { it.margin } ?: 0
}

@JsExport
fun nameplateCost(preview: NameplatePreview, flash: Boolean): NameplateCost {
    val bundle = preview.bundle
    val report = CostBudget.report(bundle)
    val layers = bundle.layers.map { layer ->
        val content = layer.content
        val shown = if (content is ParticleEmitter) content.states().size else LayerMasks.cachedStates(bundle, layer)
        NameplateLayerCost(layer.id, shown, report.margins[layer.id] ?: 0)
    }
    val issues = CostBudget.budgetIssues(bundle, report).toMutableList()
    var worst: FlashResult? = null
    var flashIssue: String? = null
    if (flash) {
        val shapes = FlashValidator.worst(bundle)
        val materials = MaterialFlashValidator.worst(bundle)
        val flashIssues = listOfNotNull(shapes.issue, materials.issue)
        issues.addAll(flashIssues)
        flashIssue = flashIssues.firstOrNull()?.message
        worst = shapes.worse(materials)
    }
    return NameplateCost(
        report.quads,
        report.cacheBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        CostBudget.cpu(bundle),
        layers.toTypedArray(),
        flashIssue,
        worst?.times,
        worst?.pixels,
        issues.map { it.toJs() }.toTypedArray(),
        ReviewWarnings.warnings(bundle).map { it.toJs() }.toTypedArray(),
        report.materialBuildOps.toDouble(),
        fallbackCosts(bundle),
        CostBudget.infos(bundle).map { it.toJs() }.toTypedArray(),
    )
}
