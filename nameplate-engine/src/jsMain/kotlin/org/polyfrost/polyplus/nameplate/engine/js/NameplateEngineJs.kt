@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Uint8ClampedArray
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.polyfrost.polyplus.nameplate.engine.api.anim.badgePose
import org.polyfrost.polyplus.nameplate.engine.api.anim.reactedPalette
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Layout
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.paint.Detail
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleBytes
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.PLATE_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor

@JsExport
class NameplateFile(val path: String, val text: String)

@JsExport
class NameplateBinary(val path: String, val bytes: Uint8Array)

@JsExport
class NameplatePixels(val path: String, val width: Int, val height: Int, val rgba: Uint8Array)

@JsExport
class NameplateIssue internal constructor(
    val path: String,
    val message: String,
    val code: String,
    val level: String,
    val values: dynamic,
    val names: dynamic,
)

internal fun BundleIssue.toJs(): NameplateIssue {
    val values: dynamic = js("({})")
    for ((name, value) in this.values) {
        values[name] = value
    }
    val names: dynamic = js("({})")
    for ((name, value) in this.names) {
        names[name] = value
    }
    return NameplateIssue(path, message, code.id, level.name.lowercase(), values, names)
}

@JsExport
class NameplateLoad internal constructor(
    val preview: NameplatePreview?,
    val skipped: String?,
    val issues: Array<NameplateIssue>,
)

@JsExport
class NameplateBlock internal constructor(internal val block: TagBlock) {
    val width: Int
        get() = block.width

    val height: Int
        get() = block.height

    fun placed(prefixWidth: Int, x0: Int, y0: Int, x1: Int, y1: Int): NameplateBlock =
        NameplateBlock(block.placed(prefixWidth, PixelRect(x0, y0, x1, y1)))
}

@JsExport
class NameplateLayer internal constructor(
    val index: Int,
    val depth: String,
    val x: Int,
    val y: Int,
    val mirrorX: Boolean,
    val width: Int,
    val opacity: Int,
    val left: Int,
    val top: Int,
    val rasterWidth: Int,
    val rasterHeight: Int,
    val rgba: Uint8ClampedArray,
    val blend: String,
    val quads: Int,
    val layer: String,
    val mask: String?,
)

@JsExport
object NameplateLayout {
    val anchors: Array<String> = OrnamentAnchor.NAMES.toTypedArray()
    val atAnchors: Array<String> = OrnamentAnchor.NAMES.filter { OrnamentAnchor.takesAt(it) }.toTypedArray()
    val nameAnchors: Array<String> = arrayOf(OrnamentAnchor.NAME_LEFT, OrnamentAnchor.NAME_RIGHT)
    val nameMask: String = NAME_TARGET
    val capability: String = Layout.CAPABILITY
    val plateTarget: String = PLATE_TARGET
}

@JsExport
class NameplateBadgePose internal constructor(
    val dx: Int,
    val dy: Int,
    val opacity: Int,
    val visible: Boolean,
)

