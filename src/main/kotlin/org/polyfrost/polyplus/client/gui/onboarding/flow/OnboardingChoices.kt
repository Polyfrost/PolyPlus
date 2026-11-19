package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
import org.polyfrost.polyplus.client.features.BlockHighlightDraft
import org.polyfrost.polyplus.client.features.BlockHighlightPresets
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.features.OnboardingFeatures.ModCard
import org.polyfrost.polyplus.client.features.OnboardingFeatures.available
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveOurs
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTuning
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTweak
import org.polyfrost.polyplus.client.gui.onboarding.live.RenderScreenKind
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
import org.polyfrost.polyplus.privacy.LauncherEnvironment
import kotlin.io.path.exists
import kotlin.math.abs

internal val brandName: String get() = if (LauncherEnvironment.isOneClient) "OneClient" else "PolyPlus"

internal const val VANILLA_NAME = "Vanilla"

internal enum class ScreenPart(val label: String, val kind: RenderScreenKind) {
    SETTINGS("Settings", RenderScreenKind.OPTIONS),
    WORLDS("New world", RenderScreenKind.WORLDS),
    CONNECTING("Connecting", RenderScreenKind.CONNECTING),
    REALMS("Realms", RenderScreenKind.REALMS),
    PAUSE("Pause menu", RenderScreenKind.PAUSE),
    LOADING("Loading screen", RenderScreenKind.LOADING),
    ;

    fun read(): Boolean = when (this) {
        SETTINGS -> PolyPlusScreensConfig.customSettings
        WORLDS -> PolyPlusScreensConfig.customWorlds
        CONNECTING -> PolyPlusScreensConfig.customConnecting
        REALMS -> PolyPlusScreensConfig.customRealms
        PAUSE -> PolyPlusPauseConfig.pauseScreenStyle != PauseScreenStyle.VANILLA
        LOADING -> PolyPlusConfig.brandedStartup
    }

    fun write(ours: Boolean) {
        when (this) {
            SETTINGS -> PolyPlusScreensConfig.customSettings = ours
            WORLDS -> PolyPlusScreensConfig.customWorlds = ours
            CONNECTING -> PolyPlusScreensConfig.customConnecting = ours
            REALMS -> PolyPlusScreensConfig.customRealms = ours
            PAUSE -> {
                val style = PolyPlusPauseConfig.pauseScreenStyle
                PolyPlusPauseConfig.pauseScreenStyle = when {
                    !ours -> PauseScreenStyle.VANILLA
                    style == PauseScreenStyle.VANILLA -> PauseScreenStyle.STEP_BACK_FLAT
                    else -> style
                }
            }
            LOADING -> PolyPlusConfig.brandedStartup = ours
        }
    }

    companion object {
        //? if > 1.8.9 {
        val shown: List<ScreenPart> = entries
        //?} else {
        /*val shown: List<ScreenPart> = entries - REALMS
        *///?}
    }
}

internal sealed interface CardTopic {
    data class Tweak(val tweak: LiveTweak) : CardTopic

    data class Screens(val parts: List<ScreenPart>, val watermark: Boolean) : CardTopic
}

internal val LiveTweak.title: String
    get() = when (this) {
        LiveTweak.CAPES -> "Waving capes"
        LiveTweak.SKIN_LAYERS -> "3D skin layers"
        LiveTweak.SPRINT -> "Toggle sprint"
        LiveTweak.MOUNT -> "See-through mounts"
        LiveTweak.FIRE -> "Lower fire"
        LiveTweak.SHIELD -> "Lower shield"
        LiveTweak.FULLBRIGHT -> "Fullbright"
        LiveTweak.DYNAMIC_LIGHTS -> "Dynamic lights"
        LiveTweak.BETTER_GRASS -> "Better grass"
        LiveTweak.BLOCK_OUTLINE -> "Block outline"
    }

internal val ModCard.tweak: LiveTweak
    get() = when (this) {
        ModCard.GRASS -> LiveTweak.BETTER_GRASS
        ModCard.FIRE_OVERLAY -> LiveTweak.FIRE
        ModCard.SHIELD_HEIGHT -> LiveTweak.SHIELD
        ModCard.MOUNT -> LiveTweak.MOUNT
        ModCard.CAPES -> LiveTweak.CAPES
        ModCard.SKIN_LAYERS -> LiveTweak.SKIN_LAYERS
        ModCard.GAMMA -> LiveTweak.FULLBRIGHT
        ModCard.BLOCK_HIGHLIGHT -> LiveTweak.BLOCK_OUTLINE
        ModCard.DYNAMIC_LIGHTS -> LiveTweak.DYNAMIC_LIGHTS
    }

internal fun cardTopics(offered: List<ModCard>, sprint: Boolean, screens: List<ScreenPart>, watermark: Boolean): List<CardTopic> {
    val tweaks = offered.map { it.tweak }.toSet() + if (sprint) setOf(LiveTweak.SPRINT) else emptySet()
    return buildList {
        LiveTweak.entries.filter { it in tweaks }.forEach { add(CardTopic.Tweak(it)) }
        if (screens.isNotEmpty() || watermark) add(CardTopic.Screens(screens, watermark))
    }
}

