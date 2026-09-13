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
import org.polyfrost.polyplus.client.ThemeBrandingUtil
import org.polyfrost.polyplus.client.PolyPlusConfig

object OnboardingFeatures {
    private val logger = LogManager.getLogger("PolyPlus/Onboarding")
    private val warnedModApplyFailures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var nextModApplyAttemptMs = 0L

    @Volatile
    private var modApplyRetryDelayMs = MOD_APPLY_RETRY_INITIAL_MS

    @Volatile
    private var modApplyAttempts = 0

    val polySprintAvailable: Boolean by lazy { classExists(POLYSPRINT_CONFIG) }
    val polyBlurAvailable: Boolean by lazy { classExists(POLYBLUR_CONFIG) }
    val betterGrassAvailable: Boolean by lazy { classExists(LBG_MOD) }
    val fireOverlayAvailable: Boolean by lazy { hasFloatingField(OVERLAY_TWEAKS_CONFIG, FIRE_OVERLAY_HEIGHT) }
    val fireOverlayOpacityAvailable: Boolean by lazy {
        hasFloatingField(OVERLAY_TWEAKS_CONFIG, FIRE_OVERLAY_OPACITY)
    }
    val mountOpacityAvailable: Boolean by lazy { classExists(MOUNT_OPACITY_CONFIG) }
    val waveyCapesAvailable: Boolean by lazy { classExists(WAVEY_MOD_BASE) }
    val skinLayersAvailable: Boolean by lazy { classExists(SKIN_LAYERS_MOD_BASE) }
    val shieldHeightAvailable: Boolean by lazy {
        hasFloatingField(OVERLAY_TWEAKS_CONFIG, SHIELD_HEIGHT)
    }

    val itemPositionsAvailable: Boolean by lazy {
        runCatching {
            val extras = loadWithoutInit(ANIMATIUM_CONFIG).getField("extras").type
            ITEM_POSITION_FIELDS.forEach { extras.getField(it) }
        }.isSuccess
    }

    val modsPageAvailable: Boolean
        get() = polySprintAvailable || modCardCount > 0

    val modCardCount: Int
        get() = listOf(
            betterGrassAvailable,
            fireOverlayAvailable,
            shieldHeightAvailable,
            itemPositionsAvailable,
            mountOpacityAvailable,
            waveyCapesAvailable,
            skinLayersAvailable,
        ).count { it }

    @JvmStatic
    fun needsModSettingsChoice(): Boolean =
        shouldShowModSettings(PolyPlusConfig.onboardingModSettingsVersion, modCardCount)

    internal fun shouldShowModSettings(completedVersion: Int, availableCards: Int): Boolean =
        availableCards > 0 && completedVersion < MOD_SETTINGS_VERSION

    internal fun completedModSettingsVersion(completedVersion: Int, availableCards: Int): Int =
        if (availableCards > 0) MOD_SETTINGS_VERSION else completedVersion

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
            if (applyPendingModOptions()) {
                changed = true
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
        applyPendingModOptions(force = true)
        PolyPlusConfig.save()
    }

    fun applySavedModSettings() {
        applyPendingModOptions(force = true)
        PolyPlusConfig.save()
    }

