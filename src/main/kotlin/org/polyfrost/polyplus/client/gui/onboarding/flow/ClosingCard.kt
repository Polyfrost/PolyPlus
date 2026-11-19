@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayButton
import org.polyfrost.polyplus.client.lobby.LobbyFraming

@Composable
internal fun ClosingCard(frame: CardFlowFrame, onPlay: () -> Unit) {
    val appear = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (frame.lobbyHost) LobbyFraming.react(LobbyFraming.Reaction.WAVE)
        if (!Motion.reduced) appear.animateTo(1f, tween(CLOSING_MS, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PageTitle(CLOSING_TITLE)
            Spacer(Modifier.height(TEXT_GAP.dp))
            Text(
                CLOSING_TEXT,
                modifier = Modifier.width((frame.width - TEXT_MARGIN * 2f).dp),
                color = OneConfigTheme.current.mutedText,
                style = OneConfigTheme.current.typography.body,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PLAY_GAP.dp))
            PlayButton(Modifier.width(PLAY_WIDTH.dp), "Play", null, zoom = false, onClick = onPlay)
        }
    }
}

internal const val CLOSING_TITLE = "You’re all set"
internal const val CLOSING_TEXT = "You can change any of this later in Mods."

private const val CLOSING_MS = 400
private const val TEXT_GAP = 10f
private const val TEXT_MARGIN = 80f
private const val PLAY_GAP = 28f
private const val PLAY_WIDTH = 240f
