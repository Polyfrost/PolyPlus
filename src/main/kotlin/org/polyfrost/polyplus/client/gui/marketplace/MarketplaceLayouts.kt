package org.polyfrost.polyplus.client.gui.marketplace

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.leftColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.rightColumn
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeEntry

private const val SECTION_LINE_DIVISOR = 3

private class RailJump(val section: Int, val firstItem: Int, val firstOffset: Int)

private fun LazyGridState.sectionOnScreen(anchors: List<Int>): Int? {
    val info = layoutInfo
    val line = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / SECTION_LINE_DIVISOR
    val reached = info.visibleItemsInfo.filter { it.offset.y <= line }.maxOfOrNull { it.index } ?: firstVisibleItemIndex
    return anchors.lastOrNull { it <= reached } ?: anchors.firstOrNull()
}

@Composable
internal fun BoxScope.MarketplaceLayout(state: MarketplaceState, onBack: () -> Unit, onWardrobe: () -> Unit) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val entries = state.entries
    val anchors = remember(entries) {
        entries.mapIndexedNotNull { index, entry -> (entry as? WardrobeEntry.Divider)?.takeIf { it.anchor }?.let { it.label to index } }
    }
    var jump by remember { mutableStateOf<RailJump?>(null) }
    val active by remember(anchors) {
        derivedStateOf {
            jump?.takeIf { it.firstItem == grid.firstVisibleItemIndex && it.firstOffset == grid.firstVisibleItemScrollOffset }?.section
                ?: grid.sectionOnScreen(anchors.map { it.second })
        }
    }
    val rail = anchors.mapIndexed { position, (label, index) ->
        val next = anchors.getOrNull(position + 1)?.second ?: entries.size
        RailEntry(label, next - index - 1, index == active) {
            scope.launch {
                grid.scrollToItem(index)
                withFrameNanos { }
                jump = RailJump(index, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
            }
        }
    }
    PreviewColumn(state, onWardrobe, leftColumn())
    MarketBody(state, grid, entries, onBack, centerColumn())
    CategoryRail(rail, rightColumn())
}
