package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.anim.reactedPalette
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
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.paletteAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.color.Srgb
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame

internal class FlashResult(val times: Int, val pixels: Int, val issue: BundleIssue?) {
    fun worse(other: FlashResult): FlashResult = when {
        other.pixels != pixels -> if (other.pixels > pixels) other else this
        else -> if (other.times > times) other else this
    }

    companion object {
        val NONE: FlashResult = FlashResult(0, 0, null)

        fun flagged(path: String, summary: String, times: Int, pixels: Int): FlashResult = FlashResult(
            times,
            pixels,
            BundleIssue(
                path,
                "$summary (each pixel checked on its own for light-dark swings of 10% or more); " +
                    "the limit is ${BundleLimits.MAX_FLASHES} flashes over ${BundleLimits.MAX_FLASH_AREA} px",
                IssueCode.FLASH,
                issueValues(
                    "flashes" to times,
                    "px" to pixels,
                    "limit" to BundleLimits.MAX_FLASHES,
                    "area" to BundleLimits.MAX_FLASH_AREA,
                ),
            ),
        )
    }
}

internal object FlashValidator {
    private const val MANIFEST = BundleFiles.MANIFEST
    private const val WEIGHT_UNIT = 10_000
    private const val WEIGHT_RED = 2126
    private const val WEIGHT_GREEN = 7152
    private const val WEIGHT_BLUE = 722
    private const val MAX_SIM_FRAMES = 4800
    private const val EVENT_REPEATS = 4
    private const val OPAQUE = 255
    private const val FIELDS = 5
    private const val SENTINEL = -1

    private val LINEAR = IntArray(256) { (Srgb.linear(it) * FlashTiles.SCALE + 0.5).toInt() }

    private class Scenario(val plan: EventPlan, val frames: Int, val reactions: ReactionPlan = ReactionPlan.NONE)

    private class Scene(
        val variant: NameplateBundle.Variant,
        val block: TagBlock,
        val ops: List<Placed>,
        val prints: List<Footprint>,
        val shapes: ShapeSet,
    )

    fun validate(bundle: NameplateBundle): List<BundleIssue> = listOfNotNull(scan(bundle, full = false).issue)

    fun worst(bundle: NameplateBundle): FlashResult = scan(bundle, full = true)

    private fun scan(bundle: NameplateBundle, full: Boolean): FlashResult {
        if (!isAnimated(bundle)) return FlashResult.NONE

        val stripped = PlacedLayers.stripped(bundle)
        val grid = PixelGrid()
        val painter = LayerPainter(grid, ByteLruCache<LayerKey, PixelImage>(Long.MAX_VALUE, ::imageBytes), materialFallbacks = false)
        val shapes = ShapeSet(grid, bundle.variants.values.first().resolved)
        val blocks = if (bundle.molang == null) listOf(ReferencePlacements.WIDEST_LINE) else ReferencePlacements.ALL
        val variants = if (bundle.molang == null) PlacedLayers.distinct(bundle, byPalette = true) else bundle.variants.values
        var worst = FlashResult.NONE
        for (block in blocks) {
            val workspace = Workspace(PixelRect.vanillaBackground(block.width, block.height).expand(BundleLimits.MAX_DECORATION_MARGIN))
            for (variant in variants) {
                val ops = PlacedLayers.animated(bundle, stripped, painter, variant, block)
                val prints = Footprints(bundle, shapes, block)
                val scene = Scene(variant, block, ops, ops.map(prints::of), shapes)
                for (scenario in scenarios(bundle)) {
                    val run = Run(bundle, scene, scenario, workspace)
                    var outcome = run.simulate(light = false)
                    if (run.exposed && (full || outcome.pixels <= BundleLimits.MAX_FLASH_AREA)) {
                        val light = run.simulate(light = true)
                        outcome = if (full) outcome.worse(light) else light
                    }
                    val result = withIssue(outcome)
                    if (!full && result.issue != null) return result
                    worst = worst.worse(result)
                }
            }
        }

        return worst
    }