internal class OnboardingChoices {
    private val tweaks = mutableStateMapOf<LiveTweak, Boolean>()
    private val screens = mutableStateMapOf<ScreenPart, Boolean>()
    var watermark by mutableStateOf(!PolyPlusConfig.watermarkAsked || PolyPlusConfig.watermarkEnabled)

    fun ours(tweak: LiveTweak): Boolean = tweaks[tweak] ?: true

    fun answered(tweak: LiveTweak): Boolean = tweak in tweaks

    fun pick(tweak: LiveTweak, ours: Boolean) {
        tweaks[tweak] = ours
    }

    fun ours(part: ScreenPart): Boolean = screens[part] ?: (!PolyPlusScreensConfig.onboardingAsked || part.read())

    fun pick(part: ScreenPart, ours: Boolean) {
        screens[part] = ours
    }

    fun writeScreens(parts: List<ScreenPart>) {
        parts.forEach { it.write(ours(it)) }
        PolyPlusScreensConfig.onboardingAsked = true
        PolyPlusScreensConfig.save()
        PolyPlusPauseConfig.save()
    }

    fun writeWatermark() {
        PolyPlusConfig.watermarkEnabled = watermark
        PolyPlusConfig.watermarkAsked = true
    }

    fun writeTweaks(offered: List<ModCard>, reads: ModReads, tuning: LiveTuning): Set<ModCard> = offered.filterTo(mutableSetOf()) { card ->
        answered(card.tweak) && writeTweak(card, ours(card.tweak), reads, tuning)
    }

    private fun writeTweak(card: ModCard, ours: Boolean, reads: ModReads, tuning: LiveTuning): Boolean = when (card) {
        ModCard.GRASS -> {
            val mode = if (ours) LiveOurs.BETTER_GRASS_MODE else OnboardingFeatures.BETTER_GRASS_OFF
            PolyPlusConfig.onboardingBetterGrassMode = mode
            mode != reads.grass
        }
        ModCard.FIRE_OVERLAY -> {
            val height = if (ours) tuning.fireHeight else OnboardingFeatures.FIRE_OVERLAY_MAX
            val opacity = if (ours) tuning.fireOpacity else OnboardingFeatures.FIRE_OPACITY_VANILLA
            PolyPlusConfig.onboardingFireOverlayHeight = height
            PolyPlusConfig.onboardingFireOverlayOpacity = opacity
            differs(height, reads.fireHeight) ||
                OnboardingFeatures.fireOverlayOpacityAvailable && differs(opacity.toDouble(), reads.fireOpacity?.toDouble(), OPACITY_TOLERANCE)
        }
        ModCard.SHIELD_HEIGHT -> {
            val height = if (ours) tuning.shieldHeight else OnboardingFeatures.SHIELD_HEIGHT_MAX
            PolyPlusConfig.onboardingShieldHeight = height
            differs(height.toDouble(), reads.shield?.toDouble())
        }
        ModCard.MOUNT -> {
            val opacity = if (ours) tuning.horseOpacity else OnboardingFeatures.HORSE_OPACITY_MAX
            PolyPlusConfig.onboardingHorseOpacity = opacity
            differs(opacity.toDouble(), reads.horse?.toDouble(), OPACITY_TOLERANCE)
        }
        ModCard.CAPES -> {
            PolyPlusConfig.onboardingWaveyCapes = ours
            ours != reads.capes
        }
        ModCard.SKIN_LAYERS -> {
            PolyPlusConfig.onboardingSkinLayers = ours
            ours != reads.layers
        }
        ModCard.GAMMA -> {
            val gamma = if (ours) LiveOurs.GAMMA else OnboardingFeatures.GAMMA_MIN
            PolyPlusConfig.onboardingGamma = gamma
            reads.gammaToggled?.let { PolyPlusConfig.onboardingGammaToggled = it.coerceIn(OnboardingFeatures.GAMMA_MIN, OnboardingFeatures.GAMMA_MAX) }
            reads.gammaSmooth?.let { PolyPlusConfig.onboardingGammaSmooth = it }
            differs(gamma.toDouble(), reads.gamma?.toDouble(), OPACITY_TOLERANCE)
        }
        ModCard.BLOCK_HIGHLIGHT -> {
            val json = if (ours) outlineJson(tuning.outlinePreset, tuning.outlineColor) else BlockHighlightPresets.presetJson(BlockHighlightPresets.DEFAULT)
            PolyPlusConfig.onboardingBlockHighlightConfig = json
            val current = reads.blockHighlight
            current == null || runCatching { !BlockHighlightDraft(current).matches(json) }.getOrDefault(true)
        }
        ModCard.DYNAMIC_LIGHTS -> {
            val mode = if (ours) LiveOurs.DYNAMIC_LIGHTS_MODE else OnboardingFeatures.DYNAMIC_LIGHTS_OFF
            PolyPlusConfig.onboardingDynamicLightsMode = mode
            mode != reads.dynamicLights
        }
    }

