package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialFallback
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures

class MaterialOp internal constructor(
    val layerId: String,
    val layer: MaterialLayer,
    val area: PixelRect,
    val body: PixelRect,
    val frame: MaterialFrame,
    val cycle: Int,
    private val painter: MaterialPainter,
    internal val bundle: NameplateBundle,
    internal val variant: NameplateBundle.Variant,
    val clip: PixelImage? = null,
    internal val shot: Boolean = false,
) {
    val quads: Int
        get() = clip?.rectCount ?: 1

    fun clipRects(): IntArray {
        val region = clip ?: return intArrayOf(area.x0, area.y0, area.x1, area.y1)
        val rects = region.geometry()
        val out = IntArray(region.rectCount * CLIP_STRIDE)
        for (r in 0 until region.rectCount) {
            rects.copyInto(out, r * CLIP_STRIDE, r * PixelImage.STRIDE, r * PixelImage.STRIDE + CLIP_STRIDE)
        }
        return out
    }

    internal fun clipped(region: PixelImage): MaterialOp =
        MaterialOp(layerId, layer, area, body, frame, cycle, painter, bundle, variant, region, shot)

    val material: Material
        get() = layer.material

    val shader: MaterialShader
        get() = layer.shader

    val key: String
        get() = layer.shader.key

    val clock: Int
        get() = frame.frame

    val fallback: MaterialFallback
        get() = layer.fallback

    val drift: Boolean
        get() = layer.drift

    fun shader(dialect: GlslDialect): MaterialShader = layer.shader(dialect)

    fun uniforms(
        palette: ResolvedPalette = frame.palette,
        atlas: FloatArray? = null,
        dialect: GlslDialect = GlslDialect.CORE_330,
    ): IntArray {
        val shown = if (palette === frame.palette) frame else frame.copy(palette = palette)
        return MaterialUniforms.std140(material, layer.shader(dialect).layout, shown, cycle, atlas)
    }

    val params: Map<String, Double>
        get() = frame.params

    fun fallbackImage(textures: MaterialTextures? = null): PixelImage {
        val shown = fallbackFrame(layer.fallback.still)
        return painter.image(this, shown, textures, shot && shown === frame)
    }

    fun cachedFallback(still: Boolean = false): PixelImage? {
        if (still && (layer.drift || shot)) {
            painter.held(this, frame, shot)?.let { return it }
        }
        val shown = fallbackFrame(still || layer.fallback.still)
        return painter.cached(this, shown, shot && shown === frame)
    }

    private fun fallbackFrame(still: Boolean): MaterialFrame =
        if (still) frame.copy(frame = 0, params = variant.params) else frame

    companion object {
        const val CLIP_STRIDE = 4
    }
}