@JsExport
class NameplatePreview internal constructor(
    internal val bundle: NameplateBundle,
    textures: MaterialTextures = MaterialTextures.NONE,
) {
    private val painter = LayerPainter(PixelGrid(), textures = textures)
    private var level = Detail.FULL

    var detail: String
        get() = level.name.lowercase()
        set(value) {
            level = requireNotNull(Detail.named(value)) { "detail must be full or reduced" }
        }

    val id: String
        get() = bundle.id

    val fps: Int
        get() = bundle.fps

    val loop: Int
        get() = bundle.loop

    val variants: Array<String>
        get() = bundle.variants.keys.toTypedArray()

    fun ownsBackground(variant: String): Boolean = bundle.variant(variant).ownsBackground

    val reactions: Array<String>
        get() = bundle.triggers.reactions.map { it.id }.toTypedArray()

    val oneShots: Array<String>
        get() = bundle.triggers.oneShots.map { it.name }.toTypedArray()

    fun paint(
        variant: String,
        block: NameplateBlock,
        frame: Int,
        seed: Int,
        state: NameplateState? = null,
    ): Array<NameplateLayer> {
        val resolved = bundle.variant(variant)
        val palette = reactedPalette(
            bundle,
            resolved,
            painter.palette(bundle, resolved, frame, seed, block.block),
            frame,
            seed,
            state?.state ?: TagState.NONE,
        )
        val plate = painter.plate(bundle, resolved, block.block, frame, seed, state?.state ?: TagState.NONE)
        val rasters = HashMap<Pair<PixelImage, PixelRect?>, Raster>()
        return painter.paint(bundle, resolved, block.block, frame, seed, state?.state ?: TagState.NONE, level)
            .map { op ->
                val slot = bundle.layers[op.index]
                val layer = bundle.layer(resolved.swaps[slot.id] ?: slot.id)
                val raster = rasters.getOrPut(op.image to op.clip) {
                    rasterize(if (op.image.usesPalette) op.image.resolve(palette) else op.image.resolve(), op.clip)
                }
                NameplateLayer(
                    op.index,
                    op.depth.name.lowercase(),
                    op.x + plate.dx,
                    op.y + plate.dy,
                    op.mirrorX,
                    op.width,
                    op.opacity,
                    raster.left,
                    raster.top,
                    raster.width,
                    raster.height,
                    raster.rgba,
                    (op.material?.material?.blend ?: Material.Blend.ALPHA).name.lowercase(),
                    op.material?.quads ?: op.image.quadCount,
                    layer.id,
                    layer.mask,
                )
            }
            .toTypedArray()
    }

    fun maskOf(layer: String): String? = bundle.layers.firstOrNull { it.id == layer }?.mask

    fun bodyRect(layer: String, block: NameplateBlock): IntArray? {
        val body = bundle.layers.firstOrNull { it.id == layer }?.content as? NameplateBundle.LayerContent.Body ?: return null
        val rect = body.rect(block.block)
        return intArrayOf(rect.x0, rect.y0, rect.x1, rect.y1)
    }

    fun badgePose(frame: Int, seed: Int, state: NameplateState? = null): NameplateBadgePose {
        val pose = badgePose(bundle, frame, seed, state?.state ?: TagState.NONE)
        return NameplateBadgePose(pose.dx, pose.dy, pose.opacity, pose.visible)
    }

    fun platePose(variant: String, block: NameplateBlock, frame: Int, seed: Int, state: NameplateState? = null): NameplateBadgePose {
        val pose = painter.plate(bundle, bundle.variant(variant), block.block, frame, seed, state?.state ?: TagState.NONE)
        return NameplateBadgePose(pose.dx, pose.dy, pose.opacity, pose.visible)
    }

    fun badgeFollow(variant: String, block: NameplateBlock, frame: Int, seed: Int, state: NameplateState? = null): NameplateBadgePose {
        val pose = painter.badgeFollow(bundle, bundle.variant(variant), block.block, frame, seed, state?.state ?: TagState.NONE)
        return NameplateBadgePose(pose.dx, pose.dy, pose.opacity, pose.visible)
    }

    fun badgeSpot(variant: String, block: NameplateBlock): IntArray? {
        val anchor = bundle.variant(variant).badge.placement.anchor ?: return null
        return intArrayOf(anchor.x(block.block), anchor.y(block.block))
    }

    private fun clipped(rects: IntArray, clip: PixelRect): IntArray {
        val out = ArrayList<Int>()
        for (i in rects.indices step PixelImage.STRIDE) {
            val x0 = maxOf(rects[i], clip.x0)
            val y0 = maxOf(rects[i + 1], clip.y0)
            val x1 = minOf(rects[i + 2], clip.x1)
            val y1 = minOf(rects[i + 3], clip.y1)
            if (x1 > x0 && y1 > y0) {
                out += x0
                out += y0
                out += x1
                out += y1
                out += rects[i + PixelImage.REF]
            }
        }
        return out.toIntArray()
    }

    private class Raster(val left: Int, val top: Int, val width: Int, val height: Int, val rgba: Uint8ClampedArray)

    private fun rasterize(source: IntArray, clip: PixelRect?): Raster {
        val rects = if (clip == null) source else clipped(source, clip)
        if (rects.isEmpty()) return Raster(0, 0, 0, 0, Uint8ClampedArray(0))

        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (i in rects.indices step PixelImage.STRIDE) {
            x0 = minOf(x0, rects[i])
            y0 = minOf(y0, rects[i + 1])
            x1 = maxOf(x1, rects[i + 2])
            y1 = maxOf(y1, rects[i + 3])
        }

        val width = x1 - x0
        val height = y1 - y0
        val rgba = Uint8Array(width * height * 4)
        for (i in rects.indices step PixelImage.STRIDE) {
            val argb = rects[i + PixelImage.REF]
            for (y in rects[i + 1] until rects[i + 3]) {
                for (x in rects[i] until rects[i + 2]) {
                    val p = ((y - y0) * width + (x - x0)) * 4
                    rgba[p] = (argb ushr 16 and 0xFF).toByte()
                    rgba[p + 1] = (argb ushr 8 and 0xFF).toByte()
                    rgba[p + 2] = (argb and 0xFF).toByte()
                    rgba[p + 3] = (argb ushr 24).toByte()
                }
            }
        }
        return Raster(x0, y0, width, height, Uint8ClampedArray(rgba.buffer))
    }
}