    private fun applyPendingModOptions(force: Boolean = false): Boolean {
        if (PolyPlusConfig.onboardingModSettingsVersion < MOD_SETTINGS_VERSION) return false
        if (!hasPendingModOptions()) return false
        val now = System.currentTimeMillis()
        if (force) {
            modApplyAttempts = 0
            modApplyRetryDelayMs = MOD_APPLY_RETRY_INITIAL_MS
        } else if (now < nextModApplyAttemptMs || modApplyAttempts >= MOD_APPLY_MAX_ATTEMPTS) {
            return false
        }

        var changed = false
        if (betterGrassAvailable && !PolyPlusConfig.onboardingBetterGrassSettled) {
            if (applyBetterGrass(PolyPlusConfig.onboardingBetterGrassMode)) {
                PolyPlusConfig.onboardingBetterGrassSettled = true
                changed = true
            }
        }
        val fireOverlayPending = fireOverlayAvailable && !PolyPlusConfig.onboardingFireOverlaySettled
        val shieldHeightPending = shieldHeightAvailable && !PolyPlusConfig.onboardingShieldHeightSettled
        if (fireOverlayPending || shieldHeightPending) {
            val applied = applyFireOverlay(
                PolyPlusConfig.onboardingFireOverlayHeight,
                PolyPlusConfig.onboardingFireOverlayOpacity,
                PolyPlusConfig.onboardingShieldHeight,
                applyFireSettings = fireOverlayPending,
                applyShieldHeight = shieldHeightPending,
            )
            if (applied) {
                if (fireOverlayPending) PolyPlusConfig.onboardingFireOverlaySettled = true
                if (shieldHeightPending) PolyPlusConfig.onboardingShieldHeightSettled = true
                changed = true
            }
        }
        if (mountOpacityAvailable && !PolyPlusConfig.onboardingMountOpacitySettled) {
            if (applyHorseOpacity(PolyPlusConfig.onboardingHorseOpacity)) {
                PolyPlusConfig.onboardingMountOpacitySettled = true
                changed = true
            }
        }
        if (waveyCapesAvailable && !PolyPlusConfig.onboardingWaveyCapesSettled) {
            if (applyWaveyCapes(PolyPlusConfig.onboardingWaveyCapes)) {
                PolyPlusConfig.onboardingWaveyCapesSettled = true
                changed = true
            }
        }
        if (skinLayersAvailable && !PolyPlusConfig.onboardingSkinLayersSettled) {
            if (applySkinLayers(PolyPlusConfig.onboardingSkinLayers)) {
                PolyPlusConfig.onboardingSkinLayersSettled = true
                changed = true
            }
        }
        if (itemPositionsAvailable && !PolyPlusConfig.onboardingItemPositionsSettled) {
            val applied = applyItemPosition(
                PolyPlusConfig.onboardingItemOffsetX,
                PolyPlusConfig.onboardingItemOffsetY,
                PolyPlusConfig.onboardingItemOffsetZ,
                PolyPlusConfig.onboardingItemScale,
            )
            if (applied) {
                PolyPlusConfig.onboardingItemPositionsSettled = true
                changed = true
            }
        }
        if (hasPendingModOptions()) {
            modApplyAttempts++
            nextModApplyAttemptMs = now + modApplyRetryDelayMs
            modApplyRetryDelayMs = (modApplyRetryDelayMs * 2).coerceAtMost(MOD_APPLY_RETRY_MAX_MS)
            if (modApplyAttempts >= MOD_APPLY_MAX_ATTEMPTS) {
                logger.warn("Giving up on the optional-mod settings after {} attempts", modApplyAttempts)
            }
        } else {
            modApplyAttempts = 0
            nextModApplyAttemptMs = 0L
            modApplyRetryDelayMs = MOD_APPLY_RETRY_INITIAL_MS
        }
        return changed
    }

    private fun hasPendingModOptions(): Boolean =
        (betterGrassAvailable && !PolyPlusConfig.onboardingBetterGrassSettled) ||
            (fireOverlayAvailable && !PolyPlusConfig.onboardingFireOverlaySettled) ||
            (shieldHeightAvailable && !PolyPlusConfig.onboardingShieldHeightSettled) ||
            (mountOpacityAvailable && !PolyPlusConfig.onboardingMountOpacitySettled) ||
            (waveyCapesAvailable && !PolyPlusConfig.onboardingWaveyCapesSettled) ||
            (skinLayersAvailable && !PolyPlusConfig.onboardingSkinLayersSettled) ||
            (itemPositionsAvailable && !PolyPlusConfig.onboardingItemPositionsSettled)

