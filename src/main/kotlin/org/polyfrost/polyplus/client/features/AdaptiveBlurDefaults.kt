package org.polyfrost.polyplus.client.features

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.apache.logging.log4j.LogManager
object AdaptiveBlurDefaults {
    private val logger = LogManager.getLogger("PolyPlus/AdaptiveBlur")

    private const val POLYBLUR_CONFIG = "org.polyfrost.polyblur.client.PolyBlurConfig"
    private const val POLYPLUS_PACKAGE = "org.polyfrost.polyplus."
    private const val FALLBACK_REFRESH_RATE = 60
    private const val WINDOW_MILLIS = 5_000L
    private const val WARMUP_MILLIS = 1_000L // discard while the lifted cap takes effect

    private const val LOW_FPS_MARGIN = 10    // refresh - 10: below this, no blur
    private const val HEADROOM_MARGIN = 30   // refresh + 30: below this, cheap Unity blur
    private const val HEADROOM_MARGIN_HI = 75 // refresh + 75: below this, just fewer samples
    private const val UNITY_BLUR_TYPE = 1    // blurType dropdown: 0=Phosphor, 1=Unity, 2=Hybrid
    private const val REDUCED_SAMPLES = 8f   // motionBlurSamples slider range 4..32
    private const val MEDIUM_SAMPLES = 12f

    var sampled by mutableStateOf(false)
        private set

    var recommendsPerformance by mutableStateOf(false)
        private set

    private var windowStartNanos = 0L
    private var fpsSum = 0L
    private var frameCount = 0

    @Volatile
    private var sampling = false

    @JvmStatic
    fun isSampling(): Boolean = sampling

    fun initialize() {
        eventHandler { event: MainMenuFpsEvent ->
            onSample(event.averageFps)
        }
        eventHandler { _: TickEvent.End ->
            tickSample()
        }
    }

    private fun tickSample() {
        if (sampled) return
        val mc = Minecraft.getInstance()
        val screen = currentScreen(mc)
        val onMenu = mc.player == null && screen != null && screen.javaClass.name.startsWith(POLYPLUS_PACKAGE)
        if (!onMenu) { // loading/other screen, or in a world: abandon the window and restore the cap
            abortWindow()
            return
        }

        if (!sampling) { // start of a fresh window
            sampling = true
            windowStartNanos = System.nanoTime()
            fpsSum = 0L
            frameCount = 0
        }

        val elapsedMillis = (System.nanoTime() - windowStartNanos) / 1_000_000L
        if (elapsedMillis >= WARMUP_MILLIS) {
            fpsSum += Platform.compatibility().fps().coerceAtLeast(0)
            frameCount++
        }
        if (elapsedMillis >= WARMUP_MILLIS + WINDOW_MILLIS) {
            sampling = false
            onSample(if (frameCount > 0) fpsSum.toFloat() / frameCount else 0f)
        }
    }

    private fun abortWindow() {
        if (sampling) {
            sampling = false
            fpsSum = 0L
            frameCount = 0
        }
    }

    private fun currentScreen(mc: Minecraft): Screen? =
        //? if >= 26.2 {
        /*mc.gui.screen()
        *///?} else
        mc.screen

    private fun onSample(averageFps: Float) {
        if (sampled) return
        val refreshRate = refreshRate()
        recommendsPerformance = averageFps < refreshRate - LOW_FPS_MARGIN
        if (!PolyPlusConfig.adaptiveBlurApplied) apply(averageFps, refreshRate)
        sampled = true
    }

    private fun apply(averageFps: Float, refreshRate: Int) {
        val instance = polyBlurInstance() ?: return // PolyBlur absent; retry on a later launch

        runCatching {
            when {
                averageFps < refreshRate - LOW_FPS_MARGIN -> {
                    setBoolean(instance, "setEnabled", false)
                    logger.info(
                        "Avg main-menu FPS {} below refresh {} - {}; disabling blur",
                        averageFps, refreshRate, LOW_FPS_MARGIN,
                    )
                }
                averageFps < refreshRate + HEADROOM_MARGIN -> {
                    setBoolean(instance, "setEnabled", true)
                    setInt(instance, "setBlurType", UNITY_BLUR_TYPE)
                    setFloat(instance, "setMotionBlurSamples", REDUCED_SAMPLES)
                    setBoolean(instance, "setBlurHand", false) // >= 1.21.5 only; skipped if absent
                    logger.info(
                        "Avg main-menu FPS {} below refresh {} + {}; Unity blur, hand blur off, {} samples",
                        averageFps, refreshRate, HEADROOM_MARGIN, REDUCED_SAMPLES.toInt(),
                    )
                }
                averageFps < refreshRate + HEADROOM_MARGIN_HI -> {
                    setFloat(instance, "setMotionBlurSamples", MEDIUM_SAMPLES)
                    logger.info(
                        "Avg main-menu FPS {} below refresh {} + {}; {} samples",
                        averageFps, refreshRate, HEADROOM_MARGIN_HI, MEDIUM_SAMPLES.toInt(),
                    )
                }
                else -> logger.info(
                    "Avg main-menu FPS {} has headroom over refresh {}; keeping PolyBlur defaults",
                    averageFps, refreshRate,
                )
            }
            instance.javaClass.getMethod("save").invoke(instance)
        }.onFailure { logger.warn("Could not apply adaptive blur defaults", it) }

        PolyPlusConfig.adaptiveBlurApplied = true
        PolyPlusConfig.save()
    }

    private fun refreshRate(): Int = runCatching {
        Minecraft.getInstance().window.refreshRate.takeIf { it > 0 } ?: FALLBACK_REFRESH_RATE
    }.getOrDefault(FALLBACK_REFRESH_RATE)

    private fun polyBlurInstance(): Any? = runCatching {
        Class.forName(POLYBLUR_CONFIG).getField("INSTANCE").get(null)
    }.getOrNull()

    private fun setBoolean(instance: Any, method: String, value: Boolean) {
        val fn = runCatching { instance.javaClass.getMethod(method, Boolean::class.javaPrimitiveType) }.getOrNull()
        if (fn == null) {
            logger.debug("PolyBlur has no {}, skipping", method)
            return
        }
        fn.invoke(instance, value)
    }

    private fun setInt(instance: Any, method: String, value: Int) {
        instance.javaClass.getMethod(method, Int::class.javaPrimitiveType).invoke(instance, value)
    }

    private fun setFloat(instance: Any, method: String, value: Float) {
        instance.javaClass.getMethod(method, Float::class.javaPrimitiveType).invoke(instance, value)
    }
}