    private fun differs(chosen: Double, current: Double?, tolerance: Double = HEIGHT_TOLERANCE): Boolean =
        current == null || abs(chosen - current) > tolerance

    private companion object {
        const val HEIGHT_TOLERANCE = 0.001
        const val OPACITY_TOLERANCE = 0.5
    }
}

internal fun outlineJson(preset: String, color: Int): String {
    val json = BlockHighlightPresets.presetJson(preset)
    if (color == 0) return json
    return runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val rgb = OPAQUE or (color and RGB_MASK)
        val colorAlpha = (color ushr ALPHA_SHIFT) and ALPHA_MASK
        OUTLINE_COLOR_PATHS.forEach { path ->
            val target = path.fold<String, JsonObject?>(root) { node, key -> node?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject } ?: return@forEach
            target.addProperty("col1", rgb)
            target.addProperty("col2", rgb)
            val presetAlpha = target.get("alpha")?.takeIf { it.isJsonPrimitive }?.asInt ?: ALPHA_MASK
            target.addProperty("alpha", presetAlpha * colorAlpha / ALPHA_MASK)
            val rainbow = target.get("rainbowSettings")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { target.add("rainbowSettings", it) }
            rainbow.addProperty("enabled", false)
        }
        root.toString()
    }.getOrDefault(json)
}

private val OUTLINE_COLOR_PATHS = listOf(listOf("primary", "color"), listOf("secondary", "color"), listOf("tertiary", "color"), listOf("fillCol"))
private const val OPAQUE = 0xFF000000.toInt()
private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_SHIFT = 24
private const val ALPHA_MASK = 0xFF

internal class ModReads(
    val grass: Int?,
    val fireHeight: Double?,
    val fireOpacity: Float?,
    val shield: Float?,
    val horse: Float?,
    val capes: Boolean?,
    val layers: Boolean?,
    val gamma: Float?,
    val gammaToggled: Float?,
    val gammaSmooth: Boolean?,
    val blockHighlight: String?,
    val dynamicLights: Int?,
) {
    val cards: List<ModCard> = buildList {
        if (grass != null) add(ModCard.GRASS)
        if (fireHeight != null && (!OnboardingFeatures.fireOverlayOpacityAvailable || fireOpacity != null)) {
            add(ModCard.FIRE_OVERLAY)
        }
        if (shield != null) add(ModCard.SHIELD_HEIGHT)
        if (horse != null) add(ModCard.MOUNT)
        if (capes != null) add(ModCard.CAPES)
        if (layers != null) add(ModCard.SKIN_LAYERS)
        if (gamma != null) add(ModCard.GAMMA)
        if (blockHighlight != null) add(ModCard.BLOCK_HIGHLIGHT)
        if (dynamicLights != null) add(ModCard.DYNAMIC_LIGHTS)
    }.filter { it.available }

    companion object {
        fun read(enabled: Boolean): ModReads {
            fun <T> ifAvailable(available: Boolean, read: () -> T?): T? =
                if (enabled && available) read() else null
            return ModReads(
                grass = ifAvailable(OnboardingFeatures.betterGrassAvailable) { OnboardingFeatures.currentBetterGrassMode() },
                fireHeight = ifAvailable(OnboardingFeatures.fireOverlayAvailable) { OnboardingFeatures.currentFireOverlayHeight() },
                fireOpacity = ifAvailable(
                    OnboardingFeatures.fireOverlayAvailable && OnboardingFeatures.fireOverlayOpacityAvailable,
                ) { OnboardingFeatures.currentFireOverlayOpacity() },
                shield = ifAvailable(OnboardingFeatures.shieldHeightAvailable) { OnboardingFeatures.currentShieldHeight() },
                horse = ifAvailable(OnboardingFeatures.mountOpacityAvailable) { OnboardingFeatures.currentHorseOpacity() },
                capes = ifAvailable(OnboardingFeatures.waveyCapesAvailable) { OnboardingFeatures.currentWaveyCapes() },
                layers = ifAvailable(OnboardingFeatures.skinLayersAvailable) { OnboardingFeatures.currentSkinLayers() },
                gamma = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) { OnboardingFeatures.currentGamma() },
                gammaToggled = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) { OnboardingFeatures.currentGammaToggled() },
                gammaSmooth = ifAvailable(OnboardingFeatures.gammaUtilsAvailable) { OnboardingFeatures.currentGammaSmooth() },
                blockHighlight = ifAvailable(BlockHighlightPresets.available) { currentBlockHighlight() },
                dynamicLights = ifAvailable(OnboardingFeatures.dynamicLightsAvailable) { OnboardingFeatures.currentDynamicLightsMode() },
            )
        }

        private fun currentBlockHighlight(): String? = runCatching {
            if (BlockHighlightPresets.markerPath().exists() && !BlockHighlightPresets.isModDefault()) {
                BlockHighlightPresets.currentJson()
            } else {
                BlockHighlightPresets.presetJson(BlockHighlightPresets.DEFAULT)
            }
        }.getOrNull()
    }
}
