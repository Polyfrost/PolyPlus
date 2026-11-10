package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.anim.badgePose
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.AnimationCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.BADGE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks

internal object ReviewWarnings {
    private const val FADED_BELOW = 128
    private const val COVERED_PERCENT = 25
    private const val COVERAGE_PERCENT = 50
    private const val SOLID_ALPHA = 128
    private const val AREA_TIMES = 4
    private const val VISIBLE_ALPHA = 32
    private const val TYPICAL_NAME = 27
    private const val MAX_FRAMES = 60
    private const val TENTHS = 10
    private const val OPAQUE = 255
    private const val PERCENT = 100
    private const val TRACKS = "tracks"

    fun warnings(bundle: NameplateBundle): List<BundleIssue> =
        ReadabilityValidator.validate(bundle) + badgeTracks(bundle) + plateBackground(bundle) + Scan(bundle).validate() +
            PositionClampValidator.validate(bundle)

    private fun plateBackground(bundle: NameplateBundle): List<BundleIssue> {
        if (bundle.plateChain.isEmpty()) return emptyList()
        val left = bundle.variants.values.filterNot { it.ownsBackground }.map { it.key }
        if (left.isEmpty()) return emptyList()
        return listOf(
            BundleIssue(
                "${BundleFiles.MANIFEST}#animations.$PLATE_TARGET",
                "moves the whole plate, but the vanilla background stays still in ${left.joinToString()}; " +
                    "set ownsBackground so nothing is left behind",
                IssueCode.PLATE_BACKGROUND,
                issueValues("variants" to left.size),
            ),
        )
    }

    private class Span(val path: String, val frames: Int, val persistent: Boolean, val opacityOf: (Int) -> Int)

    private fun badgeTracks(bundle: NameplateBundle): List<BundleIssue> {
        val spans = ArrayList<Span>()
        bundle.loopChannels[BADGE_TARGET]?.let { spans += span(trackPath(AnimationCompiler.LOOP), bundle.loop, true, it) }
        for (reaction in bundle.triggers.whiles) {
            reaction.channels(BADGE_TARGET)?.let { spans += span(trackPath(reaction.name), bundle.loop, true, it) }
        }
        for (shot in bundle.triggers.oneShots) {
            shot.channels(BADGE_TARGET)?.let { spans += span(trackPath(shot.name), shot.length, false, it) }
        }

        val issues = ArrayList<BundleIssue>()
        for (span in spans) {
            var hidden = 0
            var faded = 0
            var lowest = OPAQUE
            for (frame in 0 until span.frames) {
                val opacity = span.opacityOf(frame)
                when {
                    opacity <= 0 -> hidden++
                    opacity < FADED_BELOW -> {
                        faded++
                        lowest = minOf(lowest, opacity)
                    }
                }
            }
            val brief = if (span.persistent) 0 else bundle.fps
            if (hidden > brief) {
                issues += BundleIssue(
                    span.path,
                    "hides the PolyPlus badge for $hidden of ${span.frames} frames; the reviewer checks that the badge stays visible",
                    IssueCode.BADGE_HIDDEN,
                    issueValues("frames" to hidden, "of" to span.frames),
                )
            }
            if (faded > brief) {
                val percent = lowest * PERCENT / OPAQUE
                issues += BundleIssue(
                    span.path,
                    "fades the PolyPlus badge to $percent% opacity for $faded of ${span.frames} frames; " +
                        "below ${FADED_BELOW * PERCENT / OPAQUE}% the reviewer checks that the badge stays visible",
                    IssueCode.BADGE_FADED,
                    issueValues("opacity" to percent, "frames" to faded, "of" to span.frames, "limit" to FADED_BELOW * PERCENT / OPAQUE),
                )
            }
        }
        return issues
    }

    private fun span(path: String, frames: Int, persistent: Boolean, tracks: ChannelTracks): Span =
        Span(path, frames, persistent) { frame ->
            val visible = tracks.visible?.at(frame) ?: 1
            if (visible == 0) 0 else tracks.opacity?.at(frame) ?: OPAQUE
        }

    private fun trackPath(animation: String): String = "${BundleFiles.MANIFEST}#animations.$animation.$TRACKS.$BADGE_TARGET"

    private class Coverage(val percent: Int, val pixels: Int)

    private class Raster(val region: PixelRect) {
        val width: Int = region.width
        val covered = BooleanArray(region.width * region.height)
        val marked = BooleanArray(region.width * region.height)

        fun clear() {
            covered.fill(false)
        }

        inline fun each(x0: Int, y0: Int, x1: Int, y1: Int, visit: (Int) -> Unit) {
            val left = maxOf(x0, region.x0)
            val right = minOf(x1, region.x1)
            for (y in maxOf(y0, region.y0) until minOf(y1, region.y1)) {
                val row = (y - region.y0) * width - region.x0
                for (x in left until right) {
                    visit(row + x)
                }
            }
        }

