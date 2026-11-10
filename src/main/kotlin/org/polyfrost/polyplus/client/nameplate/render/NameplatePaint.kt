package org.polyfrost.polyplus.client.nameplate.render

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.nameplate.engine.api.anim.reactedPalette
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.DEFAULT_CACHE_BYTES
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.cache.imageBytes
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.paint.Detail
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.paint.PaintMemo
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.BackdropPixels
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedBadge
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.api.text.TextBackdrop
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import java.util.Collections
import java.util.WeakHashMap
//? if >= 1.21.1 || = 1.8.9 {
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialAssets
//?}
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateBinding
*///?}

object NameplatePaint {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private const val BACKDROP_SEED = 0
    private const val STYLING_CAPACITY = 1024
    private const val MAX_EXTERNALS = 8
    private val NO_PIXELS = IntArray(0)
    private val STILL = LayerPose(0, 0, LayerPose.OPAQUE, true)

    private val cache = ByteLruCache<LayerKey, PixelImage>(DEFAULT_CACHE_BYTES, ::imageBytes)
    private var textureBundle: NameplateBundle? = null
    //? if >= 1.21.1 || = 1.8.9 {
    private val textures = MaterialTextures { ref -> textureBundle?.let { NameplateMaterialAssets.pixels(it, ref) } }
    //?} else {
    /*private val textures = MaterialTextures.NONE
    *///?}
    private val painter = LayerPainter(PixelGrid(), cache, textures, materialFallbacks = false)

    private val texts = WeakHashMap<NameplateBundle.Variant, HashMap<Int, LruMap<ResolvedText>>>()
    private val badges = WeakHashMap<NameplateBundle.Variant, LruMap<ResolvedBadge>>()
    private val droppedVariants: MutableSet<NameplateBundle.Variant> = Collections.newSetFromMap(WeakHashMap())
    private val reportedDrops = HashSet<String>()
    private val memos = WeakHashMap<BoundNameplate, PaintMemo>()
    private var lastMemo: PaintMemo? = null
    private var lastNameplate: BoundNameplate? = null
    private var drops = 0

    fun paint(nameplate: BoundNameplate, block: TagBlock, frame: Int, state: TagState, detailed: Boolean): List<LayerPainter.Painted> {
        val detail = if (detailed && NameplateBinding.settings.motion == NameplateMotion.FULL) Detail.FULL else Detail.REDUCED
        val memo = memoOf(nameplate)
        if (!memo.hasOps(block, frame, state, detail, drops)) {
            val generation = drops
            val painted = withTextures(nameplate.bundle) {
                painter.paint(nameplate.bundle, nameplate.variant, block, frame, nameplate.seed, state, detail)
            }
            memo.keepOps(block, frame, state, detail, generation, painted, painter.dropped)
        }
        if (memo.opsDropped) {
            reportDropped(nameplate.bundle, nameplate.variant, PAINT_CALL)
        }
        return memo.ops
    }

    fun palette(nameplate: BoundNameplate, block: TagBlock, frame: Int): ResolvedPalette {
        val memo = memoOf(nameplate)
        if (!memo.hasPalette(block, frame, drops)) {
            val generation = drops
            val palette = painter.palette(nameplate.bundle, nameplate.variant, frame, nameplate.seed, block)
            memo.keepPalette(block, frame, generation, palette, painter.dropped)
        }
        if (memo.paletteDropped) {
            reportDropped(nameplate.bundle, nameplate.variant, PALETTE_CALL)
        }
        return memo.palette
    }

    fun reacted(nameplate: BoundNameplate, looped: ResolvedPalette, frame: Int, state: TagState): ResolvedPalette {
        val memo = memoOf(nameplate)
        if (!memo.hasReacted(looped, frame, state)) {
            memo.keepReacted(looped, frame, state, reactedPalette(nameplate.bundle, nameplate.variant, looped, frame, nameplate.seed, state))
        }
        return memo.reacted
    }

    fun plate(nameplate: BoundNameplate, block: TagBlock, frame: Int, state: TagState): LayerPose {
        if (textMode(nameplate.variant) == NameplateBundle.TextMode.VANILLA) {
            return STILL
        }
        return try {
            painter.plate(nameplate.bundle, nameplate.variant, block, frame, nameplate.seed, state)
        } catch (e: RuntimeException) {
            NameplateGate.fail(nameplate, e)
            STILL
        }
    }

    fun badgeFollow(nameplate: BoundNameplate, block: TagBlock, frame: Int, state: TagState): LayerPose =
        painter.badgeFollow(nameplate.bundle, nameplate.variant, block, frame, nameplate.seed, state)