    private fun withIssue(outcome: FlashResult): FlashResult =
        if (outcome.pixels > BundleLimits.MAX_FLASH_AREA) {
            FlashResult.flagged(
                MANIFEST,
                "${outcome.pixels} px flash together ${outcome.times} times in one second",
                outcome.times,
                outcome.pixels,
            )
        } else {
            outcome
        }

    private fun isAnimated(bundle: NameplateBundle): Boolean =
        bundle.loopChannels.isNotEmpty() ||
            bundle.schedules.isNotEmpty() ||
            bundle.paletteTracks.isNotEmpty() ||
            !bundle.triggers.isEmpty ||
            bundle.layers.any { states(bundle, it).size > 1 } ||
            bundle.layers.any(DynamicSlots::isDynamic)

    private fun scenarios(bundle: NameplateBundle): List<Scenario> {
        val base = bundle.loop * maxOf(2, (bundle.fps + bundle.loop - 1) / bundle.loop + 1)
        val scenarios = arrayListOf(Scenario(NoEvents, base))
        val step = bundle.layers.maxOfOrNull { it.step } ?: 1
        var longest = 0
        for (schedule in bundle.schedules) {
            val ids = setOf(schedule.id)
            val spacing = BackToBack.spacing(schedule) * step
            for (event in schedule.events.indices) {
                scenarios.add(Scenario(BackToBack(ids, event), frames(base, spacing * EVENT_REPEATS)))
            }
            val cycle = spacing * (schedule.events.size + EVENT_REPEATS)
            scenarios.add(Scenario(BackToBack(ids), frames(base, cycle)))
            longest = maxOf(longest, cycle)
        }
        if (bundle.schedules.size > 1) {
            scenarios.add(Scenario(BackToBack(bundle.schedules.map { it.id }.toSet()), frames(base, longest)))
        }
        if (!bundle.triggers.isEmpty) {
            reactionScenarios(bundle, base, longest, scenarios)
        }

        return scenarios
    }

    private fun reactionScenarios(bundle: NameplateBundle, base: Int, longest: Int, into: MutableList<Scenario>) {
        val span = frames(base, ReactionPlan.span(bundle) * EVENT_REPEATS)
        val reactions = bundle.triggers.reactions
        for (reaction in reactions) {
            into.add(Scenario(NoEvents, span, ReactionPlan(reaction.bit, repeatShots = false)))
        }
        val repeats = ReactionPlan.repeats(bundle)
        if (repeats) {
            into.add(Scenario(NoEvents, span, ReactionPlan(0, repeatShots = true)))
        }
        val sources = reactions.size + (if (repeats) 1 else 0) + bundle.schedules.size
        if (sources > 1) {
            val schedules = BackToBack(bundle.schedules.map { it.id }.toSet())
            into.add(Scenario(schedules, maxOf(span, frames(base, longest)), ReactionPlan(ReactionPlan.maskOf(reactions), repeats)))
        }
    }

    private fun frames(base: Int, span: Int): Int = minOf(MAX_SIM_FRAMES, maxOf(base, span))

    private fun luminance(rgb: Int): Int =
        (
            WEIGHT_RED * LINEAR[(rgb shr 16) and 0xFF] +
                WEIGHT_GREEN * LINEAR[(rgb shr 8) and 0xFF] +
                WEIGHT_BLUE * LINEAR[rgb and 0xFF] +
                WEIGHT_UNIT / 2
            ) / WEIGHT_UNIT

    private class Box {
        var x0 = 0
        var y0 = 0
        var x1 = 0
        var y1 = 0

        val isEmpty: Boolean
            get() = x1 <= x0 || y1 <= y0

        fun set(x0: Int, y0: Int, x1: Int, y1: Int) {
            this.x0 = x0
            this.y0 = y0
            this.x1 = x1
            this.y1 = y1
        }

        fun add(ax0: Int, ay0: Int, ax1: Int, ay1: Int) {
            if (ax1 <= ax0 || ay1 <= ay0) return
            if (isEmpty) {
                set(ax0, ay0, ax1, ay1)
                return
            }

            x0 = minOf(x0, ax0)
            y0 = minOf(y0, ay0)
            x1 = maxOf(x1, ax1)
            y1 = maxOf(y1, ay1)
        }