    private fun logModApplyFailure(key: String, message: String, error: Throwable) {
        if (error is ClassNotFoundException) return
        if (warnedModApplyFailures.add(key)) logger.warn(message, error) else logger.debug(message, error)
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
        ThemeRegistry.activate(ThemeBrandingUtil.branded(theme))
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
                setBoolean(instance, "setBlurHand", !performance) // >= 1.21.5 only skipped if absent
            }
            config.getMethod("save").invoke(instance)
            true
        }.onFailure {
            if (it !is ClassNotFoundException) logger.warn("Could not apply PolyBlur preference", it)
        }.getOrDefault(false)
    }

    private fun betterGrassConfig(): Any {
        val modClass = Class.forName(LBG_MOD)
        val mod = modClass.getMethod("get").invoke(null) ?: error("LambdaBetterGrass is not initialised yet")
        return modClass.getField("config").get(mod) ?: error("LambdaBetterGrass has no config")
    }

    fun currentBetterGrassMode(): Int? = runCatching {
        val mode = betterGrassConfig().let { it.javaClass.getMethod("getMode").invoke(it) } as Enum<*>
        BETTER_GRASS_MODES.indexOf(mode.name).takeIf { it >= 0 } ?: BETTER_GRASS_FANCY
    }.getOrNull()

    fun applyBetterGrass(mode: Int): Boolean = runCatching {
        val config = betterGrassConfig()
        val modeClass = Class.forName(LBG_MODE)
        val name = BETTER_GRASS_MODES.getOrElse(mode) { BETTER_GRASS_MODES[BETTER_GRASS_FANCY] }
        @Suppress("UNCHECKED_CAST")
        val constant = java.lang.Enum.valueOf(modeClass as Class<out Enum<*>>, name)
        config.javaClass.getMethod("setMode", modeClass).invoke(config, constant)
        config.javaClass.getMethod("save").invoke(config)
        true
    }.onFailure {
        logModApplyFailure("better-grass", "Could not apply the LambdaBetterGrass preference", it)
    }.getOrDefault(false)

    internal class ConfigAccess internal constructor(
        val instance: Any,
        private val saveAction: () -> Unit,
    ) {
        fun save() = saveAction()
    }

    private fun handlerAccess(configClass: Class<*>): ConfigAccess {
        val handler = configClass.getField("CONFIG").get(null)
            ?: error("${configClass.simpleName} has no config handler")
        val instance = handler.javaClass.getMethod("instance").invoke(handler)
            ?: error("${configClass.simpleName} config is unavailable")
        return ConfigAccess(instance) { handler.javaClass.getMethod("save").invoke(handler) }
    }

    internal fun overlayTweaksAccess(configClass: Class<*> = Class.forName(OVERLAY_TWEAKS_CONFIG)): ConfigAccess {
        if (runCatching { configClass.getField("CONFIG") }.isSuccess) return handlerAccess(configClass)

        val instance = configClass.getField("INSTANCE").get(null)
            ?: error("Overlay Tweaks config is unavailable")
        return ConfigAccess(instance) { configClass.getMethod("save").invoke(instance) }
    }

    internal fun readFloatingField(instance: Any, name: String): Double {
        val value = instance.javaClass.getField(name).get(instance)
        return (value as? Number)?.toDouble() ?: error("Overlay Tweaks option '$name' is not numeric")
    }

    internal fun writeFloatingField(instance: Any, name: String, value: Double) {
        val field = instance.javaClass.getField(name)
        when (field.type) {
            java.lang.Double.TYPE -> field.setDouble(instance, value)
            java.lang.Float.TYPE -> field.setFloat(instance, value.toFloat())
            else -> error("Overlay Tweaks option '$name' is not floating-point")
        }
    }

    fun currentFireOverlayHeight(): Double? = runCatching {
        val instance = overlayTweaksAccess().instance
        readFloatingField(instance, FIRE_OVERLAY_HEIGHT)
    }.getOrNull()

    fun currentFireOverlayOpacity(): Float? = runCatching {
        val instance = overlayTweaksAccess().instance
        readFloatingField(instance, FIRE_OVERLAY_OPACITY).toFloat()
    }.getOrNull()

    fun currentShieldHeight(): Float? = runCatching {
        val instance = overlayTweaksAccess().instance
        readFloatingField(instance, SHIELD_HEIGHT).toFloat()
    }.getOrNull()

    fun applyFireOverlay(
        height: Double,
        opacity: Float,
        shieldHeight: Float,
        applyFireSettings: Boolean = true,
        applyShieldHeight: Boolean = true,
    ): Boolean = runCatching {
        val access = overlayTweaksAccess()
        val instance = access.instance
        if (applyFireSettings && fireOverlayAvailable) {
            writeFloatingField(instance, FIRE_OVERLAY_HEIGHT, height.coerceIn(FIRE_OVERLAY_MIN, FIRE_OVERLAY_MAX))
        }
        if (applyFireSettings && fireOverlayOpacityAvailable) {
            writeFloatingField(
                instance,
                FIRE_OVERLAY_OPACITY,
                opacity.coerceIn(FIRE_OPACITY_MIN, FIRE_OPACITY_MAX).toDouble(),
            )
        }
        if (applyShieldHeight && shieldHeightAvailable) {
            writeFloatingField(
                instance,
                SHIELD_HEIGHT,
                shieldHeight.coerceIn(SHIELD_HEIGHT_MIN, SHIELD_HEIGHT_MAX).toDouble(),
            )
        }
        access.save()
        true
    }.onFailure {
        logModApplyFailure("overlay-tweaks", "Could not apply the Overlay Tweaks preference", it)
    }.getOrDefault(false)

    private fun mountOpacityAccess(): ConfigAccess = handlerAccess(Class.forName(MOUNT_OPACITY_CONFIG))

    fun currentHorseOpacity(): Float? = runCatching {
        val instance = mountOpacityAccess().instance
        instance.javaClass.getField(HORSE_OPACITY).getFloat(instance)
    }.getOrNull()

    fun applyHorseOpacity(opacity: Float): Boolean = runCatching {
        val access = mountOpacityAccess()
        val instance = access.instance
        val value = opacity.coerceIn(HORSE_OPACITY_MIN, HORSE_OPACITY_MAX)
        var applied = false
        for (field in MOUNT_OPACITY_FIELDS) {
            runCatching { instance.javaClass.getField(field).setFloat(instance, value) }
                .onSuccess { applied = true }
        }
        if (!applied) error("Mount Opacity has no opacity fields")
        access.save()
        true
    }.onFailure {
        logModApplyFailure("mount-opacity", "Could not apply the Mount Opacity preference", it)
    }.getOrDefault(false)

    private fun waveyConfig(): Any =
        Class.forName(WAVEY_MOD_BASE).getField("config").get(null) ?: error("WaveyCapes is not initialised yet")

    private fun waveySave() {
        val base = Class.forName(WAVEY_MOD_BASE)
        val instance = base.getField("INSTANCE").get(null) ?: error("WaveyCapes is not initialised yet")
        base.getMethod("writeConfig").invoke(instance)
    }

    fun currentWaveyCapes(): Boolean? = runCatching {
        val movement = waveyConfig().let { it.javaClass.getField(CAPE_MOVEMENT).get(it) } as Enum<*>
        movement.name != CAPE_MOVEMENT_VANILLA
    }.getOrNull()

    fun applyWaveyCapes(enabled: Boolean): Boolean = runCatching {
        val config = waveyConfig()
        waveyCapeMovementWrite(enabled, currentEnumName(config, CAPE_MOVEMENT))
            ?.let { setEnum(config, CAPE_MOVEMENT, CAPE_MOVEMENT_ENUM, it) }
        waveySave()
        true
    }.onFailure {
        logModApplyFailure("wavey-capes", "Could not apply the WaveyCapes preference", it)
    }.getOrDefault(false)

    internal fun waveyCapeMovementWrite(enabled: Boolean, movement: String?): String? = when {
        !enabled -> CAPE_MOVEMENT_VANILLA
        movement == CAPE_MOVEMENT_VANILLA -> CAPE_MOVEMENT_WAVEY
        else -> null
    }

    private fun currentEnumName(target: Any, field: String): String? =
        (target.javaClass.getField(field).get(target) as? Enum<*>)?.name

    private fun setEnum(target: Any, field: String, enumClass: String, name: String) {
        @Suppress("UNCHECKED_CAST")
        val type = Class.forName(enumClass) as Class<out Enum<*>>
        target.javaClass.getField(field).set(target, java.lang.Enum.valueOf(type, name))
    }

    private fun skinLayersConfig(): Any =
        Class.forName(SKIN_LAYERS_MOD_BASE).getField("config").get(null)
            ?: error("3D Skin Layers is not initialised yet")

    fun currentSkinLayers(): Boolean? = runCatching {
        val config = skinLayersConfig()
        config.javaClass.getField(SKIN_LAYERS.first()).getBoolean(config)
    }.getOrNull()

    fun applySkinLayers(enabled: Boolean): Boolean = runCatching {
        val config = skinLayersConfig()
        SKIN_LAYERS.forEach { config.javaClass.getField(it).setBoolean(config, enabled) }
        val base = Class.forName(SKIN_LAYERS_MOD_BASE)
        val instance = base.getField("instance").get(null) ?: error("3D Skin Layers is not initialised yet")
        base.getMethod("writeConfig").invoke(instance)
        true
    }.onFailure {
        logModApplyFailure("skin-layers", "Could not apply the 3D Skin Layers preference", it)
    }.getOrDefault(false)

    private fun animatiumExtras(): Any {
        val configClass = Class.forName(ANIMATIUM_CONFIG)
        val instance = configClass.getMethod("instance").invoke(null) ?: error("Animatium config is unavailable")
        return instance.javaClass.getField("extras").get(instance) ?: error("Animatium has no extras category")
    }

    private fun animatiumFloat(name: String): Float? = runCatching {
        val extras = animatiumExtras()
        extras.javaClass.getField(name).getFloat(extras)
    }.getOrNull()

    fun currentItemOffsetX(): Float? = animatiumFloat(ITEM_OFFSET_X)

    fun currentItemOffsetY(): Float? = animatiumFloat(ITEM_OFFSET_Y)

    fun currentItemOffsetZ(): Float? = animatiumFloat(ITEM_OFFSET_Z)

    fun currentItemScale(): Float? = animatiumFloat(ITEM_SCALE_AXES.first())

    fun applyItemPosition(offsetX: Float, offsetY: Float, offsetZ: Float, scale: Float): Boolean = runCatching {
        val extras = animatiumExtras()
        fun set(name: String, value: Float) = extras.javaClass.getField(name).setFloat(extras, value)
        set(ITEM_OFFSET_X, offsetX.coerceIn(ITEM_OFFSET_MIN, ITEM_OFFSET_MAX))
        set(ITEM_OFFSET_Y, offsetY.coerceIn(ITEM_OFFSET_MIN, ITEM_OFFSET_MAX))
        set(ITEM_OFFSET_Z, offsetZ.coerceIn(ITEM_OFFSET_MIN, ITEM_OFFSET_MAX))
        ITEM_SCALE_AXES.forEach { set(it, scale.coerceIn(ITEM_SCALE_MIN, ITEM_SCALE_MAX)) }
        Class.forName(ANIMATIUM_CONFIG).getMethod("save").invoke(null)
        reloadAnimatium()
        true
    }.onFailure {
        logModApplyFailure("animatium-item-position", "Could not apply the Animatium item position", it)
    }.getOrDefault(false)

    private fun reloadAnimatium() {
        val mod = runCatching { Class.forName(ANIMATIUM_MOD) }.getOrNull() ?: return
        val reload = runCatching { mod.getMethod("reload") }.getOrNull() ?: return
        runCatching { reload.invoke(null) }
            .onFailure { logger.warn("Could not reload Animatium after applying the preference", it) }
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

    private fun loadWithoutInit(name: String): Class<*> = Class.forName(name, false, javaClass.classLoader)

    private fun classExists(name: String) = runCatching { loadWithoutInit(name) }.isSuccess

    internal fun hasFloatingField(className: String, fieldName: String): Boolean = runCatching {
        loadWithoutInit(className).getField(fieldName).type
    }.getOrNull().let { it == java.lang.Double.TYPE || it == java.lang.Float.TYPE }

    const val MOD_SETTINGS_VERSION = 1

    private const val MOD_APPLY_RETRY_INITIAL_MS = 1_000L
    private const val MOD_APPLY_RETRY_MAX_MS = 60_000L
    private const val MOD_APPLY_MAX_ATTEMPTS = 10

    const val MOTION_BLUR_UNSET = -1
    const val MOTION_BLUR_DISABLED = 0
    const val MOTION_BLUR_PERFORMANCE = 1
    const val MOTION_BLUR_QUALITY = 2

    private const val UNITY_BLUR_TYPE = 1  // blurType dropdown 0 Phosphor 1 Unity 2 Hybrid
    private const val HYBRID_BLUR_TYPE = 2
    private const val PERFORMANCE_SAMPLES = 8f // motionBlurSamples slider range 4..32
    private const val QUALITY_SAMPLES = 16f
    private const val POLYSPRINT_CONFIG = "org.polyfrost.polysprint.client.PolySprintConfig"
    private const val POLYBLUR_CONFIG = "org.polyfrost.polyblur.client.PolyBlurConfig"

    private const val LBG_MOD = "dev.lambdaurora.lambdabettergrass.LambdaBetterGrass"
    private const val LBG_MODE = "dev.lambdaurora.lambdabettergrass.LBGMode"
    val BETTER_GRASS_MODES = listOf("OFF", "FASTEST", "FAST", "FANCY")
    const val BETTER_GRASS_OFF = 0
    const val BETTER_GRASS_FASTEST = 1
    const val BETTER_GRASS_FANCY = 3

    private const val OVERLAY_TWEAKS_CONFIG = "dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig"
    private const val FIRE_OVERLAY_HEIGHT = "fireOverlayHeight"
    const val FIRE_OVERLAY_MIN = -0.5
    const val FIRE_OVERLAY_MAX = 0.0

    private const val FIRE_OVERLAY_OPACITY = "customFireOverlayOpacity"
    const val FIRE_OPACITY_MIN = 0f
    const val FIRE_OPACITY_MAX = 100f

    private const val SHIELD_HEIGHT = "customShieldHeight"
    const val SHIELD_HEIGHT_MIN = -0.5f
    const val SHIELD_HEIGHT_MAX = 0f

    private const val MOUNT_OPACITY_CONFIG = "dev.microcontrollers.mountopacity.config.MountOpacityConfig"
    private const val HORSE_OPACITY = "horseOpacity"
    private val MOUNT_OPACITY_FIELDS = listOf(
        HORSE_OPACITY,
        "pigOpacity",
        "llamaOpacity",
        "striderOpacity",
        "camelOpacity",
        "nautilusOpacity",
        "happyGhastOpacity",
        "defaultOpacity",
    )
    const val HORSE_OPACITY_MIN = 0f
    const val HORSE_OPACITY_MAX = 100f

    private const val WAVEY_MOD_BASE = "dev.tr7zw.waveycapes.versionless.ModBase"
    private const val CAPE_MOVEMENT = "capeMovement"
    private const val CAPE_MOVEMENT_ENUM = "dev.tr7zw.waveycapes.versionless.CapeMovement"
    private const val CAPE_MOVEMENT_VANILLA = "VANILLA"
    private const val CAPE_MOVEMENT_WAVEY = "BASIC_SIMULATION_3D"

    private const val SKIN_LAYERS_MOD_BASE = "dev.tr7zw.skinlayers.SkinLayersModBase"
    private val SKIN_LAYERS = listOf(
        "enableHat",
        "enableJacket",
        "enableLeftSleeve",
        "enableRightSleeve",
        "enableLeftPants",
        "enableRightPants",
    )

    private const val ANIMATIUM_CONFIG = "org.visuals.legacy.animatium.config.AnimatiumConfig"
    private const val ANIMATIUM_MOD = "org.visuals.legacy.animatium.Animatium"
    private const val ITEM_OFFSET_X = "itemOffsetX"
    private const val ITEM_OFFSET_Y = "itemOffsetY"
    private const val ITEM_OFFSET_Z = "itemOffsetZ"
    private val ITEM_SCALE_AXES = listOf("itemScaleX", "itemScaleY", "itemScaleZ")

    private val ITEM_POSITION_FIELDS = listOf(ITEM_OFFSET_X, ITEM_OFFSET_Y, ITEM_OFFSET_Z) + ITEM_SCALE_AXES

    const val ITEM_OFFSET_MIN = -10f
    const val ITEM_OFFSET_MAX = 10f
    const val ITEM_SCALE_MIN = 0.5f
    const val ITEM_SCALE_MAX = 2f
}
