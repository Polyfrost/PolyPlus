@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.party.PartyScene
import androidx.compose.ui.text.style.TextOverflow

internal const val SCENE_FADE_MILLIS = 140
private const val STAR_FILLED = "star-filled"

@Composable
internal fun SceneNote(text: String, color: Color = OneConfigTheme.current.textColorSecondary) {
    Text(text, Modifier, color, OneConfigTheme.current.typography.small)
}

@Composable
internal fun FavouriteStar() {
    ThemedIcon(STAR_FILLED, OneConfigTheme.accent, 12.dp)
}

@Composable
internal fun PartyBackdrop(following: Boolean) {
    val keepOwn = PartyScene.keepOwnBackdrop
    val typography = OneConfigTheme.current.typography
    if (following) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Following your party leader", style = typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(
                PartyScene.followedLabel()?.let { "$it. Only the leader chooses it." } ?: "Waiting for the leader's scene.",
                color = OneConfigTheme.current.textColorSecondary,
                style = typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
    ToggleRow(
        "Keep my own backdrop",
        keepOwn,
        { PartyScene.setKeepOwnBackdrop(it) },
        Modifier.fillMaxWidth(),
        description = "Use your own scene and time in parties.",
        control = ToggleControl.Switch,
        framed = false,
    )
}

internal fun singleDeletion(entry: LobbySceneEntry): SceneDeletion =
    SceneDeletion("Delete ${entry.label}?", "This scene will be deleted from your computer.", listOf(entry))

internal fun removeScenes(entries: List<LobbySceneEntry>): Int {
    val showing = entries.any { it.id == LobbyScene.currentId }
    val removed = LobbySceneStore.delete(entries)
    if (showing) LobbyScene.reload()
    return removed
}

internal fun pickScene(id: String?) {
    if (LobbySceneStore.pinned == id) return
    LobbySceneStore.pinned = id
    if (id == null || LobbyScene.currentId != id) LobbyScene.reload()
}

internal fun plural(count: Int, word: String): String = if (count == 1) "1 $word" else "$count ${word}s"
