@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.lobbyshell.lobbyHover
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.rowInk
import org.polyfrost.polyplus.client.gui.lobbyshell.rowMuted

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
    val rename: Boolean,
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
            rename = true,
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
            rename = false,
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

@Composable
internal fun Modifier.socialRow(selected: Boolean, hovered: Boolean): Modifier {
    val filled = socialRowFilled(selected)
    val framed = rowFrame(filled, hovered && selected == filled)
    return if (selected && !filled) framed.border(1.dp, OneConfigTheme.accent, SocialFieldShape) else framed
}

@Composable
internal fun socialRowInk(selected: Boolean): Color = rowInk(socialRowFilled(selected))

@Composable
internal fun socialRowMuted(selected: Boolean): Color = rowMuted(socialRowFilled(selected))

@Composable
internal fun socialHover(name: String?): Modifier =
    if (LocalSocialLook.current.sceneHover) lobbyHover(name) else Modifier

internal val SocialLook.paneEdge: Dp
    get() = if (surface == SocialSurface.None) 24.dp else 12.dp

@Composable
internal fun threadTime(thread: SocialThread): String? =
    if (LocalSocialLook.current.time == SocialTimeStyle.Clock) thread.clock else thread.time