        fun clip(to: PixelRect) {
            x0 = maxOf(x0, to.x0)
            y0 = maxOf(y0, to.y0)
            x1 = minOf(x1, to.x1)
            y1 = minOf(y1, to.y1)
        }

        fun meets(boxes: IntArray, at: Int): Boolean =
            boxes[at] < x1 && boxes[at + 2] > x0 && boxes[at + 1] < y1 && boxes[at + 3] > y0
    }

    private class Workspace(val region: PixelRect) {
        val width: Int = region.width
        val rgb = IntArray(width * region.height)
        val owner = IntArray(width * region.height)
    }

    private class Run(
        private val bundle: NameplateBundle,
        private val scene: Scene,
        private val scenario: Scenario,
        private val ws: Workspace,
    ) {
        private val ops = scene.ops
        private val prints = scene.prints
        private val variant = scene.variant
        private val motion = SimulatedMotion(bundle, scenario.plan)
        private val molang = bundle.molang?.let { MolangFrame(it, bundle.loop, bundle.fps) }
        private val region = ws.region
        private val dirty = Box()
        private val within = Box()
        private val current = Signature(ops.size)
        private val previous = Signature(ops.size)
        private val drawn = arrayOfNulls<Shape>(ops.size)
        private val tileBase = IntArray(ops.size)
        private val weights: IntArray
        private val sums: IntArray
        private val posX = IntArray(ops.size)
        private val posY = IntArray(ops.size)
        private val known = BooleanArray(ops.size)
        private val tracked = BooleanArray(ops.size)
        private val wasShown = BooleanArray(ops.size)
        private val touched = BooleanArray(ops.size)
        private var tiles = FlashTiles(IntArray(0), bundle.fps, 0)
        private var drawing = 0

        var exposed = false
            private set

        init {
            var total = 0
            for (i in ops.indices) {
                tileBase[i] = total
                total += prints[i].size
            }
            weights = IntArray(total)
            sums = IntArray(total)
        }

        private class Signature(count: Int) {
            val fields = IntArray(count * FIELDS)
            val boxes = IntArray(count * 4)
        }

        fun simulate(light: Boolean): FlashResult {
            val backdrop = if (light) 0xFFFFFF else 0
            reset(backdrop)
            val reactions = scenario.reactions.start(bundle)
            var palette = variant.resolved
            var before = palette
            var now = current
            var then = previous
            motion.molang = molang
            for (frame in 0 until scenario.frames) {
                molang?.bind(variant.params, scene.block, frame)
                val state = reactions?.at(frame) ?: TagState.NONE
                val looped = if (bundle.paletteTracks.isEmpty()) variant.resolved else paletteAt(bundle, variant, frame, molang)
                palette = reactedPalette(bundle, variant, looped, frame, 0, state)
                motion.tagState = state
                describe(frame, now)
                if (frame == 0) {
                    dirty.set(region.x0, region.y0, region.x1, region.y1)
                } else {
                    dirty.set(0, 0, 0, 0)
                    markChanged(now, then, samePalette(before, palette))
                    dirty.clip(region)
                }
                if (!dirty.isEmpty) {
                    compose(frame, now, palette, backdrop)
                }
                for (i in ops.indices) {
                    wasShown[i] = now.fields[i * FIELDS] == 1
                }

                before = palette
                val swap = now
                now = then
                then = swap
            }

            return FlashResult(tiles.mostTimes, tiles.mostPixels(scenario.frames), null)
        }

        private fun reset(backdrop: Int) {
            weights.fill(1)
            tiles = FlashTiles(weights, bundle.fps, scenario.frames)
            exposed = false
            known.fill(false)
            tracked.fill(false)
            wasShown.fill(false)
            touched.fill(false)
            sums.fill(luminance(backdrop))
        }

        private fun describe(frame: Int, into: Signature) {
            for ((i, op) in ops.withIndex()) {
                val pose = op.pose(motion, frame)
                val placed = op.dynamic == null || pose !== DynamicSlots.HIDDEN_POSE
                val state = op.state(motion, frame)
                val shape = scene.shapes.of(op, state)
                val shown = placed && pose.visible && pose.opacity > 0 && !shape.isEmpty
                drawn[i] = shape
                if (shown || (placed && !known[i])) {
                    posX[i] = op.x + pose.dx
                    posY[i] = op.y + pose.dy
                    known[i] = true
                }
                val f = i * FIELDS
                val b = i * 4
                if (!known[i]) {
                    into.fields.fill(0, f, f + FIELDS)
                    into.boxes.fill(0, b, b + 4)
                    continue
                }

                into.fields[f] = if (shown) 1 else 0
                into.fields[f + 1] = if (shown) state else 0
                into.fields[f + 2] = posX[i]
                into.fields[f + 3] = posY[i]
                into.fields[f + 4] = if (shown) pose.opacity else 0
                box(i, op, if (shown) shape else null, into.boxes, b)
            }
        }

        private fun box(index: Int, op: Placed, shape: Shape?, boxes: IntArray, offset: Int) {
            val ox = posX[index]
            val oy = posY[index]
            val print = prints[index]
            within.set(0, 0, 0, 0)
            within.add(
                span(op, ox, print.x0, print.x1, low = true),
                oy + print.y0,
                span(op, ox, print.x0, print.x1, low = false),
                oy + print.y1,
            )
            if (shape != null) {
                within.add(
                    span(op, ox, shape.x0, shape.x1, low = true),
                    oy + shape.y0,
                    span(op, ox, shape.x0, shape.x1, low = false),
                    oy + shape.y1,
                )
            }
            boxes[offset] = within.x0
            boxes[offset + 1] = within.y0
            boxes[offset + 2] = within.x1
            boxes[offset + 3] = within.y1
        }

        private fun span(op: Placed, ox: Int, x0: Int, x1: Int, low: Boolean): Int = when {
            op.mirrored && low -> ox + op.width - x1
            op.mirrored -> ox + op.width - x0
            low -> ox + x0
            else -> ox + x1
        }

        private fun markChanged(now: Signature, then: Signature, samePalette: Boolean) {
            for (i in ops.indices) {
                val f = i * FIELDS
                val b = i * 4
                var same = true
                for (k in 0 until FIELDS) {
                    if (now.fields[f + k] != then.fields[f + k]) {
                        same = false
                        break
                    }
                }
                if (!same) {
                    dirty.add(then.boxes[b], then.boxes[b + 1], then.boxes[b + 2], then.boxes[b + 3])
                }
                if (!same || !samePalette) {
                    dirty.add(now.boxes[b], now.boxes[b + 1], now.boxes[b + 2], now.boxes[b + 3])
                }
            }
        }

        private fun samePalette(first: ResolvedPalette, second: ResolvedPalette): Boolean =
            first === second ||
                first.copyColors().contentEquals(second.copyColors()) && first.copyStops().contentDeepEquals(second.copyStops())

        private fun compose(frame: Int, now: Signature, palette: ResolvedPalette, backdrop: Int) {
            for (y in dirty.y0 until dirty.y1) {
                val row = (y - region.y0) * ws.width - region.x0
                ws.rgb.fill(SENTINEL, row + dirty.x0, row + dirty.x1)
                ws.owner.fill(-1, row + dirty.x0, row + dirty.x1)
            }
            for ((i, op) in ops.withIndex()) {
                if (!known[i] || !dirty.meets(now.boxes, i * 4)) continue

                drawing = i
                val shown = now.fields[i * FIELDS] == 1
                if (!tracked[i] && frame > 0) {
                    read(i, op, frame, backdrop)
                    settle(i, op, frame, start = true)
                    tracked[i] = true
                }
                if (shown) {
                    draw(i, op, now.fields[i * FIELDS + 4], palette, backdrop)
                }
                if (shown || wasShown[i] || !tracked[i]) {
                    read(i, op, frame, backdrop)
                    touched[i] = true
                }
            }
            for ((i, op) in ops.withIndex()) {
                if (known[i] && dirty.meets(now.boxes, i * 4)) {
                    uncover(i, op)
                }
            }
            for ((i, op) in ops.withIndex()) {
                if (touched[i]) {
                    settle(i, op, frame, start = !tracked[i])
                    tracked[i] = true
                    touched[i] = false
                }
            }
        }

        private fun draw(index: Int, op: Placed, opacity: Int, palette: ResolvedPalette, backdrop: Int) {
            val rects = checkNotNull(drawn[index]).image.resolve(palette)
            val ox = posX[index]
            val oy = posY[index]
            for (r in rects.indices step PixelImage.STRIDE) {
                val x0 = if (op.mirrored) ox + op.width - rects[r + 2] else ox + rects[r]
                val x1 = if (op.mirrored) ox + op.width - rects[r] else ox + rects[r + 2]
                fill(x0, oy + rects[r + 1], x1, oy + rects[r + 3], rects[r + 4], opacity, backdrop)
            }
        }

        private inline fun inDirty(index: Int, op: Placed, visit: (index: Int, pixel: Int) -> Unit) {
            val print = prints[index]
            val ox = posX[index]
            val oy = posY[index]
            for (ly in maxOf(print.y0, dirty.y0 - oy) until minOf(print.y1, dirty.y1 - oy)) {
                val row = (oy + ly - region.y0) * ws.width - region.x0
                for (k in print.rows[ly - print.y0] until print.rows[ly - print.y0 + 1]) {
                    val x = if (op.mirrored) ox + op.width - 1 - print.xs[k] else ox + print.xs[k]
                    if (x >= dirty.x0 && x < dirty.x1) {
                        visit(k, row + x)
                    }
                }
            }
        }

        private fun read(index: Int, op: Placed, frame: Int, backdrop: Int) {
            val base = tileBase[index]
            inDirty(index, op) { k, pixel ->
                var color = ws.rgb[pixel]
                if (color == SENTINEL) {
                    exposed = exposed || frame > 0
                    color = backdrop
                }
                sums[base + k] = luminance(color)
            }
        }

        private fun uncover(index: Int, op: Placed) {
            val base = tileBase[index]
            inDirty(index, op) { k, pixel ->
                weights[base + k] = if (ws.owner[pixel] <= index) 1 else 0
            }
        }

        private fun settle(index: Int, op: Placed, frame: Int, start: Boolean) {
            val base = tileBase[index]
            if (start) {
                for (t in 0 until prints[index].size) {
                    tiles.start(base + t, sums[base + t], frame)
                }
                return
            }
            inDirty(index, op) { k, _ ->
                tiles.step(base + k, sums[base + k], frame)
            }
        }

        private fun fill(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int, opacity: Int, backdrop: Int) {
            val left = maxOf(x0, dirty.x0)
            val right = minOf(x1, dirty.x1)
            val top = maxOf(y0, dirty.y0)
            val bottom = minOf(y1, dirty.y1)
            val alpha = ((argb ushr 24) * opacity + OPAQUE / 2) / OPAQUE
            if (left >= right || top >= bottom || alpha == 0) return

            if (alpha == OPAQUE) {
                val color = argb and 0xFFFFFF
                for (y in top until bottom) {
                    val row = (y - region.y0) * ws.width - region.x0
                    ws.rgb.fill(color, row + left, row + right)
                    ws.owner.fill(drawing, row + left, row + right)
                }
                return
            }

            val inverse = OPAQUE - alpha
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            for (y in top until bottom) {
                val row = (y - region.y0) * ws.width - region.x0
                for (x in left until right) {
                    var under = ws.rgb[row + x]
                    if (under == SENTINEL) {
                        exposed = true
                        under = backdrop
                    }
                    val nr = (r * alpha + ((under shr 16) and 0xFF) * inverse + OPAQUE / 2) / OPAQUE
                    val ng = (g * alpha + ((under shr 8) and 0xFF) * inverse + OPAQUE / 2) / OPAQUE
                    val nb = (b * alpha + (under and 0xFF) * inverse + OPAQUE / 2) / OPAQUE
                    ws.rgb[row + x] = (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
    }
}
