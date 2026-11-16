@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal const val SCENE_FADE_MILLIS = 140
private const val STAR_FILLED = "star-filled"
private const val SHORT_AGE_DAYS = 7L
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val OLD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

internal class SceneDeletion(val title: String, val text: String, val entries: List<LobbySceneEntry>)

@Composable
internal fun SceneNote(text: String, color: Color = OneConfigTheme.current.textColorSecondary) {
    Text(text, Modifier, color, OneConfigTheme.current.typography.small)
}

@Composable
internal fun FavouriteStar() {
    ThemedIcon(STAR_FILLED, OneConfigTheme.accent, 12.dp)
}

@Composable
internal fun DeletionConfirm(deletion: SceneDeletion, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = deletion.title,
        text = deletion.text,
        confirmLabel = "Delete",
        dismissLabel = "Cancel",
        icon = LobbyIcons.TRASH,
        danger = true,
        confirmEnabled = true,
        onConfirm = {
            onDismiss()
            removeScenes(deletion.entries)
            onDeleted()
        },
        onDismiss = onDismiss,
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
    val before = LobbySceneStore.pinned to LobbySceneStore.mode
    LobbySceneStore.choose(id)
    if (before == LobbySceneStore.pinned to LobbySceneStore.mode) return
    if (id == null || LobbyScene.currentId != id) LobbyScene.reload()
}

internal fun plural(count: Int, word: String): String = if (count == 1) "1 $word" else "$count ${word}s"

@Composable
internal fun SceneMoodBackdrop(entry: LobbySceneEntry, modifier: Modifier = Modifier) {
    Box(modifier.background(moodBrush(sceneMood(entry))))
}

private fun moodBrush(mood: SceneMood): Brush =
    Brush.verticalGradient(mood.fadeStart to Color(mood.top), 1f to Color(mood.bottom))

internal fun sceneTileLine(entry: LobbySceneEntry): String {
    if (entry.bundled) return "Polyfrost"
    val world = LobbySceneStore.worldName(entry).trim()
    val sameName = world.isBlank() || world.equals(entry.label.trim(), ignoreCase = true)
    val where = if (sameName) scenePlace(entry).ifBlank { world } else world
    return listOfNotNull(where.takeIf { it.isNotBlank() }, shortAge(entry.capturedAt)).joinToString(" · ")
}

internal fun shortAge(capturedAt: Long, now: Long = System.currentTimeMillis()): String? {
    if (capturedAt <= 0L) return null
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(capturedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "Today"
        days < SHORT_AGE_DAYS -> "${days}d ago"
        day.year == today.year -> SHORT_DATE.format(day)
        else -> OLD_DATE.format(day)
    }
}
