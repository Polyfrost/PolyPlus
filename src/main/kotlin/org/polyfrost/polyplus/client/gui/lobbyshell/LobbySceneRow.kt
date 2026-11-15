@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.AnimatedVisibility
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Checkbox
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import androidx.compose.ui.text.style.TextOverflow

internal const val AUTOMATIC_KEY = "automatic"
private val HeaderHeight = 52.dp
private val StatusHeight = 20.dp
private val WideGrid = 480.dp
private const val WIDE_COLUMNS = 3
private const val NARROW_COLUMNS = 2

@Composable
internal fun SceneCard(state: SceneManagerState, entry: LobbySceneEntry?, canPick: Boolean) {
    val key = entry?.id ?: AUTOMATIC_KEY
    val checked = if (state.selecting && entry != null && !entry.bundled) entry.id in state.selected else null
    val inert = state.selecting && (entry == null || entry.bundled)
    val open = !state.selecting && state.expanded == key
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(12.dp)
    val border by animateColorAsState(
        when {
            open || checked == true -> OneConfigTheme.accent
            hover.hovered && !inert -> SocialHoverBorder
            else -> SocialBorderColor
        },
        Motion.finiteSpec(tween(SCENE_FADE_MILLIS)),
    )
    Column(Modifier.fillMaxWidth().clip(shape).background(CardSurface).border(1.dp, border, shape)) {
        val header = HeaderState(open, checked, inert)
        val click: () -> Unit = {
            when {
                checked != null && entry != null -> state.toggle(entry.id)
                else -> state.expanded = if (open) null else key
            }
        }
        SceneCardHeader(entry, header, shape, hover.interactionSource, click)
        AnimatedVisibility(
            open,
            enter = Motion.enter(expandVertically(tween(SCENE_FADE_MILLIS)) + fadeIn(tween(SCENE_FADE_MILLIS))),
            exit = Motion.exit(shrinkVertically(tween(SCENE_FADE_MILLIS)) + fadeOut(tween(SCENE_FADE_MILLIS))),
        ) {
            SceneCardBody(state, entry, canPick)
        }
    }
}

private class HeaderState(val open: Boolean, val checked: Boolean?, val inert: Boolean)

@Composable
private fun SceneCardHeader(entry: LobbySceneEntry?, header: HeaderState, shape: Shape, interaction: MutableInteractionSource, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val problem = entry?.let(::sceneProblem)
    val title = entry?.label ?: "Automatic"
    val detail = problem ?: entry?.let(::sceneLine) ?: "Best recent scene"
    val checked = header.checked
    val turn by animateFloatAsState(if (header.open) 0f else 180f, Motion.finiteSpec(tween(SCENE_FADE_MILLIS)))
    val narration = "$title, $detail"
    val click = when {
        header.inert -> Modifier
        checked != null -> Modifier.oneConfigClickable(narration, onClick, A11yRole.Checkbox, checked = checked, shape = shape, interactionSource = interaction)
        else -> Modifier.oneConfigClickable(narration, onClick, selected = header.open.takeIf { it }, shape = shape, indication = !header.open, interactionSource = interaction)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .then(click)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (checked != null) Checkbox(checked, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (header.inert) theme.textColorSecondary else theme.textColor, style = theme.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = if (problem != null) SocialDangerText else theme.mutedText, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (entry?.favourite == true) FavouriteStar()
        if (checked == null && !header.inert) ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", theme.textColorSecondary, 14.dp, Modifier.rotate(turn))
    }
}

@Composable
private fun SceneCardBody(state: SceneManagerState, entry: LobbySceneEntry?, canPick: Boolean) {
    val pinned = LobbySceneStore.pinned
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider()
        StatusLine(entry, pinned)
        FactGrid(entry?.let(::sceneFacts) ?: automaticFacts())
        SceneActions(state, entry, canPick, pinned)
    }
}

@Composable
private fun StatusLine(entry: LobbySceneEntry?, pinned: String?) {
    val picked = pinned == entry?.id
    val showing = entry != null && LobbyScene.currentId == entry.id
    val unavailable = entry != null && !LobbySceneStore.usable(entry)
    val theme = OneConfigTheme.current
    Row(Modifier.height(StatusHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            picked -> Badge("Picked".uppercase(), tone = BadgeTone.Accent, style = BadgeStyle.Solid)
            showing -> Badge("Showing".uppercase(), style = BadgeStyle.Hairline)
        }
        if (entry?.manual == true) Badge("Manual".uppercase(), style = BadgeStyle.Hairline)
        if (unavailable) DangerTag("Unavailable".uppercase())
        if (!picked && !showing && entry?.manual != true && !unavailable) Text("Not in use", color = theme.mutedText, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    val theme = OneConfigTheme.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= WideGrid) WIDE_COLUMNS else NARROW_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            facts.chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.forEach { (label, value) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, color = theme.mutedText, style = theme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            Text(value, style = theme.typography.label.copy(fontWeight = FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SceneActions(state: SceneManagerState, entry: LobbySceneEntry?, canPick: Boolean, pinned: String?) {
    val inUse = pinned == entry?.id
    val usable = entry == null || LobbySceneStore.usable(entry)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (entry != null && !entry.bundled) {
            TextButton(
                text = "Delete",
                onClick = { state.pending = singleDeletion(entry) },
                size = ControlSize.Large,
                style = ControlStyle.DangerFilled,
                dangerColor = KitDangerColor,
            )
            if (LobbySceneStore.canRename(entry)) {
                TextButton(text = "Rename", onClick = { state.renaming = entry }, size = ControlSize.Large, style = ControlStyle.Secondary)
            }
            TextButton(
                text = if (entry.favourite) "Unfavourite" else "Favourite",
                onClick = { LobbySceneStore.setFavourite(entry, !entry.favourite) },
                size = ControlSize.Large,
                style = ControlStyle.Secondary,
            )
        }
        TextButton(
            text = if (inUse) "In use" else "Use",
            onClick = { pickScene(entry?.id) },
            size = ControlSize.Large,
            style = ControlStyle.Primary,
            enabled = canPick && usable && !inUse,
        )
    }
}

private fun automaticFacts(): List<Pair<String, String>> {
    val usable = LobbySceneStore.usableScenes.size
    val showing = LobbyScene.currentId?.let { id -> (LobbySceneStore.captures + LobbySceneStore.bundledScenes).firstOrNull { it.id == id }?.label }
    return listOf(
        "Picks" to "Best recent scene",
        "Choosing from" to if (usable == 0) "Polyfrost scenes" else plural(usable, "capture"),
        "Showing now" to (showing ?: MISSING_FACT),
    )
}
