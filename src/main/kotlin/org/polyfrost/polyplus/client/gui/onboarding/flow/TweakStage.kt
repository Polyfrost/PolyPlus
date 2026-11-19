@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTweak
import org.polyfrost.polyplus.client.gui.onboarding.live.LiveTweakPreview

internal class TweakStageState(
    val tweak: LiveTweak,
    val answered: Boolean,
    val currentOurs: Boolean,
    val picked: Boolean?,
    val pick: Float,
)

@Composable
internal fun TweakStage(state: TweakStageState, frame: CardFlowFrame, motion: CardMotion, onTune: () -> Unit, onPick: (Boolean) -> Unit) {
    val stageWidth = frame.width - CARD_INSET * 2f
    val stageHeight = frame.height - CARD_TOP - CARD_INSET
    val sideWidth = (stageWidth - SIDE_GAP) / 2f
    val picked = state.picked
    val pick = state.pick
    Box(
        Modifier
            .offset(CARD_INSET.dp, CARD_TOP.dp)
            .size(stageWidth.dp, stageHeight.dp)
            .graphicsLayer { alpha = (motion.shown() * (1f - motion.gone())).coerceIn(0f, 1f) }
            .clip(OneConfigTheme.shape(CARD_RADIUS.dp)),
    ) {
        val order = if (picked == false) listOf(true, false) else listOf(false, true)
        order.forEach { ours ->
            val chosen = picked == ours
            val dropped = picked != null && !chosen
            val home = if (ours) sideWidth + SIDE_GAP else 0f
            val x = when {
                chosen -> home * (1f - pick)
                dropped && ours -> home + sideWidth * pick
                else -> home
            }
            val width = when {
                chosen -> sideWidth + (stageWidth - sideWidth) * pick
                dropped -> sideWidth * (1f - pick)
                else -> sideWidth
            }.coerceAtLeast(0f)
            TweakSide(
                state.tweak,
                ours,
                if (picked != null) chosen else state.currentOurs == ours,
                Modifier
                    .offset {
                        val arrive = motion.shown().coerceIn(0f, 1f)
                        IntOffset((x + (1f - arrive) * motion.direction * SIDE_SHIFT).dp.roundToPx(), 0)
                    }
                    .size(width.dp, stageHeight.dp),
                sideWidth - FOOTER_PADDING * 2f,
                onTune,
            ) { onPick(ours) }
        }
    }
}

@Composable
private fun TweakSide(
    tweak: LiveTweak,
    ours: Boolean,
    selected: Boolean,
    modifier: Modifier,
    controlsWidth: Float,
    onTune: () -> Unit,
    onClick: () -> Unit,
) {
    val hover = rememberHoverState()
    val name = if (ours) brandName else VANILLA_NAME
    Column(modifier.cardFrame(selected = selected, hovered = hover.hovered)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .oneConfigClickable(name, onClick, A11yRole.Button, interactionSource = hover.interactionSource),
        ) {
            LiveTweakPreview(tweak, ours, Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(footerHeight(tweak).dp)
                .padding(FOOTER_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(FOOTER_GAP.dp),
        ) {
            Text(
                name,
                modifier = Modifier.oneConfigClickable(null, onClick, indication = false),
                style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (ours && tweak.tunable) TuningControls(tweak, controlsWidth, onTune)
        }
    }
}

private fun footerHeight(tweak: LiveTweak): Float =
    FOOTER_PADDING * 2f + LABEL_HEIGHT + tweak.tuningRows * (FOOTER_GAP + TUNING_ROW_HEIGHT)

private const val SIDE_GAP = 12f
private const val SIDE_SHIFT = 40f
private const val CARD_RADIUS = 16f
private const val FOOTER_PADDING = 14f
private const val FOOTER_GAP = 8f
private const val LABEL_HEIGHT = 22f
