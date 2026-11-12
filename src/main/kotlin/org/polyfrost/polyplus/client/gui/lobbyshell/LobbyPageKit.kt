@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.LocalAnnounceTitles
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.ppShape

private const val PAGE_SCRIM_ALPHA = 0.35f
private const val SELECTED_MUTED_ALPHA = 0.8f
private const val TAG_HAIRLINE_ALPHA = 0.7f
private const val TAG_FILL_ALPHA = 0.6f

internal object LobbyIcons {
    const val BACK = "left-arrow"
    const val SEARCH = "search"
    const val PLUS = "plus"
    const val CLOSE = "close"
    const val TRASH = "trash"
    const val REFRESH = "refresh"
    const val TICK = "tick"
    const val COPY = "copy"
    const val UP = "up"
    const val MORE = "dots-vertical"
    const val FILTER = "align"
}

internal enum class TagTone { Dark, Accent, Danger }

@Composable
internal fun PageScrim() {
    Box(Modifier.fillMaxSize().background(OneConfigTheme.current.pageBackground.copy(alpha = PAGE_SCRIM_ALPHA)))
}

@Composable
internal fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Label(text, 24.sp, modifier, weight = FontWeight.SemiBold)
}

@Composable
internal fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    if (LocalAnnounceTitles.current) Announce(text)
    Label(text, 18.sp, modifier, weight = FontWeight.Medium)
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton("Back", null, Modifier, null, TooltipPlacement.BelowStart, onBack) { ThemedIcon(LobbyIcons.BACK, SocialTextPrimary, 16.dp) }
}

@Composable
internal fun EmptyState(title: String, modifier: Modifier = Modifier, text: String = "", action: (@Composable () -> Unit)? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelTitle(title)
            if (text.isNotEmpty()) SocialText(text, 14.sp, color = MutedText, textAlign = TextAlign.Center)
            action?.let { Box(Modifier.padding(top = 8.dp)) { it() } }
        }
    }
}

@Composable
internal fun Tag(text: String, tone: TagTone = TagTone.Dark, modifier: Modifier = Modifier) {
    val theme = OneConfigTheme.current
    val shape = ppShape(5.dp)
    val surface = if (tone == TagTone.Accent) {
        Modifier.background(OneConfigTheme.accent, shape)
    } else {
        Modifier.background(Color.Black.copy(alpha = TAG_FILL_ALPHA), shape).border(1.dp, theme.textColorSecondary.copy(alpha = TAG_HAIRLINE_ALPHA), shape)
    }
    val ink = when (tone) {
        TagTone.Danger -> SocialDangerColor
        TagTone.Accent -> theme.accentTextColor
        else -> Color.White
    }
    Box(modifier.height(18.dp).clip(shape).then(surface).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Label(text.uppercase(), 10.sp, color = ink, weight = FontWeight.Medium)
    }
}

@Composable
internal fun Modifier.rowFrame(selected: Boolean, hovered: Boolean, shape: Shape = SocialFieldShape): Modifier =
    this
        .clip(shape)
        .background(if (selected) OneConfigTheme.accent else Color.Transparent)
        .border(1.dp, if (hovered && !selected) OneConfigTheme.current.borderColor else Color.Transparent, shape)

@Composable
internal fun rowInk(selected: Boolean): Color = if (selected) OneConfigTheme.current.accentTextColor else SocialTextPrimary

@Composable
internal fun rowMuted(selected: Boolean): Color =
    if (selected) OneConfigTheme.current.accentTextColor.copy(alpha = SELECTED_MUTED_ALPHA) else MutedText

internal fun leftColumn(): Modifier = Modifier
    .padding(start = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
    .width(LobbyLayout.ChatWidth)
    .fillMaxHeight()

internal fun centerColumn(): Modifier = Modifier
    .padding(start = LobbyLayout.BarStart, end = LobbyLayout.BarEnd, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
    .fillMaxSize()

internal fun BoxScope.rightColumn(): Modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(end = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.RightColumnBottom)
    .width(LobbyLayout.UnitWidth)
    .fillMaxHeight()

@Composable
internal fun Modifier.cardFrame(selected: Boolean, hovered: Boolean): Modifier {
    val shape = ppShape(16.dp)
    val border = when {
        selected -> OneConfigTheme.accent
        hovered -> SocialHoverBorder
        else -> SocialBorderColor
    }
    return this
        .clip(shape)
        .background(CardSurface)
        .border(1.dp, border, shape)
}

@Composable
internal fun rememberDoubleClick(onDouble: () -> Unit): () -> Unit {
    val lastClick = remember { longArrayOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - lastClick[0] < DOUBLE_CLICK_MILLIS) onDouble()
        lastClick[0] = now
    }
}

private const val DOUBLE_CLICK_MILLIS = 250L