        fun cover(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            var added = 0
            each(x0, y0, x1, y1) { pixel ->
                if (!covered[pixel]) {
                    covered[pixel] = true
                    added++
                }
            }
            return added
        }
    }

    private class Scan(private val bundle: NameplateBundle) {
        private val painter = LayerPainter(PixelGrid(), ByteLruCache<LayerKey, PixelImage>(Long.MAX_VALUE, ::imageBytes))
        private val variants = PlacedLayers.distinct(bundle, byPalette = true)
        private val stride = maxOf(1, (bundle.loop + MAX_FRAMES - 1) / MAX_FRAMES)
        private val frames = (0 until bundle.loop step stride).toList()
        private val held: List<Reaction?> = listOf<Reaction?>(null) + bundle.triggers.whiles.map { it.reaction }.distinct()

        fun validate(): List<BundleIssue> = listOfNotNull(area() ?: coverage(), fixedHeight() ?: sameOnEveryLine()) + covered() + overlaps()

        private inline fun eachFrame(visit: (NameplateBundle.Variant, Int) -> Unit) {
            for (variant in variants) {
                for (frame in frames) {
                    visit(variant, frame)
                }
            }
        }

        private fun stateOf(reaction: Reaction?): TagState =
            if (reaction == null) TagState.NONE else TagState.of(TagInputs.of(reaction))

        private fun placed(
            block: TagBlock,
            variant: NameplateBundle.Variant,
            frame: Int,
            state: TagState = TagState.NONE,
            materials: Boolean = false,
            visit: (LayerPainter.Painted, IntArray) -> Unit,
        ) {
            val palette = painter.palette(bundle, variant, frame, 0, block)
            for (op in painter.paint(bundle, variant, block, frame, 0, state)) {
                if ((op.material != null && !materials) || op.opacity <= 0) continue
                val image = op.image
                visit(op, if (image.usesPalette) image.resolve(palette) else image.resolve())
            }
        }

        private fun rects(op: LayerPainter.Painted, rects: IntArray, visit: (Int, Int, Int, Int, Int) -> Unit) {
            for (r in rects.indices step PixelImage.STRIDE) {
                val x0 = if (op.mirrorX) op.x + op.width - rects[r + 2] else op.x + rects[r]
                val x1 = if (op.mirrorX) op.x + op.width - rects[r] else op.x + rects[r + 2]
                val alpha = ((rects[r + PixelImage.REF] ushr 24) * op.opacity + OPAQUE / 2) / OPAQUE
                if (alpha > 0) {
                    visit(x0, op.y + rects[r + 1], x1, op.y + rects[r + 3], alpha)
                }
            }
        }

        private fun region(block: TagBlock): PixelRect = block.background.expand(BundleLimits.MAX_DECORATION_MARGIN)

        private fun coverage(): BundleIssue? {
            var worst: Coverage? = null
            for (block in ReferencePlacements.ALL) {
                val background = block.background
                val raster = Raster(region(block))
                val ring = raster.covered.size - background.width * background.height
                eachFrame { variant, frame ->
                    raster.clear()
                    var count = 0
                    placed(block, variant, frame, materials = true) { op, rects ->
                        rects(op, rects) { x0, y0, x1, y1, _ ->
                            raster.each(x0, y0, x1, y1) { pixel ->
                                if (!raster.covered[pixel] && !inside(raster, pixel, background)) {
                                    raster.covered[pixel] = true
                                    count++
                                }
                            }
                        }
                    }
                    val percent = count * PERCENT / maxOf(1, ring)
                    if (percent > (worst?.percent ?: -1)) {
                        worst = Coverage(percent, count)
                    }
                }
            }
            val found = worst ?: return null
            if (found.percent < COVERAGE_PERCENT) return null
            return BundleIssue(
                BundleFiles.MANIFEST,
                "art covers ${found.percent}% of the ${BundleLimits.MAX_DECORATION_MARGIN} px margin around the tag " +
                    "(${found.pixels} px), hiding much of the view behind it; the reviewer checks from $COVERAGE_PERCENT%",
                IssueCode.ART_COVERAGE,
                issueValues("percent" to found.percent, "px" to found.pixels, "limit" to COVERAGE_PERCENT),
            )
        }

