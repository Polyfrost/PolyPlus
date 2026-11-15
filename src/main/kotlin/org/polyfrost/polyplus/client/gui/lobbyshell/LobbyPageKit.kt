@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeColors
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.LocalAnnounceTitles
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState as KitEmptyState

internal object LobbyIcons {
    const val BACK = "left-arrow"
    const val SEARCH = "search"
    const val PLUS = "plus"
    const val CLOSE = "close"
    const val TRASH = "trash"
    const val REFRESH = "refresh"
    const val TICK = "tick"
    const val COPY = "copy"
    const val MORE = "dots-vertical"
    const val FILTER = "align"
}

@Composable
internal fun PageScrim() {
    Box(Modifier.fillMaxSize().background(OneConfigTheme.current.scrimColor))
}

@Composable
internal fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = pageTitleStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

@Composable
internal fun pageTitleStyle(): TextStyle = OneConfigTheme.current.typography.title.copy(fontSize = 24.sp)

@Composable
internal fun PanelTitle(text: String, modifier: Modifier = Modifier) {
    if (LocalAnnounceTitles.current) Announce(text)
    Text(text, modifier, style = OneConfigTheme.current.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(icon = LobbyIcons.BACK, contentDescription = "Back", onClick = onBack, size = ControlSize.Regular, tooltipPlacement = TooltipPlacement.BelowStart)
}

@Composable
internal fun EmptyState(title: String, modifier: Modifier = Modifier, text: String = "", action: (@Composable () -> Unit)? = null) {
    KitEmptyState(title, modifier, text = text.takeIf { it.isNotEmpty() }, action = action)
}

@Composable
internal fun Modifier.rowFrame(selected: Boolean, hovered: Boolean, shape: Shape = SocialFieldShape): Modifier =
    this
        .clip(shape)
        .background(if (selected) OneConfigTheme.accent else Color.Transparent)
        .border(1.dp, if (hovered && !selected) OneConfigTheme.current.borderColor else Color.Transparent, shape)

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
    val shape = OneConfigTheme.shape(16.dp)
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

@Composable
internal fun DangerTag(text: String, modifier: Modifier = Modifier) {
    Badge(
        text,
        modifier.height(18.dp),
        tone = BadgeTone.Danger,
        style = BadgeStyle.Hairline,
        colors = BadgeColors(
            container = Color.Black.copy(alpha = 0.6f),
            content = SocialDangerColor,
            border = OneConfigTheme.current.textColorSecondary.copy(alpha = 0.7f),
        ),
        shape = OneConfigTheme.shape(5.dp),
        labelStyle = TextStyle(fontSize = 10.sp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    )
}
