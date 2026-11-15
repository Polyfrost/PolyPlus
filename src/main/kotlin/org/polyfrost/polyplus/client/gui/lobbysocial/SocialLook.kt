@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.lobbyshell.lobbyHover
import org.polyfrost.polyplus.client.gui.lobbyshell.panel

internal enum class SocialSurface { Glass, None }

internal enum class SocialSelection { Fill, Hairline }

internal enum class SocialTimeline { Bubbles, Rows }

internal enum class SocialTimeStyle { Relative, Clock }

internal enum class SocialGroupFace { TwoHeads, Grid }

internal data class SocialLook(
    val surface: SocialSurface,
    val selection: SocialSelection,
    val timeline: SocialTimeline,
    val time: SocialTimeStyle,
    val groupFace: SocialGroupFace,
    val sceneHover: Boolean,
    val backButton: Boolean,
    val inviteBanner: Boolean,
    val narrowLists: Boolean,
) {
    companion object {
        val Lobby = SocialLook(
            surface = SocialSurface.Glass,
            selection = SocialSelection.Fill,
            timeline = SocialTimeline.Bubbles,
            time = SocialTimeStyle.Relative,
            groupFace = SocialGroupFace.TwoHeads,
            sceneHover = true,
            backButton = true,
            inviteBanner = true,
            narrowLists = false,
        )

        val InGame = SocialLook(
            surface = SocialSurface.None,
            selection = SocialSelection.Hairline,
            timeline = SocialTimeline.Rows,
            time = SocialTimeStyle.Clock,
            groupFace = SocialGroupFace.Grid,
            sceneHover = false,
            backButton = false,
            inviteBanner = false,
            narrowLists = true,
        )
    }
}

internal val LocalSocialLook = staticCompositionLocalOf { SocialLook.Lobby }

@Composable
internal fun Modifier.socialSurface(): Modifier =
    if (LocalSocialLook.current.surface == SocialSurface.Glass) panel() else this

@Composable
internal fun socialRowFilled(selected: Boolean): Boolean =
    selected && LocalSocialLook.current.selection == SocialSelection.Fill

internal fun Modifier.socialSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    shape: Shape? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val outline = shape ?: SocialFieldShape
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val filled = socialRowFilled(selected)
    val edge = when {
        selected && !filled -> OneConfigTheme.accent
        hovered && !selected -> OneConfigTheme.current.borderColor
        else -> Color.Transparent
    }
    clip(outline)
        .background(if (filled) OneConfigTheme.accent else Color.Transparent)
        .border(1.dp, edge, outline)
        .hoverable(source)
        .oneConfigClickable(label, onClick, A11yRole.Option, selected = selected, shape = outline, indication = false, interactionSource = source)
}

@Composable
internal fun socialHover(name: String?): Modifier =
    if (LocalSocialLook.current.sceneHover) lobbyHover(name) else Modifier

internal val SocialLook.paneEdge: Dp
    get() = if (surface == SocialSurface.None) 24.dp else 12.dp

@Composable
internal fun threadTime(thread: SocialThread): String? =
    if (LocalSocialLook.current.time == SocialTimeStyle.Clock) thread.clock else thread.time
