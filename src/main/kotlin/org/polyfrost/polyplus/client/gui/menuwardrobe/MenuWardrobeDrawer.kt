@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.DrillRow
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSection
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

private const val PAGE_MILLIS = 180
private val PAGE_SHIFT = 24.dp
private val HEADER_HEIGHT = 56.dp
private val PANEL_RADIUS = 20.dp
private val WEARING_HEIGHT = 40.dp

internal sealed interface DrawerPage {
    val title: String

    data object Home : DrawerPage {
        override val title = "Wardrobe"
    }

    data class Slot(val type: CosmeticType) : DrawerPage {
        override val title get() = type.displayName
    }

    data class Library(val view: WardrobeView) : DrawerPage {
        override val title get() = view.title
    }
}

internal class DrawerState {
    private val stack = mutableStateListOf<DrawerPage>(DrawerPage.Home)
    val page: DrawerPage get() = stack.last()
    var forward by mutableStateOf(true)
        private set

    val slotType: CosmeticType? get() = (page as? DrawerPage.Slot)?.type
    val view: WardrobeView? get() = (page as? DrawerPage.Library)?.view

    fun open(next: DrawerPage) {
        if (next == page) return
        forward = next != DrawerPage.Home
        stack.removeRange(1, stack.size)
        if (next != DrawerPage.Home) stack += next
    }

    fun back() {
        if (stack.size <= 1) return
        forward = false
        stack.removeAt(stack.lastIndex)
    }
}

internal class SlotWearing(val name: String?, val onUnequip: (() -> Unit)?)

@Composable
internal fun WardrobeDrawer(state: DrawerState, modifier: Modifier, content: @Composable (DrawerPage) -> Unit) {
    val page = state.page
    if (page != DrawerPage.Home) EscapeCloses(state::back)
    Column(
        modifier
            .panel(PANEL_RADIUS)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        DrawerHeader(state)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(page) {
                val enter = remember { Animatable(if (Motion.reduced) 1f else 0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) }
                val direction = if (state.forward) 1f else -1f
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = enter.value
                        translationX = (1f - enter.value) * direction * PAGE_SHIFT.toPx()
                    },
                ) { content(page) }
            }
        }
    }
}

@Composable
private fun DrawerHeader(state: DrawerState) {
    val sub = state.page != DrawerPage.Home
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = if (sub) 8.dp else CustomizeKit.Inset, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (sub) IconButton(SOCIAL_ASSETS + "chevron-left.svg", "Back", state::back, tooltip = null)
        Text(
            state.page.title,
            Modifier.weight(1f),
            color = OneConfigTheme.current.textColor,
            style = OneConfigTheme.current.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
internal fun DrawerHomePage(
    state: DrawerState,
    top: @Composable () -> Unit,
    bottom: (@Composable () -> Unit)?,
    libraries: Boolean,
) {
    PanelPageColumn {
        top()
        if (libraries) {
            PanelSection(null, divider = false) {
                for (view in WardrobeView.entries) {
                    DrillRow(view.icon, view.label, null, { state.open(DrawerPage.Library(view)) })
                }
            }
        }
        if (bottom != null) PanelSection(null) { bottom() }
    }
}

@Composable
internal fun DrawerSlotPage(model: PickerModel, wearing: SlotWearing) {
    val theme = OneConfigTheme.current
    Column(Modifier.fillMaxSize().padding(start = CustomizeKit.Inset, end = CustomizeKit.Inset, bottom = CustomizeKit.Inset)) {
        Row(
            Modifier.fillMaxWidth().height(WEARING_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                wearing.name?.let { "Wearing: $it" } ?: "Nothing equipped",
                Modifier.weight(1f),
                color = theme.textColorSecondary,
                style = theme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            wearing.onUnequip?.let { TextButton(text = "Unequip", onClick = it, size = ControlSize.Compact) }
        }
        SlotPicker(model, Modifier.fillMaxWidth().weight(1f))
    }
}