    private fun textMode(variant: NameplateBundle.Variant): NameplateBundle.TextMode =
        //? if >= 1.21.1 {
        NameplateText.mode(variant)
        //?} else {
        /*LegacyNameplateBinding.mode(variant)
        *///?}

    private fun memoOf(nameplate: BoundNameplate): PaintMemo {
        if (nameplate === lastNameplate) {
            lastMemo?.let { return it }
        }
        val memo = memos.getOrPut(nameplate) { PaintMemo(nameplate.bundle, nameplate.variant, nameplate.seed) }
        lastNameplate = nameplate
        lastMemo = memo
        return memo
    }

    fun text(nameplate: BoundNameplate, block: TagBlock, externalRgb: Int): ResolvedText {
        val styling = nameplate.variant.text
        if (styling === TextStyling.VANILLA) {
            return ResolvedText.VANILLA
        }

        val byExternal = texts.getOrPut(nameplate.variant) { HashMap() }
        var entries = byExternal[externalRgb]
        if (entries == null) {
            if (byExternal.size >= MAX_EXTERNALS) {
                byExternal.clear()
            }
            entries = LruMap()
            byExternal[externalRgb] = entries
        }

        val sampled = styling.needsBackdrop
        val key = if (sampled) placementKey(block.width, block.height, block.nameLineY, block.nameX, block.nameWidth) else 0L
        return entries.getOrPut(key) {
            val pixels = if (sampled) {
                val y0 = block.nameLineY
                BackdropPixels.collect(
                    backdrop(nameplate, block),
                    block.width,
                    block.height,
                    block.nameX,
                    y0,
                    block.nameX + block.nameWidth,
                    y0 + TagBlock.GLYPH_HEIGHT,
                    styling.backdropHint(),
                )
            } else {
                NO_PIXELS
            }
            styling.resolve(pixels, externalRgb)
        }
    }

    fun badge(nameplate: BoundNameplate, block: TagBlock, x: Int, y: Int): ResolvedBadge {
        val key = placementKey(block.width, block.height, y, x, 0)
        return badges.getOrPut(nameplate.variant) { LruMap() }.getOrPut(key) {
            val pixels = BackdropPixels.collect(
                backdrop(nameplate, block),
                block.width,
                block.height,
                x,
                y,
                x + BadgeStyling.CELLS_WIDE,
                y + BadgeStyling.CELLS_HIGH,
                NO_PIXELS,
            )
            nameplate.variant.badge.resolve(pixels)
        }
    }

    private fun backdrop(nameplate: BoundNameplate, block: TagBlock): TextBackdrop {
        val bundle = nameplate.bundle
        val variant = nameplate.variant
        val range = painter.backdrop(bundle, variant, block, BACKDROP_SEED)
        return TextBackdrop { width, height, x0, y0, x1, y1, sink ->
            withTextures(bundle) { range.sample(width, height, x0, y0, x1, y1, sink) }
            if (painter.dropped) {
                reportDropped(bundle, variant, PAINT_CALL)
            }
        }
    }

    private inline fun <T> withTextures(bundle: NameplateBundle, paint: () -> T): T {
        val previous = textureBundle
        textureBundle = bundle
        try {
            return paint()
        } finally {
            textureBundle = previous
        }
    }

    private fun reportDropped(bundle: NameplateBundle, variant: NameplateBundle.Variant, call: String) {
        if (!droppedVariants.add(variant)) {
            return
        }
        drops++
        if (!reportedDrops.add("${bundle.id}:${variant.key}")) {
            return
        }
        LOGGER.warn("Nameplate {} variant {} dropped its Molang in {}; it draws without it until it reloads", bundle.id, variant.key, call)
        PolyPlusSentry.captureNameplateDropped(bundle.id, variant.key, call)
    }

    fun scaleAlpha(argb: Int, opacity: Int): Int {
        val alpha = ((argb ushr 24) * opacity + OPAQUE / 2) / OPAQUE
        return (alpha shl 24) or (argb and 0xFFFFFF)
    }

    private fun placementKey(width: Int, height: Int, row: Int, x: Int, size: Int): Long =
        (width.toLong() and 0xFFFFL shl 48) or
            (height.toLong() and 0xFFL shl 40) or
            (row.toLong() and 0xFFL shl 32) or
            (x.toLong() and 0xFFFFL shl 16) or
            (size.toLong() and 0xFFFFL)

    private const val OPAQUE = 255
    private const val PAINT_CALL = "paint"
    private const val PALETTE_CALL = "palette"

    private class LruMap<V> : LinkedHashMap<Long, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, V>?): Boolean = size > STYLING_CAPACITY
    }
}
