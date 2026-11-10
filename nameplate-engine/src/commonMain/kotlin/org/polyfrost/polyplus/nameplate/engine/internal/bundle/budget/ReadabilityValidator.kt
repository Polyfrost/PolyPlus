package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.text.BackdropPixels
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import kotlin.math.roundToInt

internal object ReadabilityValidator {
    const val WARN_BELOW = Contrast.LARGE_TEXT

    private const val TENTHS = 10.0
    private val PLACEMENTS: List<TagBlock> = listOf(
        TagBlock.line(ReferencePlacements.NARROW, 0, ReferencePlacements.NARROW),
        TagBlock.line(ReferencePlacements.MEDIUM, 0, ReferencePlacements.MEDIUM),
        ReferencePlacements.WIDEST_LINE,
    )

    fun validate(bundle: NameplateBundle): List<BundleIssue> {
        val painter = LayerPainter(PixelGrid(), ByteLruCache<LayerKey, PixelImage>(Long.MAX_VALUE, ::imageBytes))
        val issues = ArrayList<BundleIssue>()
        for (variant in bundle.variants.values) {
            val worst = worstRatio(painter, bundle, variant) ?: continue
            issues.add(
                BundleIssue(
                    "${BundleFiles.MANIFEST}#variants.${variant.key}",
                    "the name can be hard to read on this variant: its contrast with the art behind it drops to " +
                        "${tenths(worst)}:1, and ${tenths(WARN_BELOW)}:1 is readable. Set text.outline to " +
                        "\"auto\", or change the colors behind the name",
                    IssueCode.READABILITY,
                    issueValues("ratio" to worst, "target" to WARN_BELOW),
                ),
            )
        }
        return issues
    }

    private fun readable(text: ResolvedText): Boolean =
        text.outline != ResolvedText.UNSET || text.minRatio >= WARN_BELOW

    private fun worstRatio(painter: LayerPainter, bundle: NameplateBundle, variant: NameplateBundle.Variant): Double? {
        val styling = variant.text
        var worst: Double? = null
        for (block in PLACEMENTS) {
            val y0 = block.nameLineY
            val pixels = BackdropPixels.collect(
                painter.backdrop(bundle, variant, block),
                block.width,
                block.height,
                block.nameX,
                y0,
                block.nameX + block.nameWidth,
                y0 + TagBlock.GLYPH_HEIGHT,
                styling.backdropHint(),
            )
            if (pixels.isEmpty()) continue
            val resolved = styling.resolve(pixels, ResolvedText.UNSET)
            if (!readable(resolved)) {
                worst = minOf(worst ?: resolved.minRatio, resolved.minRatio)
            }
        }
        return worst
    }

    private fun tenths(ratio: Double): String {
        val rounded = (ratio * TENTHS).roundToInt()
        return "${rounded / TENTHS.toInt()}.${rounded % TENTHS.toInt()}"
    }
}