        private fun area(): BundleIssue? {
            val block = TagBlock.line(TYPICAL_NAME, 0, TYPICAL_NAME)
            val plain = block.background.width * block.background.height
            val raster = Raster(region(block))
            var most = 0
            eachFrame { variant, frame ->
                raster.clear()
                var count = 0
                placed(block, variant, frame, materials = true) { op, rects ->
                    rects(op, rects) { x0, y0, x1, y1, alpha ->
                        if (alpha >= VISIBLE_ALPHA) {
                            count += raster.cover(x0, y0, x1, y1)
                        }
                    }
                }
                most = maxOf(most, count)
            }
            if (most < plain * AREA_TIMES) return null
            val tenths = most * TENTHS / plain
            val times = "${tenths / TENTHS}.${tenths % TENTHS}"
            return BundleIssue(
                BundleFiles.MANIFEST,
                "the art covers $most px, $times times the $plain px of a plain one-line name tag, hiding much of the view " +
                    "behind the player; the reviewer checks from $AREA_TIMES times",
                IssueCode.ART_COVERAGE,
                issueValues("times" to tenths.toDouble() / TENTHS, "px" to most, "limit" to AREA_TIMES),
            )
        }

        private fun inside(raster: Raster, pixel: Int, rect: PixelRect): Boolean {
            val x = pixel % raster.width + raster.region.x0
            val y = pixel / raster.width + raster.region.y0
            return x >= rect.x0 && x < rect.x1 && y >= rect.y0 && y < rect.y1
        }

        private fun extent(block: TagBlock): IntArray? {
            var top = Int.MAX_VALUE
            var bottom = Int.MIN_VALUE
            val region = region(block)
            eachFrame { variant, frame ->
                placed(block, variant, frame) { op, rects ->
                    rects(op, rects) { x0, y0, x1, y1, _ ->
                        if (x1 > maxOf(x0, region.x0) && minOf(x1, region.x1) > x0 && y1 > y0) {
                            top = minOf(top, maxOf(y0, region.y0))
                            bottom = maxOf(bottom, minOf(y1, region.y1))
                        }
                    }
                }
            }
            return if (top < bottom) intArrayOf(top, bottom) else null
        }

        private fun spans(block: TagBlock): Boolean {
            val extent = extent(block) ?: return false
            return extent[0] <= block.background.y0 && extent[1] >= block.background.y1
        }

        private fun fixedHeight(): BundleIssue? {
            val all = ReferencePlacements.ALL
            for (one in all.filter { it.lineCount == 1 && it.width == ReferencePlacements.MEDIUM }) {
                if (!spans(one)) continue
                val taller = all.firstOrNull { it.lineCount > 1 && it.nameLineWidth == one.width && !spans(it) } ?: continue
                return BundleIssue(
                    BundleFiles.MANIFEST,
                    "the art frames a one-line tag but not a ${taller.lineCount}-line one: nothing grows with the block. " +
                        "Use a body, anchor art to the block's edges or repeat it so it works on 2- and 3-line tags",
                    IssueCode.FIXED_HEIGHT,
                    issueValues("lines" to taller.lineCount),
                )
            }
            return null
        }

        private fun shapes(block: TagBlock): List<Long> {
            val shapes = ArrayList<Long>()
            eachFrame { variant, frame ->
                val bounds = LinkedHashMap<Int, IntArray>()
                val palette = painter.palette(bundle, variant, frame, 0, block)
                for (op in painter.paint(bundle, variant, block, frame, 0)) {
                    if (op.opacity <= 0) continue
                    val area = op.material?.area
                    val box = bounds.getOrPut(op.index) { intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE) }
                    if (area != null) {
                        grow(box, area.x0, area.y0, area.x1, area.y1)
                        continue
                    }
                    val image = op.image
                    val resolved = if (image.usesPalette) image.resolve(palette) else image.resolve()
                    rects(op, resolved) { x0, y0, x1, y1, _ -> grow(box, x0, y0, x1, y1) }
                }
                for ((index, box) in bounds) {
                    if (box[0] < box[2]) {
                        shapes += index.toLong()
                        box.forEach { shapes += it.toLong() }
                    }
                }
                shapes += Long.MIN_VALUE
            }
            return shapes
        }

        private fun grow(box: IntArray, x0: Int, y0: Int, x1: Int, y1: Int) {
            box[0] = minOf(box[0], x0)
            box[1] = minOf(box[1], y0)
            box[2] = maxOf(box[2], x1)
            box[3] = maxOf(box[3], y1)
        }

        private fun sameOnEveryLine(): BundleIssue? {
            val blocks = ReferencePlacements.ALL.filter { it.width == ReferencePlacements.MEDIUM }
            val one = blocks.firstOrNull { it.lineCount == 1 } ?: return null
            val shape = shapes(one)
            if (shape.none { it != Long.MIN_VALUE }) return null
            val taller = blocks.filter { it.lineCount > 1 }
            if (taller.isEmpty() || taller.any { shapes(it) != shape }) return null
            val lines = taller.maxOf { it.lineCount }
            return BundleIssue(
                BundleFiles.MANIFEST,
                "the art is the same on 1-, 2- and $lines-line tags: when a server adds a rank or team line, the art stays around " +
                    "the top line. Use a body, or anchor art to the block's bottom or sides, so it moves or grows with the lines",
                IssueCode.FIXED_HEIGHT,
                issueValues("lines" to lines),
            )
        }

