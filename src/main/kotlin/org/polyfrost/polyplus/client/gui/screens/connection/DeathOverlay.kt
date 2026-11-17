@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.connection

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if >= 26.3 || = 1.8.9 {
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.Outfit

internal class DeathDetails(
    val title: String,
    val cause: String?,
    val causeLink: Boolean,
    val scoreLabel: String,
    val scoreValue: String,
    val coordinates: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
)

private const val VIGNETTE_MILLIS = 700
private const val TITLE_DELAY = 450
private const val TITLE_MILLIS = 550
private const val DETAILS_DELAY = 750
private const val DETAILS_MILLIS = 450
private const val ACTIONS_DELAY = 950
private const val ACTIONS_MILLIS = 400
private const val TITLE_LIFT = 28f
private const val DETAILS_LIFT = 14f
private const val ACTIONS_LIFT = 10f

private val VignetteCore = Color(0x33500000)
private val VignetteMid = Color(0x8C5A0606)
private val VignetteEdge = Color(0xE61A0000)
private val FloorShade = Color(0xA6000000)
private val TitleRed = Color(0xFFFF5A52)

@Composable
private fun stage(delay: Int, duration: Int, easing: Easing = FastOutSlowInEasing): Float {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val value by Motion.animateFloatAsState(if (shown) 1f else 0f, tween(duration, delayMillis = delay, easing = easing))
    return value
}

private fun Modifier.rise(progress: Float, lift: Float) = graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * lift * density
}

@Composable
internal fun DeathOverlay(
    details: DeathDetails,
    ready: Boolean,
    respawned: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onCause: () -> Unit,
) {
    val vignette = stage(0, VIGNETTE_MILLIS, LinearOutSlowInEasing)
    val title = stage(TITLE_DELAY, TITLE_MILLIS)
    val body = stage(DETAILS_DELAY, DETAILS_MILLIS)
    val actions = stage(ACTIONS_DELAY, ACTIONS_MILLIS)

    val theme = OneConfigTheme.current
    val type = theme.typography
    val shadow = Shadow(theme.onMediaShadow, Offset(0f, 2f), 10f)

    Box(
        Modifier.fillMaxSize().drawBehind {
            val radius = size.maxDimension * 0.75f
            drawRect(
                Brush.radialGradient(
                    0f to VignetteCore,
                    0.55f to VignetteMid,
                    1f to VignetteEdge,
                    center = center,
                    radius = radius,
                ),
                alpha = vignette,
            )
            drawRect(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to FloorShade),
                alpha = vignette,
            )
        },
        contentAlignment = BiasAlignment(0f, -0.2f),
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                details.title,
                Modifier.rise(title, TITLE_LIFT),
                color = TitleRed,
                style = type.display.copy(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 46.sp, letterSpacing = 1.sp, shadow = shadow),
                textAlign = TextAlign.Center,
                legacyFormatting = false,
            )
            Column(
                Modifier.padding(top = 14.dp).rise(body, DETAILS_LIFT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                details.cause?.takeIf { it.isNotBlank() }?.let { CauseLine(it, details.causeLink, type.heading.copy(fontWeight = FontWeight.Medium, shadow = shadow), theme.onMedia, onCause) }
                Text(
                    statsLine(details, theme.onMedia.copy(alpha = 0.6f), theme.warning),
                    color = theme.onMedia.copy(alpha = 0.6f),
                    style = type.small.copy(shadow = shadow),
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.padding(top = 32.dp).widthIn(max = 420.dp).fillMaxWidth().rise(actions, ACTIONS_LIFT),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = details.primaryLabel,
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                    style = ControlStyle.Primary,
                    size = ControlSize.Large,
                    enabled = ready && !respawned,
                )
                details.secondaryLabel?.let { label ->
                    TextButton(
                        text = label,
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f),
                        style = ControlStyle.Secondary,
                        size = ControlSize.Large,
                        enabled = ready,
                    )
                }
            }
        }
    }
}

@Composable
private fun CauseLine(cause: String, link: Boolean, style: TextStyle, color: Color, onClick: () -> Unit) {
    if (!link) {
        Text(cause, color = color.copy(alpha = 0.92f), style = style, textAlign = TextAlign.Center, legacyFormatting = false)
        return
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Text(
        cause,
        Modifier.hoverable(source).clickable(interactionSource = source, indication = null, onClick = onClick),
        color = color,
        style = style.copy(textDecoration = if (hovered) TextDecoration.Underline else TextDecoration.None),
        textAlign = TextAlign.Center,
        legacyFormatting = false,
    )
}

private fun statsLine(details: DeathDetails, muted: Color, highlight: Color): AnnotatedString = buildAnnotatedString {
    append(details.scoreLabel)
    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) { append(details.scoreValue) }
    withStyle(SpanStyle(color = muted.copy(alpha = muted.alpha * 0.6f))) { append("   ·   ") }
    append("XYZ: ")
    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(details.coordinates) }
}
//?}
