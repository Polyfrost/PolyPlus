package org.polyfrost.polyplus.client.gui.onboarding.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.features.OnboardingFeatures

internal enum class LiveTweak {
    CAPES,
    SKIN_LAYERS,
    SPRINT,
    MOUNT,
    FIRE,
    SHIELD,
    FULLBRIGHT,
    DYNAMIC_LIGHTS,
    BETTER_GRASS,
    BLOCK_OUTLINE,
}

internal object LiveOurs {
    const val FIRE_HEIGHT = -0.3
    const val FIRE_OPACITY = 55f
    const val SHIELD_HEIGHT = -0.25f
    const val HORSE_OPACITY = 30f
    const val GAMMA = 1500f
    const val DYNAMIC_LIGHTS_MODE = 2
    const val BETTER_GRASS_MODE = OnboardingFeatures.BETTER_GRASS_FANCY
    const val BLOCK_OUTLINE_PRESET = "fancy"
    const val BLOCK_OUTLINE_COLOR = 0
}

internal class LiveTuning {
    var fireHeight by mutableDoubleStateOf(LiveOurs.FIRE_HEIGHT)
    var fireOpacity by mutableFloatStateOf(LiveOurs.FIRE_OPACITY)
    var shieldHeight by mutableFloatStateOf(LiveOurs.SHIELD_HEIGHT)
    var horseOpacity by mutableFloatStateOf(LiveOurs.HORSE_OPACITY)
    var outlinePreset by mutableStateOf(LiveOurs.BLOCK_OUTLINE_PRESET)
    var outlineColor by mutableIntStateOf(LiveOurs.BLOCK_OUTLINE_COLOR)
}

internal val LocalLiveTuning = staticCompositionLocalOf { LiveTuning() }

@Composable
internal fun LiveTweakPreview(tweak: LiveTweak, ours: Boolean, modifier: Modifier) = when (tweak) {
    LiveTweak.CAPES, LiveTweak.SKIN_LAYERS, LiveTweak.SPRINT, LiveTweak.MOUNT -> LivePlayerPreview(tweak, ours, modifier)
    else -> LiveWorldPreview(tweak, ours, modifier)
}