@JsExport
fun nameplateLoad(
    files: Array<NameplateFile>,
    capabilities: Array<String>,
    binaries: Array<NameplateBinary> = emptyArray(),
    pixels: Array<NameplatePixels> = emptyArray(),
): NameplateLoad = load(files, capabilities, Assets(binaries, pixels), budgets = true)

@JsExport
fun nameplateLoadPreview(
    files: Array<NameplateFile>,
    capabilities: Array<String>,
    binaries: Array<NameplateBinary> = emptyArray(),
    pixels: Array<NameplatePixels> = emptyArray(),
): NameplateLoad = load(files, capabilities, Assets(binaries, pixels), budgets = false)

private class Assets(binaries: Array<NameplateBinary>, pixels: Array<NameplatePixels>) {
    private val bytes = binaries.associate { it.path to byteArrayOf(it.bytes) }
    private val textures = pixels.associate { it.path to texturePixels(it) }

    val source: BundleBytes = BundleBytes { bytes[it] }

    val pixels: MaterialTextures = MaterialTextures { ref ->
        if (ref is TextureRef.Bundle) textures["textures/${ref.id}.png"] else null
    }

    private fun byteArrayOf(bytes: Uint8Array): ByteArray =
        Int8Array(bytes.buffer, bytes.byteOffset, bytes.length).unsafeCast<ByteArray>().copyOf()

    private fun texturePixels(pixels: NameplatePixels): TexturePixels? {
        val count = pixels.width * pixels.height
        if (pixels.width <= 0 || pixels.height <= 0 || pixels.rgba.length < count * CHANNELS) return null
        val rgba = pixels.rgba
        val argb = IntArray(count) { i ->
            val p = i * CHANNELS
            (rgba[p + ALPHA].toInt() and BYTE shl ALPHA_SHIFT) or
                (rgba[p].toInt() and BYTE shl RED_SHIFT) or
                (rgba[p + 1].toInt() and BYTE shl GREEN_SHIFT) or
                (rgba[p + 2].toInt() and BYTE)
        }
        return TexturePixels(pixels.width, pixels.height, argb)
    }

    private companion object {
        const val CHANNELS = 4
        const val ALPHA = 3
        const val BYTE = 0xFF
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}

private fun load(files: Array<NameplateFile>, capabilities: Array<String>, assets: Assets, budgets: Boolean): NameplateLoad {
    val source = BundleSource.of(files.associate { it.path to it.text })
    return when (val load = BundleLoader(capabilities.toSet()).load(source, assets.source, budgets = budgets)) {
        is BundleLoad.Loaded -> NameplateLoad(NameplatePreview(load.bundle, assets.pixels), null, emptyArray())
        is BundleLoad.Skipped -> NameplateLoad(null, load.reason, emptyArray())
        is BundleLoad.Invalid -> NameplateLoad(null, null, load.issues.map { it.toJs() }.toTypedArray())
    }
}

@JsExport
fun nameplateLineBlock(textWidth: Int, nameX: Int, nameWidth: Int): NameplateBlock =
    NameplateBlock(TagBlock.line(textWidth, nameX, nameWidth))

@JsExport
fun nameplateDisplayBlock(
    widths: IntArray,
    maxWidth: Int,
    align: String,
    nameLine: Int,
    nameX: Int,
    nameWidth: Int,
    badge: Boolean,
): NameplateBlock = NameplateBlock(
    TagBlock.display(
        widths,
        widths.size,
        maxWidth,
        TagBlock.Align.valueOf(align.uppercase()),
        nameLine,
        nameX,
        nameWidth,
        badge,
    ),
)
