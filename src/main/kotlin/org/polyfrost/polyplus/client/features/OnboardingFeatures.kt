package org.polyfrost.polyplus.client.features

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.internal.ui.themes.MinecraftDark
import org.polyfrost.oneconfig.internal.ui.themes.MinecraftLight
import org.polyfrost.oneconfig.internal.ui.themes.PolyGlassDark
import org.polyfrost.oneconfig.internal.ui.themes.PolyGlassLight
import org.polyfrost.oneconfig.internal.ui.themes.ThemeRegistry
import org.polyfrost.polyplus.client.PolyPlusConfig

object OnboardingFeatures {
    private val logger = LogManager.getLogger("PolyPlus/Onboarding")

    val polySprintAvailable: Boolean by lazy { classExists(POLYSPRINT_CONFIG) }
    val polyBlurAvailable: Boolean by lazy { classExists(POLYBLUR_CONFIG) }

    val modsPageAvailable: Boolean
        get() = polySprintAvailable

    @JvmStatic
    fun needsMotionBlurChoice(): Boolean =
        polyBlurAvailable && PolyPlusConfig.onboardingMotionBlurMode == MOTION_BLUR_UNSET

    fun initialize() {
        eventHandler { _: TickEvent.End ->
            if (!PolyPlusConfig.onboardingCompleted) return@eventHandler
            var changed = false
            if (!PolyPlusConfig.onboardingFeaturesApplied) {
                applyCoreSettings()
                changed = true
            }
            if (polySprintAvailable && !PolyPlusConfig.onboardingSprintApplied) {
                applyToggleSprint(PolyPlusConfig.onboardingToggleSprint)
                PolyPlusConfig.onboardingSprintApplied = true
                changed = true
            }
            if (polyBlurAvailable && !PolyPlusConfig.onboardingPolyBlurApplied) {
                if (applyMotionBlur(PolyPlusConfig.onboardingMotionBlurMode, PolyPlusConfig.onboardingMotionBlur)) {
                    PolyPlusConfig.onboardingPolyBlurApplied = true
                    changed = true
                }
            }
            if (changed) PolyPlusConfig.save()
        }
    }

    fun applySavedSettings() {
        applyCoreSettings()
        if (polySprintAvailable) {
            applyToggleSprint(PolyPlusConfig.onboardingToggleSprint)
            PolyPlusConfig.onboardingSprintApplied = true
        }
        if (applyMotionBlur(PolyPlusConfig.onboardingMotionBlurMode, PolyPlusConfig.onboardingMotionBlur)) {
            PolyPlusConfig.onboardingPolyBlurApplied = true
        }
        PolyPlusConfig.save()
    }

    fun applySavedMotionBlur() {
        if (applyMotionBlur(PolyPlusConfig.onboardingMotionBlurMode, PolyPlusConfig.onboardingMotionBlur)) {
            PolyPlusConfig.onboardingPolyBlurApplied = true
            PolyPlusConfig.save()
        }
    }

    private fun applyCoreSettings() {
        applyTheme(PolyPlusConfig.onboardingLightTheme, PolyPlusConfig.onboardingUiStyle)
        applyGuiScale(PolyPlusConfig.onboardingGuiScale)
        PolyPlusConfig.onboardingFeaturesApplied = true
    }

    fun applyGuiScale(value: Int, persist: Boolean = true) {
        runCatching {
            val mc = Minecraft.getInstance()
            mc.options.guiScale().set(value.coerceAtLeast(0))
            if (persist) mc.options.save()
            //? if >= 26.1 {
            mc.resizeGui()
            //?} else {
            /*mc.resizeDisplay()
            *///?}
        }.onFailure { logger.warn("Could not apply GUI scale preference", it) }
    }

    fun maxGuiScale(): Int = runCatching {
        val mc = Minecraft.getInstance()
        mc.window.calculateScale(0, mc.isEnforceUnicode)
    }.getOrDefault(4).coerceAtLeast(1)

    fun applyTheme(light: Boolean, style: Int) {
        val theme = when {
            style == 1 && light -> MinecraftLight
            style == 1 -> MinecraftDark
            light -> PolyGlassLight
            else -> PolyGlassDark
        }
        ThemeRegistry.activate(theme)
    }

    private fun applyToggleSprint(enabled: Boolean) {
        runCatching {
            Minecraft.getInstance().options.toggleSprint().set(enabled)
            Minecraft.getInstance().options.save()
        }.onFailure { logger.warn("Could not apply toggle sprint preference", it) }
    }

    fun applyMotionBlur(mode: Int, strength: Int): Boolean {
        if (mode == MOTION_BLUR_UNSET) return false
        return runCatching {
            val config = Class.forName(POLYBLUR_CONFIG)
            val instance = config.getField("INSTANCE").get(null)
            val performance = mode == MOTION_BLUR_PERFORMANCE
            setBoolean(instance, "setEnabled", mode != MOTION_BLUR_DISABLED)
            if (mode != MOTION_BLUR_DISABLED) {
                setFloat(instance, "setStrength", strength.coerceIn(1, 10).toFloat())
                setInt(instance, "setBlurType", if (performance) UNITY_BLUR_TYPE else HYBRID_BLUR_TYPE)
                setFloat(instance, "setMotionBlurSamples", if (performance) PERFORMANCE_SAMPLES else QUALITY_SAMPLES)
                setBoolean(instance, "setBlurHand", !performance) // >= 1.21.5 only; skipped if absent
            }
            config.getMethod("save").invoke(instance)
            true
        }.onFailure {
            if (it !is ClassNotFoundException) logger.warn("Could not apply PolyBlur preference", it)
        }.getOrDefault(false)
    }

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

    private fun classExists(name: String) = runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess

    const val MOTION_BLUR_UNSET = -1
    const val MOTION_BLUR_DISABLED = 0
    const val MOTION_BLUR_PERFORMANCE = 1
    const val MOTION_BLUR_QUALITY = 2

    private const val UNITY_BLUR_TYPE = 1  // blurType dropdown: 0=Phosphor, 1=Unity, 2=Hybrid
    private const val HYBRID_BLUR_TYPE = 2
    private const val PERFORMANCE_SAMPLES = 8f // motionBlurSamples slider range 4..32
    private const val QUALITY_SAMPLES = 16f
    private const val POLYSPRINT_CONFIG = "org.polyfrost.polysprint.client.PolySprintConfig"
    private const val POLYBLUR_CONFIG = "org.polyfrost.polyblur.client.PolyBlurConfig"
}