        private fun badgeRect(
            variant: NameplateBundle.Variant,
            block: TagBlock,
            reserved: PixelRect,
            pose: LayerPose,
            follow: LayerPose,
        ): PixelRect {
            val anchor = variant.badge.placement.anchor
            val x = (if (anchor == null) reserved.x0 else anchor.x(block)) + pose.dx + follow.dx
            val y = (if (anchor == null) reserved.y0 else anchor.y(block)) + pose.dy + follow.dy
            return PixelRect(x, y, x + BadgeStyling.CELLS_WIDE, y + BadgeStyling.CELLS_HIGH)
        }

        private fun covered(): List<BundleIssue> {
            val most = LinkedHashMap<Int, Int>()
            val during = HashMap<Int, Reaction?>()
            val area = BadgeStyling.CELLS_WIDE * BadgeStyling.CELLS_HIGH
            for ((block, reserved) in ReferencePlacements.BADGED) {
                val raster = Raster(region(block))
                for (variant in variants) {
                    for (reaction in held) {
                        val state = stateOf(reaction)
                        for (frame in frames) {
                            val pose = badgePose(bundle, frame, 0, state)
                            if (!pose.visible || pose.opacity <= 0) continue
                            val follow = painter.badgeFollow(bundle, variant, block, frame, 0, state)
                            val badge = badgeRect(variant, block, reserved, pose, follow)
                            placed(block, variant, frame, state) { op, rects ->
                                if (op.depth != NameplateBundle.Depth.OVER_TEXT) return@placed
                                raster.clear()
                                var count = 0
                                rects(op, rects) { x0, y0, x1, y1, alpha ->
                                    if (alpha >= SOLID_ALPHA) {
                                        count += raster.cover(maxOf(x0, badge.x0), maxOf(y0, badge.y0), minOf(x1, badge.x1), minOf(y1, badge.y1))
                                    }
                                }
                                if (count > (most[op.index] ?: 0)) {
                                    most[op.index] = count
                                    during[op.index] = reaction
                                }
                            }
                        }
                    }
                }
            }
            return most.filter { it.value * PERCENT >= area * COVERED_PERCENT }.map { (index, count) ->
                val reaction = during[index]?.let { " while ${it.id.replace('_', ' ')}" } ?: ""
                BundleIssue(
                    BundleFiles.layerPath(bundle.layers[index].id),
                    "covers ${count * PERCENT / area}% of the PolyPlus badge ($count of $area px) over the text$reaction; " +
                        "the reviewer checks that the badge stays visible",
                    IssueCode.BADGE_COVERED,
                    issueValues("px" to count, "percent" to count * PERCENT / area, "limit" to COVERED_PERCENT),
                )
            }
        }

        private fun overlaps(): List<BundleIssue> {
            val most = LinkedHashMap<Int, Int>()
            for (block in listOf(ReferencePlacements.MEDIUM_LINE, ReferencePlacements.ALL[3])) {
                val raster = Raster(region(block))
                eachFrame { variant, frame ->
                    val ops = ArrayList<Pair<LayerPainter.Painted, IntArray>>()
                    placed(block, variant, frame) { op, rects -> ops += op to rects }
                    ops.sortWith(compareBy({ it.first.depth.ordinal }, { it.first.index }))
                    raster.clear()
                    for ((op, rects) in ops) {
                        var count = 0
                        raster.marked.fill(false)
                        rects(op, rects) { x0, y0, x1, y1, alpha ->
                            raster.each(x0, y0, x1, y1) { pixel ->
                                if (alpha < OPAQUE && raster.covered[pixel] && !raster.marked[pixel]) {
                                    raster.marked[pixel] = true
                                    count++
                                }
                            }
                        }
                        if (count > 0) {
                            most[op.index] = maxOf(most[op.index] ?: 0, count)
                        }
                        rects(op, rects) { x0, y0, x1, y1, _ -> raster.cover(x0, y0, x1, y1) }
                    }
                }
            }
            return most.map { (index, count) ->
                BundleIssue(
                    BundleFiles.layerPath(bundle.layers[index].id),
                    "draws $count translucent px over other layers; layers blend, so these pixels show the art under them " +
                        "and may look different from older previews",
                    IssueCode.TRANSLUCENT_OVERLAP,
                    issueValues("px" to count),
                )
            }
        }
    }
}
