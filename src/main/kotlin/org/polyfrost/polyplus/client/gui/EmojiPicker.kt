@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.emoji.EmojiRecents
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import org.polyfrost.polyplus.client.gui.lobbyshell.popup

private const val PICKER_COLUMNS = 8
private const val SEARCH_MAX_LENGTH = 32
private val PickerWidth = 296.dp
private val PickerGridHeight = 232.dp

@Composable
internal fun EmojiPickerButton(enabled: Boolean = true, onPick: (String) -> Unit) {
    if (!EmojiRegistry.enabled() || EmojiAtlas.bitmap == null) return

    var open by remember { mutableStateOf(false) }
    val background by Motion.animateColorAsState(if (open) OneConfigTheme.accent.asSocialSelected else Color.Transparent)
    val buttonGlyph = remember { EmojiRegistry.resolve("smile") }
    val disabledAlpha = OneConfigTheme.current.disabledAlpha

    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }
                .clip(SocialFieldShape)
                .background(background)
                .oneConfigClickable("Emoji picker", { open = !open }, enabled = enabled, shape = SocialFieldShape, recipe = ControlDefaults.Media),
            contentAlignment = Alignment.Center,
        ) {
            if (buttonGlyph != null) {
                EmojiGlyph(buttonGlyph, Modifier.size(18.dp))
            } else {
                ThemedIcon(SOCIAL_ASSETS + "stars.svg", if (open) OneConfigTheme.accent else SocialTextSecondary, 16.dp)
            }
        }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                popupPositionProvider = rememberAnchoredPosition(Placement.AboveEnd),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                EmojiPickerPanel(onPick = onPick)
            }
        }
    }
}

@Composable
private fun EmojiPickerPanel(onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var picks by remember { mutableIntStateOf(0) }
    var hovered by remember { mutableStateOf<EmojiRegistry.EmojiEntry?>(null) }

    val recents = remember(picks) { EmojiRecents.entries() }
    val results = remember(query) {
        if (query.isBlank()) EmojiRegistry.catalog else EmojiRegistry.search(query)
    }
    val pick: (EmojiRegistry.EmojiEntry) -> Unit = { entry ->
        EmojiRecents.record(entry.alias)
        picks++
        onPick(entry.alias)
    }

    Column(
        modifier = Modifier
            .width(PickerWidth)
            .popup(SocialPanelShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it.take(SEARCH_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search emoji",
            clearable = false,
        )

        if (results.isEmpty() && recents.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(PickerGridHeight), contentAlignment = Alignment.Center) {
                Text("No emoji match \"$query\"", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
            }
        } else {
            val grid = rememberLazyGridState()
            ScrollContainer(grid, Modifier.fillMaxWidth().height(PickerGridHeight), scrollbar = false) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PICKER_COLUMNS),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (query.isBlank() && recents.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recently used") }
                        items(recents, key = { "recent-${it.glyph}" }) { entry ->
                            EmojiCell(entry, onHover = { hovered = it }, onClick = { pick(entry) })
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("All emoji") }
                    }
                    items(results, key = { it.glyph }) { entry ->
                        EmojiCell(entry, onHover = { hovered = it }, onClick = { pick(entry) })
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val preview = hovered
            if (preview != null) {
                EmojiGlyph(preview.glyph, Modifier.size(16.dp))
                Text(":${preview.alias}:", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
            } else {
                Text("Pick an emoji", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun EmojiCell(
    entry: EmojiRegistry.EmojiEntry,
    onHover: (EmojiRegistry.EmojiEntry?) -> Unit,
    onClick: () -> Unit,
) {
    val hover = rememberHoverState()
    LaunchedEffect(hover.hovered) { if (hover.hovered) onHover(entry) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(SocialFieldShape)
            .hoverable(hover.interactionSource)
            .oneConfigClickable(":${entry.alias}:", onClick, shape = SocialFieldShape, recipe = ControlDefaults.Media),
        contentAlignment = Alignment.Center,
    ) {
        EmojiGlyph(entry.glyph, Modifier.fillMaxSize().padding(4.dp))
    }
}
