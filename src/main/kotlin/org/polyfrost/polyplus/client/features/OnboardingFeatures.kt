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
        get() = polySprintAvailable || polyBlurAvailable

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
                if (applyPolyBlur(PolyPlusConfig.onboardingMotionBlur)) {
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
        if (applyPolyBlur(PolyPlusConfig.onboardingMotionBlur)) {
            PolyPlusConfig.onboardingPolyBlurApplied = true
        }
        PolyPlusConfig.save()
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

    private fun applyPolyBlur(value: Int): Boolean {
        val strength = value.coerceIn(0, 10)
        return runCatching {
            val config = Class.forName(POLYBLUR_CONFIG)
            val instance = config.getField("INSTANCE").get(null)
            config.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(instance, strength > 0)
            if (strength > 0) {
                config.getMethod("setStrength", Float::class.javaPrimitiveType).invoke(instance, strength.toFloat())
            }
            config.getMethod("save").invoke(instance)
            true
        }.onFailure {
            if (it !is ClassNotFoundException) logger.warn("Could not apply PolyBlur preference", it)
        }.getOrDefault(false)
    }

    private fun classExists(name: String) = runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess

    private const val POLYSPRINT_CONFIG = "org.polyfrost.polysprint.client.PolySprintConfig"
    private const val POLYBLUR_CONFIG = "org.polyfrost.polyblur.client.PolyBlurConfig"
}
