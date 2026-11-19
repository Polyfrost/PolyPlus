@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.OnboardingKeys
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.onboarding.live.prepareLiveScenes
import org.polyfrost.polyplus.client.gui.onboarding.live.releaseLiveScenes
import org.polyfrost.polyplus.client.lobby.LobbyFraming

internal class CardFlowFrame(val width: Float, val height: Float, val lobbyHost: Boolean)

internal class CardFlowActions(
    val onLeave: (() -> Unit)?,
    val onSkip: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
internal fun OnboardingCardFlow(
    topics: List<CardTopic>,
    choices: OnboardingChoices,
    frame: CardFlowFrame,
    actions: CardFlowActions,
) {
    DisposableEffect(Unit) {
        prepareLiveScenes()
        onDispose { releaseLiveScenes() }
    }
    var index by remember { mutableIntStateOf(0) }
    var started by remember { mutableIntStateOf(-1) }
    var direction by remember { mutableIntStateOf(1) }
    var picked by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    val enter = remember { Animatable(0f) }
    val pick = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shown: () -> Float = { if (started == index) enter.value else 0f }
    val gone: () -> Float = { exit.value }

    LaunchedEffect(index) {
        picked = null
        pick.snapTo(0f)
        exit.snapTo(0f)
        enter.snapTo(0f)
        started = index
        busy = false
        if (Motion.reduced) enter.snapTo(1f) else enter.animateTo(1f, spring(ENTER_DAMPING, Spring.StiffnessMediumLow))
    }

    fun moveOn(picking: Boolean) {
        scope.launch {
            if (Motion.reduced) {
                pick.snapTo(1f)
            } else {
                if (picking) {
                    pick.animateTo(1f, tween(PICK_MS, easing = FastOutSlowInEasing))
                    delay(HOLD_MS)
                }
                exit.animateTo(1f, tween(EXIT_MS, easing = FastOutLinearInEasing))
            }
            direction = 1
            index += 1
        }
    }

    fun answer(ours: Boolean) {
        val topic = topics.getOrNull(index) as? CardTopic.Tweak ?: return
        if (busy || shown() < INPUT_READY) return
        busy = true
        choices.pick(topic.tweak, ours)
        picked = ours
        if (frame.lobbyHost) LobbyFraming.react(if (ours) LobbyFraming.Reaction.NOD else LobbyFraming.Reaction.GLANCE)
        moveOn(picking = true)
    }

    fun proceed() {
        if (topics.getOrNull(index) !is CardTopic.Screens || busy || shown() < INPUT_READY) return
        busy = true
        moveOn(picking = false)
    }

    fun goBack() {
        if (busy) return
        if (index == 0) {
            actions.onLeave?.invoke()
            return
        }
        direction = -1
        index -= 1
    }

    val topic = topics.getOrNull(index)
    val canGoBack = index > 0 || actions.onLeave != null
    SideEffect {
        OnboardingKeys.update(
            CARD_PAGE_BASE + index,
            next = when (topic) {
                is CardTopic.Tweak -> ({ answer(choices.ours(topic.tweak)) })
                is CardTopic.Screens -> ({ proceed() })
                null -> actions.onFinish
            },
            back = if (canGoBack) ({ goBack() }) else null,
            blocked = null,
            left = if (topic is CardTopic.Tweak) ({ answer(false) }) else null,
            right = if (topic is CardTopic.Tweak) ({ answer(true) }) else null,
        )
    }
    Announce(cardSpeech(topic))

    Box(Modifier.fillMaxSize()) {
        val motion = CardMotion(shown, gone, direction)
        when (topic) {
            is CardTopic.Tweak -> {
                CardTitle(topic.tweak.title, frame.width, motion)
                TweakStage(
                    TweakStageState(topic.tweak, choices.answered(topic.tweak), choices.ours(topic.tweak), picked, pick.value),
                    frame,
                    motion,
                    { if (!busy) choices.pick(topic.tweak, true) },
                    ::answer,
                )
            }
            is CardTopic.Screens -> {
                CardTitle(SCREENS_TITLE, frame.width, motion)
                ScreensCard(topic, choices, frame, motion, ::proceed)
            }
            null -> ClosingCard(frame, actions.onFinish)
        }
        val fill = when (topic) {
            is CardTopic.Tweak -> pick.value
            is CardTopic.Screens -> exit.value
            null -> 1f
        }
        CardTopBar(index, topics.size, fill, frame.width, if (canGoBack) ::goBack else null, if (topic != null) actions.onSkip else null)
    }
}

internal class CardMotion(val shown: () -> Float, val gone: () -> Float, val direction: Int)

@Composable
private fun CardTitle(title: String, width: Float, motion: CardMotion) {
    Box(
        Modifier
            .offset(0.dp, TITLE_Y.dp)
            .width(width.dp)
            .graphicsLayer { alpha = (motion.shown() * (1f - motion.gone())).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) { PageTitle(title) }
}

@Composable
private fun CardTopBar(index: Int, count: Int, fill: Float, width: Float, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    if (onBack != null) {
        Box(Modifier.offset(BAR_INSET.dp, BAR_Y.dp).size(BAR_HEIGHT.dp), contentAlignment = Alignment.Center) {
            IconButton(icon = LobbyIcons.BACK, contentDescription = "Back", onClick = onBack, size = ControlSize.Regular, tooltip = null)
        }
    }
    if (count > 0) {
        Box(
            Modifier
                .offset(PROGRESS_X.dp, BAR_Y.dp)
                .size((width - PROGRESS_X * 2f).dp, BAR_HEIGHT.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProgressBar(((index + fill) / count).coerceIn(0f, 1f), Modifier.fillMaxWidth())
        }
    }
    if (onSkip != null) {
        Box(
            Modifier.offset((width - SKIP_WIDTH - BAR_INSET).dp, BAR_Y.dp).size(SKIP_WIDTH.dp, BAR_HEIGHT.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton("Skip setup", onSkip, style = ControlStyle.Ghost, size = ControlSize.Regular)
        }
    }
}

private fun cardSpeech(topic: CardTopic?): String = when (topic) {
    is CardTopic.Tweak ->
        "${topic.tweak.title}. Left arrow for $VANILLA_NAME, right arrow for $brandName."
    is CardTopic.Screens ->
        "$SCREENS_TITLE. Pick $VANILLA_NAME or $brandName for each screen, then continue."
    null -> "$CLOSING_TITLE. $CLOSING_TEXT"
}

internal const val MAIN_MENU_ASSETS = "assets/polyplus/mainmenu/"
internal const val CARD_TOP = 110f
internal const val CARD_INSET = 24f

private const val CARD_PAGE_BASE = 1000
private const val SCREENS_TITLE = "Your screens"
private const val TITLE_Y = 66f
private const val BAR_Y = 20f
private const val BAR_INSET = 20f
private const val PROGRESS_X = 150f
private const val BAR_HEIGHT = 36f
private const val SKIP_WIDTH = 120f
private const val ENTER_DAMPING = 0.74f
private const val INPUT_READY = 0.6f
private const val PICK_MS = 380
private const val HOLD_MS = 160L
private const val EXIT_MS = 200
