package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import kotlin.math.roundToInt

@Composable
internal fun RenderFirePreview(heightOffset: Float, opacityPercent: Float, modifier: Modifier) {
    val offset = heightOffset.coerceIn(OnboardingFeatures.FIRE_OVERLAY_MIN.toFloat(), OnboardingFeatures.FIRE_OVERLAY_MAX.toFloat())
    val alpha = (opacityPercent.coerceIn(OnboardingFeatures.FIRE_OPACITY_MIN, OnboardingFeatures.FIRE_OPACITY_MAX) / PERCENT * MAX_CHANNEL).roundToInt()
    LiveWorldFirstPerson("polyplus_fire_preview", modifier) { collector, _ ->
        RenderFireRenderer.submit(collector, offset, alpha)
    }
}

private const val PERCENT = 100f
private const val MAX_CHANNEL = 255
//?}
