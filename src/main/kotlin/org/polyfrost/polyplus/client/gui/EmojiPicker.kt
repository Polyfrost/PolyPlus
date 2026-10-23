package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.emoji.EmojiRecents
import org.polyfrost.polyplus.client.emoji.EmojiRegistry

private const val PICKER_COLUMNS = 8
private val PickerWidth = 296.dp
private val PickerGridHeight = 232.dp

@Composable
internal fun EmojiPickerButton(onPick: (String) -> Unit) {
    if (!EmojiRegistry.enabled() || EmojiAtlas.bitmap == null) return

    var open by remember { mutableStateOf(false) }
    val (interaction, hovered) = rememberSocialHover()
    val background by animateColorAsState(
        when {
            open -> Accent.asSocialSelected
            hovered -> SocialHoverOverlay
            else -> Color.Transparent
        },
    )
    val buttonGlyph = remember { EmojiRegistry.resolve("smile") }

    Box {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(SocialFieldShape)
                .background(background)
                .hoverable(interaction)
                .clickableWithSound { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            if (buttonGlyph != null) {
                EmojiGlyph(buttonGlyph, Modifier.size(18.dp))
            } else {
                Icon(SOCIAL_ASSETS + "stars.svg", if (open) Accent else SocialTextSecondary, Modifier.size(16.dp))
            }
        }
        if (open) {
            Popup(
                popupPositionProvider = remember { EmojiPickerPositionProvider },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                EmojiPickerPanel(onPick = onPick)
            }
        }
    }
}

private object EmojiPickerPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 6
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= 0) above else (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, y)
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
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SocialTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search emoji",
            leadingIcon = SOCIAL_ASSETS + "search.svg",
            maxLength = 32,
        )

        if (results.isEmpty() && recents.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(PickerGridHeight), contentAlignment = Alignment.Center) {
                SocialText("No emoji match \"$query\"", fontSize = 12.sp, color = SocialTextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(PICKER_COLUMNS),
                modifier = Modifier.fillMaxWidth().height(PickerGridHeight),
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (query.isBlank() && recents.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmojiSectionLabel("Recently used") }
                    items(recents, key = { "recent-${it.glyph}" }) { entry ->
                        EmojiCell(entry, onHover = { hovered = it }, onClick = { pick(entry) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { EmojiSectionLabel("All emoji") }
                }
                items(results, key = { it.glyph }) { entry ->
                    EmojiCell(entry, onHover = { hovered = it }, onClick = { pick(entry) })
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
                SocialText(":${preview.alias}:", fontSize = 12.sp, color = SocialTextSecondary, maxLines = 1)
            } else {
                SocialText("Pick an emoji", fontSize = 12.sp, color = SocialTextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmojiSectionLabel(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        SocialText(label, fontSize = 11.sp, color = SocialTextSecondary, maxLines = 1)
    }
}

@Composable
private fun EmojiCell(
    entry: EmojiRegistry.EmojiEntry,
    onHover: (EmojiRegistry.EmojiEntry?) -> Unit,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    LaunchedEffect(hovered) { if (hovered) onHover(entry) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(SocialFieldShape)
            .background(if (hovered) SocialHoverOverlay else Color.Transparent)
            .hoverable(interaction)
            .clickableWithSound(onClick),
        contentAlignment = Alignment.Center,
    ) {
        EmojiGlyph(entry.glyph, Modifier.fillMaxSize().padding(4.dp))
    }
}
