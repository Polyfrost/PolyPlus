@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun CosmeticsLoadingScreen(
    state: CosmeticLoadProgress.Snapshot,
    onRetry: () -> Unit,
) {
    val failed = state.failure != null
    val outdatedRuntime = state.failure == CosmeticLoadProgress.OUTDATED_RUNTIME
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        SocialText(
            if (failed) "Couldn't load cosmetics" else "Loading cosmetics",
            color = OneConfigTheme.current.textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        SocialText(
            when {
                outdatedRuntime -> "PolyPlus needs a newer Fabric Language Kotlin. Update it and restart the game."
                failed -> "PolyPlus couldn't reach its servers (${state.failure}). Check your connection and try again."
                else -> state.label
            },
            color = OneConfigTheme.current.textColorSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(360.dp),
            textAlign = TextAlign.Center,
        )
        if (failed) {
            if (!outdatedRuntime) {
                Spacer(Modifier.height(2.dp))
                SmallButton("Retry", iconPath = "refresh", primary = true, onClick = onRetry)
            }
        } else {
            ProgressBar(state.fraction, Modifier.width(360.dp))
        }
    }
}

private const val SWEEP_WIDTH_FRACTION = 0.35f

@Composable
private fun ProgressBar(fraction: Float?, modifier: Modifier) {
    val track = OneConfigTheme.current.chipBackground
    val shape = ppShape(4.dp)
    Box(
        modifier
            .height(8.dp)
            .clip(shape)
            .background(track)
            .border(1.dp, OneConfigTheme.current.borderColor, shape),
    ) {
        if (fraction != null) {
            val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(220))
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(shape)
                    .background(OneConfigTheme.accent),
            )
        } else {
            val transition = rememberInfiniteTransition(label = "cosmeticsLoadingSweep")
            val head by transition.animateFloat(
                initialValue = -SWEEP_WIDTH_FRACTION,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1_100, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "cosmeticsLoadingSweepHead",
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val width = maxWidth
                Box(
                    Modifier
                        .offset(x = width * head)
                        .fillMaxHeight()
                        .width(width * SWEEP_WIDTH_FRACTION)
                        .clip(shape)
                        .background(OneConfigTheme.accent),
                )
            }
        }
    }
}
