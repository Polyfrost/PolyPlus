@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.vanillamenu.LobbyElementPanel
import org.polyfrost.polyplus.client.gui.vanillamenu.LobbyHudMenu
import org.polyfrost.polyplus.client.lobby.LobbyPoses
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import kotlin.math.roundToInt

/** Right-click on your own character in the lobby: a small menu (hide username, edit animation) and the animation panel. */
internal object LobbyPlayerMenu {
    /** Where the small menu sits, in pixels of the lobby, or null when it is closed. */
    var at by mutableStateOf<Offset?>(null)
    var posesOpen by mutableStateOf(false)
    var hideName by mutableStateOf(LobbySceneStore.hideName())
    var pose by mutableStateOf(LobbySceneStore.pose())

    fun open(x: Float, y: Float) {
        hideName = LobbySceneStore.hideName()
        pose = LobbySceneStore.pose()
        at = Offset(x, y)
    }

    fun close() {
        at = null
    }

    /** Opens the animation panel and closes every other side panel, so only one shows at a time. */
    fun openPoses() {
        LobbyElementPanel.close()
        LobbyHudMenu.close()
        posesOpen = true
    }

    fun closePoses() {
        posesOpen = false
        LobbyPoses.preview = null
    }

    fun toggleName() {
        hideName = !hideName
        LobbySceneStore.setHideName(hideName)
    }

    fun pick(id: String) {
        pose = id
        LobbySceneStore.setPose(id)
    }
}

internal val POPUP_MENU_WIDTH = 190.dp
private val MENU_ROW = 34.dp

@Composable
internal fun BoxScope.LobbyPlayerMenuHost() {
    val at = LobbyPlayerMenu.at
    if (at != null) {
        // a click anywhere else closes the small menu
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            event.changes.forEach { it.consume() }
                            LobbyPlayerMenu.close()
                        }
                    }
                }
            },
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) {
                        val x = at.x.roundToInt().coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                        val y = at.y.roundToInt().coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                        placeable.place(x, y)
                    }
                }
                .width(POPUP_MENU_WIDTH)
                .panel(12.dp)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(if (LobbyPlayerMenu.hideName) "Show username" else "Hide username") {
                LobbyPlayerMenu.toggleName()
                LobbyPlayerMenu.close()
            }
            MenuRow("Edit animation", LobbyPoses.name(LobbyPlayerMenu.pose)) {
                LobbyPlayerMenu.close()
                LobbyPlayerMenu.openPoses()
            }
        }
    }
}

/** The "Your character" panel; composed after the Play card so it draws on top of it. */
@Composable
internal fun LobbyPosePanelHost() {
    PosePanel()
}

@Composable
internal fun MenuRow(label: String, value: String? = null, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(MENU_ROW)
            .selectableRow(false, onClick, label, OneConfigTheme.shape(8.dp), true, role = A11yRole.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (value != null) Text(value, color = theme.textColorSecondary, style = theme.typography.small, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PosePanel() {
    val open = LobbyPlayerMenu.posesOpen
    DisposableEffect(Unit) { onDispose { LobbyPoses.preview = null } }
    DockedPanel(open, "Your character", LobbyPlayerMenu::closePoses) {
        PanelPageColumn {
            PanelSection(null, divider = false) {
                PanelToggle("Hide username", LobbyPlayerMenu.hideName, { LobbyPlayerMenu.toggleName() })
            }
            PanelSection("Animation") {
                PanelHint("Hover one to preview it on your character, click to keep it.")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LobbyPoses.all.forEach { pose -> PoseRow(pose) }
                }
            }
        }
    }
}

@Composable
private fun PoseRow(pose: LobbyPoses.Pose) {
    val theme = OneConfigTheme.current
    val on = LobbyPlayerMenu.pose == pose.id
    Row(
        Modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight - 6.dp)
            .pointerInput(pose.id) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> LobbyPoses.preview = pose.id
                            PointerEventType.Exit -> if (LobbyPoses.preview == pose.id) LobbyPoses.preview = null
                            else -> Unit
                        }
                    }
                }
            }
            .selectableRow(on, { LobbyPlayerMenu.pick(pose.id) }, pose.name, OneConfigTheme.shape(CustomizeKit.RowRadius), true, ItemSelectionStyle.Accent, A11yRole.RadioButton)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pose.name,
            Modifier.weight(1f),
            color = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent),
            style = theme.typography.body.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
