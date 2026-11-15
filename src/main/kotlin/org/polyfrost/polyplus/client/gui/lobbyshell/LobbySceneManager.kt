@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private val ManagerWidth = 860.dp
private val ManagerHeight = 600.dp
private val RailWidth = 140.dp
private const val SCREEN_SHARE = 0.8f

internal enum class SceneSection(val label: String) {
    SCENES("Scenes"),
    MUSIC("Music"),
    CAPTURE("Capture"),
}

internal class SceneDeletion(val title: String, val text: String, val entries: List<LobbySceneEntry>)

internal class SceneManagerState {
    var section by mutableStateOf(SceneSection.SCENES)
    var query by mutableStateOf("")
    var sort by mutableStateOf(SceneSort.NEWEST)
    var filter by mutableStateOf(SceneFilter.ALL)
    var expanded by mutableStateOf<String?>(null)
    val list = LazyListState()
    var selecting by mutableStateOf(false)
    var selected by mutableStateOf(emptySet<String>())
    var pending by mutableStateOf<SceneDeletion?>(null)
    var renaming by mutableStateOf<LobbySceneEntry?>(null)

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun leaveSelect() {
        selecting = false
        selected = emptySet()
    }

    fun open(section: SceneSection) {
        if (this.section == section) return
        leaveSelect()
        this.section = section
    }
}

@Composable
internal fun SceneManagerDialog(onDismiss: () -> Unit) {
    val state = remember { SceneManagerState() }
    ModalDialog(
        onDismiss = onDismiss,
        modifier = Modifier.height(fitted(ManagerHeight, horizontal = false)),
        width = fitted(ManagerWidth, horizontal = true),
        dismissOnScrim = true,
        scrollBody = false,
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.selecting) EscapeCloses(state::leaveSelect)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DialogTitle("Scenes and settings") }
                IconButton(icon = SOCIAL_ASSETS + "x-close.svg", contentDescription = "Close", onClick = onDismiss, size = ControlSize.Regular, tooltip = null, tooltipPlacement = TooltipPlacement.BelowStart)
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SceneRail(state)
                Divider(vertical = true)
                Crossfade(state.section, Modifier.weight(1f).fillMaxHeight(), Motion.finiteSpec(tween(SCENE_FADE_MILLIS)), "Scene section") { section ->
                    Box(Modifier.fillMaxSize()) {
                        when (section) {
                            SceneSection.SCENES -> ScenesSection(state)
                            SceneSection.MUSIC -> MusicSection()
                            SceneSection.CAPTURE -> CaptureSection(state)
                        }
                    }
                }
            }
        }
    }
    state.pending?.let { deletion ->
        DeletionConfirm(
            deletion,
            onDeleted = {
                val gone = deletion.entries.mapTo(HashSet()) { it.id }
                state.selected = state.selected - gone
                if (state.expanded in gone) state.expanded = null
            },
            onDismiss = { state.pending = null },
        )
    }
    state.renaming?.let { RenameSceneDialog(it) { state.renaming = null } }
}

@Composable
private fun fitted(preferred: Dp, horizontal: Boolean): Dp {
    val root = LocalRootSize.current
    val span = if (horizontal) root.width else root.height
    if (span <= 0f) return preferred
    val room = with(LocalDensity.current) { (span * SCREEN_SHARE).toDp() }
    return minOf(preferred, room).coerceAtLeast(0.dp)
}

@Composable
private fun SceneRail(state: SceneManagerState) {
    Column(Modifier.width(RailWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SceneSection.entries.forEach { section ->
            RailItem(section.label, state.section == section) { state.open(section) }
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val typography = OneConfigTheme.current.typography
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .selectableRow(selected, onClick, label, OneConfigTheme.shape(8.dp), role = A11yRole.Tab)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = SelectionDefaults.itemContentColor(selected), style = typography.body.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
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
