@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.GridReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.rememberGridReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved

internal class ServerReorder(val entries: List<ServerEntry>, val state: GridReorderState) {
    val overlayEntry: ServerEntry? get() = state.overlayKey?.let { key -> entries.firstOrNull { it.key == key } }
}

@Composable
internal fun rememberServerReorder(lobby: LobbyShell, shown: List<ServerEntry>, enabled: Boolean, lazyState: LazyGridState): ServerReorder =
    rememberServerOrder(lobby, shown, enabled) { onMove, onDragEnd, dropRange, onClick ->
        rememberGridReorderState(lazyState, onMove, onDragEnd, dropRange, onClick, maxScrollSpeed = 2400.dp, sound = null, keyboardReorder = false)
    }

@Composable
internal fun rememberServerReorder(lobby: LobbyShell, shown: List<ServerEntry>, enabled: Boolean, lazyState: LazyListState): ServerReorder =
    rememberServerOrder(lobby, shown, enabled) { onMove, onDragEnd, dropRange, onClick ->
        rememberGridReorderState(lazyState, onMove, onDragEnd, dropRange, onClick, axisLock = true, lift = false, maxScrollSpeed = 2400.dp, sound = null, keyboardReorder = false)
    }

@Composable
private fun rememberServerOrder(
    lobby: LobbyShell,
    shown: List<ServerEntry>,
    enabled: Boolean,
    create: @Composable (
        onMove: (Int, Int) -> Unit,
        onDragEnd: () -> Unit,
        dropRange: (Int) -> IntRange?,
        onClick: (Any) -> Unit,
    ) -> GridReorderState,
): ServerReorder {
    var dragged by remember { mutableStateOf<List<ServerEntry>?>(null) }
    val draggedKey = remember { arrayOfNulls<Any>(1) }
    val entries = dragged?.takeIf { enabled && it.size == shown.size } ?: shown
    val currentEntries by rememberUpdatedState(entries)
    val state = create(
        { from, to ->
            if (draggedKey[0] == null) draggedKey[0] = currentEntries.getOrNull(from)?.key
            currentEntries.withItemMoved(from, to)?.let { dragged = it }
        },
        {
            val moved = dragged
            val key = draggedKey[0]
            dragged = null
            draggedKey[0] = null
            val index = moved?.indexOfFirst { it.key == key } ?: -1
            if (moved != null && index >= 0) lobby.servers.moveTo(moved[index], moved.subList(0, index).count { it.kind == ServerKind.SAVED })
        },
        { index ->
            val first = currentEntries.indexOfFirst { it.kind == ServerKind.SAVED }
            val last = currentEntries.indexOfLast { it.kind == ServerKind.SAVED }
            if (currentEntries.getOrNull(index)?.kind == ServerKind.SAVED) first..last else IntRange.EMPTY
        },
        { key -> currentEntries.firstOrNull { it.key == key }?.let { lobby.select(it.data) } },
    )
    LaunchedEffect(state, enabled) {
        if (!enabled) {
            state.cancel()
            dragged = null
            draggedKey[0] = null
        }
    }
    return ServerReorder(entries, state)
}
