package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.1 || = 1.8.9 {
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialOp
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import java.util.Collections
import java.util.WeakHashMap

object NameplateMaterialFallbacks {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val WINDOW_NANOS = 16_000_000L
    private const val BUDGET_NANOS = 1_000_000L

    private var windowStart = 0L
    private var spent = 0L
    private val textures = WeakHashMap<NameplateBundle, MaterialTextures>()
    private val logged: MutableSet<MaterialLayer> = Collections.newSetFromMap(WeakHashMap())

    fun painted(bundle: NameplateBundle, op: LayerPainter.Painted, material: MaterialOp): LayerPainter.Painted {
        if (op.opacity <= 0) {
            return op
        }
        val image = image(bundle, op, material)
        return if (image === op.image) op else op.copy(image = image)
    }

    fun image(bundle: NameplateBundle, op: LayerPainter.Painted, material: MaterialOp): PixelImage {
        val fallback = material.fallback
        if (material.drift && logged.add(material.layer)) {
            LOGGER.info("Nameplate {} layer {} drifts without repeating, so the CPU fallback rebuilds it at each frame step", bundle.id, material.layerId)
        } else if (fallback.still && logged.add(material.layer)) {
            LOGGER.info(
                "Nameplate {} layer {} shows frame 0 on the CPU fallback: all {} frames would cache {} bytes, over the {} byte cap",
                bundle.id, material.layerId, fallback.frames, fallback.animatedBytes, BundleLimits.MAX_FALLBACK_BYTES,
            )
        }
        if (op.image.rectCount > 0 && !fallback.still && !material.drift) {
            return op.image
        }
        material.cachedFallback()?.let { return it }
        val now = System.nanoTime()
        if (now - windowStart > WINDOW_NANOS) {
            windowStart = now
            spent = 0L
        }
        if (spent >= BUDGET_NANOS) {
            return material.cachedFallback(still = true) ?: PixelImage.EMPTY
        }
        val image = material.fallbackImage(textures(bundle))
        spent += System.nanoTime() - now
        return image
    }

    fun textures(bundle: NameplateBundle): MaterialTextures =
        textures.getOrPut(bundle) { MaterialTextures { ref -> NameplateMaterialAssets.pixels(bundle, ref) } }
}
//?}
