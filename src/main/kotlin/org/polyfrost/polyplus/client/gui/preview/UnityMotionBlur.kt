package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.ui.geometry.Offset
import org.apache.logging.log4j.LogManager
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Image as SkiaImage

object UnityMotionBlur {
    private const val MAX_BLUR = 0.08f
    private const val JITTER = 1.0f
    private const val MIN_SAMPLES = 4f

    private const val MAX_SAMPLES = 16f

    private const val SAMPLE_LIMIT = 32

    val NONE = Motion(Offset.Zero, MIN_SAMPLES)

    private val LOGGER = LogManager.getLogger("PolyPlus/MotionBlurPreview")

    @Volatile
    private var shaderUsable = true

    private val effect: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(SKSL) }.getOrNull()
    }

    fun maxSmear(strength: Int): Motion {
        val intensity = (strength.coerceIn(0, 10) / 10f) * MAX_BLUR
        if (intensity <= 1e-6f) return NONE
        val samples = (MIN_SAMPLES + (intensity / MAX_BLUR) * (MAX_SAMPLES - MIN_SAMPLES))
            .coerceIn(MIN_SAMPLES, MAX_SAMPLES)
        return Motion(Offset(intensity, 0f), samples)
    }

    fun draw(canvas: Canvas, image: SkiaImage, src: Rect, dst: Rect, bounds: Rect, motion: Motion) {
        if (!shaderUsable) return blit(canvas, image, src, dst, bounds)
        try {
            drawBlurred(canvas, image, src, dst, bounds, motion)
        } catch (e: LinkageError) {
            shaderUsable = false
            LOGGER.warn("Motion blur preview disabled; skiko API mismatch", e)
            blit(canvas, image, src, dst, bounds)
        }
    }

    private fun blit(canvas: Canvas, image: SkiaImage, src: Rect, dst: Rect, bounds: Rect) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawImageRect(image, src, dst, SamplingMode.LINEAR, null, true)
        canvas.restore()
    }

    private fun drawBlurred(canvas: Canvas, image: SkiaImage, src: Rect, dst: Rect, bounds: Rect, motion: Motion) {
        val localMatrix = Matrix33
            .makeTranslate(dst.left, dst.top)
            .makeConcat(Matrix33.makeScale(dst.width / src.width, dst.height / src.height))
            .makeConcat(Matrix33.makeTranslate(-src.left, -src.top))
        val source = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, localMatrix)
        source.use {
            val blurred = effect?.let { runtime ->
                RuntimeShaderBuilder(runtime).use { builder ->
                    builder.child("DiffuseSampler", source)
                    builder.uniform("Size", bounds.width, bounds.height)
                    builder.uniform("Velocity", motion.velocity.x, motion.velocity.y)
                    builder.uniform("Samples", motion.samples)
                    builder.uniform("Jitter", JITTER)
                    builder.makeShader()
                }
            }
            Paint().use { paint ->
                paint.shader = blurred ?: source
                canvas.drawRect(bounds, paint)
            }
            blurred?.close()
        }
    }

    data class Motion(val velocity: Offset, val samples: Float)

    private val SKSL = """
        uniform shader DiffuseSampler;
        uniform float2 Size;
        uniform float2 Velocity;
        uniform float Samples;
        uniform float Jitter;

        float gnoise(float2 p) {
            return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
        }

        half4 main(float2 coord) {
            int n = int(Samples);
            if (n < 2 || dot(Velocity, Velocity) < 1e-9) {
                return DiffuseSampler.eval(coord);
            }

            float j = (gnoise(coord) - 0.5) * Jitter;

            float4 acc = float4(0.0);
            float total = 0.0;
            for (int i = 0; i < $SAMPLE_LIMIT; i++) {
                if (i < n) {
                    float t = (float(i) + 0.5 + j) / float(n) - 0.5;
                    float w = 1.0 - abs(t) * 2.0;
                    acc += float4(DiffuseSampler.eval(coord + Velocity * Size * t)) * w;
                    total += w;
                }
            }

            return total > 0.0 ? half4(acc / total) : DiffuseSampler.eval(coord);
        }
    """.trimIndent()
}
