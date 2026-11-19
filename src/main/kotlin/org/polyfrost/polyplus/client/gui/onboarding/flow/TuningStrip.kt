@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.features.BlockHighlightPresets
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.OnboardingSlider
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTuning
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTweak
import org.polyfrost.polyplus.client.gui.onboarding.live.LocalLiveTuning
import org.polyfrost.polyplus.client.gui.onboarding.live.outlinePresetColors
import java.util.Locale
import kotlin.math.roundToInt

internal val LiveTweak.tunable: Boolean get() = tuningRows > 0

internal val LiveTweak.tuningRows: Int
    get() = when (this) {
        LiveTweak.MOUNT, LiveTweak.SHIELD -> 1
        LiveTweak.FIRE -> if (OnboardingFeatures.fireOverlayOpacityAvailable) 2 else 1
        LiveTweak.BLOCK_OUTLINE -> 2
        else -> 0
    }

internal const val TUNING_ROW_HEIGHT = 30f

@Composable
internal fun TuningControls(tweak: LiveTweak, width: Float, onTune: () -> Unit) {
    val tuning = LocalLiveTuning.current
    Column(
        Modifier
            .width(width.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) onTune()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
    ) {
        when (tweak) {
            LiveTweak.MOUNT -> TuneSlider("Opacity", width, tuning.horseOpacity, OnboardingFeatures.HORSE_OPACITY_MIN, OnboardingFeatures.HORSE_OPACITY_MAX, ::percent) {
                tuning.horseOpacity = it
            }
            LiveTweak.FIRE -> {
                TuneSlider(
                    "Height",
                    width,
                    tuning.fireHeight.toFloat(),
                    OnboardingFeatures.FIRE_OVERLAY_MIN.toFloat(),
                    OnboardingFeatures.FIRE_OVERLAY_MAX.toFloat(),
                    ::height,
                ) { tuning.fireHeight = it.toDouble() }
                if (OnboardingFeatures.fireOverlayOpacityAvailable) {
                    TuneSlider("Opacity", width, tuning.fireOpacity, OnboardingFeatures.FIRE_OPACITY_MIN, OnboardingFeatures.FIRE_OPACITY_MAX, ::percent) {
                        tuning.fireOpacity = it
                    }
                }
            }
            LiveTweak.SHIELD -> TuneSlider("Height", width, tuning.shieldHeight, OnboardingFeatures.SHIELD_HEIGHT_MIN, OnboardingFeatures.SHIELD_HEIGHT_MAX, ::height) {
                tuning.shieldHeight = it
            }
            LiveTweak.BLOCK_OUTLINE -> OutlineControls(tuning, width)
            else -> Unit
        }
    }
}

@Composable
private fun TuneSlider(label: String, width: Float, value: Float, min: Float, max: Float, format: (Float) -> String, onValue: (Float) -> Unit) {
    val span = max - min
    Row(Modifier.height(TUNING_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(LABEL_WIDTH.dp),
            color = OneConfigTheme.current.mutedText,
            style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(ROW_GUTTER.dp))
        OnboardingSlider(
            progress = ((value - min) / span).coerceIn(0f, 1f),
            width = width - LABEL_WIDTH - VALUE_WIDTH - ROW_GUTTER * 2f,
            label = label,
            valueText = { format(min + it * span) },
        ) { onValue(min + it * span) }
        Spacer(Modifier.width(ROW_GUTTER.dp))
        Text(
            format(value),
            modifier = Modifier.width(VALUE_WIDTH.dp),
            style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun OutlineControls(tuning: LiveTuning, width: Float) {
    val presets = BlockHighlightPresets.NAMES.zip(BlockHighlightPresets.LABELS).filter { it.first != BlockHighlightPresets.DEFAULT }
    val presetWidth = (width - ROW_GAP * (presets.size - 1)) / presets.size
    Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        presets.forEach { (name, label) ->
            PresetButton(name, presetLabel(name, label), tuning.outlinePreset == name, presetWidth) {
                tuning.outlinePreset = name
                tuning.outlineColor = 0
            }
        }
    }
    Row(Modifier.height(TUNING_ROW_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP.dp)) {
        Chip(
            PRESET_LABEL,
            tuning.outlineColor == 0,
            { tuning.outlineColor = 0 },
            Modifier.size(PRESET_CHIP_WIDTH.dp, CHIP_HEIGHT.dp),
            size = ControlSize.Compact,
            role = A11yRole.Option,
            contentDescription = "Outline colour: the style's own colours",
        )
        SWATCHES.forEach { (rgb, name) ->
            val argb = OPAQUE or rgb
            Swatch(argb, name, tuning.outlineColor == argb) { tuning.outlineColor = argb }
        }
    }
}

@Composable
private fun PresetButton(name: String, label: String, selected: Boolean, width: Float, onClick: () -> Unit) {
    val hover = rememberHoverState()
    val colors = remember(name) { outlinePresetColors(name).take(MAX_PRESET_COLORS) }
    val ink = if (selected) ButtonDefaults.contentColor(ControlStyle.Primary) else OneConfigTheme.current.textColor
    Row(
        Modifier
            .size(width.dp, TUNING_ROW_HEIGHT.dp)
            .rowFrame(selected, hover.hovered)
            .oneConfigClickable("Outline style: $label", onClick, A11yRole.Option, selected = selected, interactionSource = hover.interactionSource)
            .padding(horizontal = PRESET_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PRESET_GAP.dp, Alignment.CenterHorizontally),
    ) {
        if (colors.isNotEmpty()) {
            Row(Modifier.clip(OneConfigTheme.shape(STRIP_RADIUS.dp)).border(1.dp, SocialBorderColor, OneConfigTheme.shape(STRIP_RADIUS.dp))) {
                colors.forEach { Box(Modifier.size(STRIP_STEP.dp, STRIP_HEIGHT.dp).background(Color(it))) }
            }
        }
        Text(
            label,
            color = ink,
            style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Swatch(argb: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val outer = OneConfigTheme.shape(SWATCH_RADIUS.dp)
    val inner = OneConfigTheme.shape((SWATCH_RADIUS - SWATCH_INSET).dp)
    Box(
        Modifier
            .size(SWATCH_SIZE.dp)
            .clip(outer)
            .border(if (selected) SWATCH_RING.dp else 1.dp, if (selected) OneConfigTheme.accent else SocialBorderColor, outer)
            .oneConfigClickable("Outline colour: $name", onClick, A11yRole.Option, selected = selected, shape = outer, indication = false)
            .padding(if (selected) SWATCH_INSET.dp else 0.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(inner).background(Color(argb)))
    }
}

private fun presetLabel(name: String, label: String): String = if (name == TRANS_PRESET) TRANS_LABEL else label

private fun percent(value: Float): String = "${value.roundToInt()}%"

private fun height(value: Float): String = if (value >= -HEIGHT_EPSILON) VANILLA_NAME else String.format(Locale.ROOT, "%.2f", value)

private val SWATCHES = listOf(
    0xFFFFFF to "white",
    0xE5484D to "red",
    0xF5A524 to "orange",
    0xF5E142 to "yellow",
    0x46C35A to "green",
    0x5BCEFA to "light blue",
    0x9B6BF2 to "purple",
)

private const val TRANS_PRESET = "trans"
private const val TRANS_LABEL = "Trans flag"
private const val PRESET_LABEL = "Preset"
private const val OPAQUE = 0xFF000000.toInt()
private const val ROW_GAP = 6f
private const val ROW_GUTTER = 8f
private const val LABEL_WIDTH = 60f
private const val VALUE_WIDTH = 56f
private const val CHIP_HEIGHT = 26f
private const val PRESET_CHIP_WIDTH = 68f
private const val PRESET_PADDING = 6f
private const val PRESET_GAP = 6f
private const val MAX_PRESET_COLORS = 5
private const val STRIP_STEP = 3f
private const val STRIP_HEIGHT = 12f
private const val STRIP_RADIUS = 3f
private const val SWATCH_GAP = 6f
private const val SWATCH_SIZE = 24f
private const val SWATCH_RADIUS = 6f
private const val SWATCH_RING = 2f
private const val SWATCH_INSET = 3f
private const val HEIGHT_EPSILON = 0.001f
