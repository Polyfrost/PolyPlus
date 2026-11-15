@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.Text

@Composable
internal fun WardrobeHeader(
    sections: List<WardrobeSection>,
    selected: WardrobeSection,
    anchors: List<Pair<String, Int>>,
    activeAnchor: Int?,
    sort: WardrobeSort,
    cartSize: Int,
    showingHistory: Boolean,
    storeEnabled: Boolean,
    onSelectSection: (WardrobeSection) -> Unit,
    onAnchor: (Int) -> Unit,
    onCycleSort: () -> Unit,
    onToggleHistory: () -> Unit,
    onRefresh: () -> Unit,
    onCart: () -> Unit,
) {
    val theme = OneConfigTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(33.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sectionScroll = rememberScrollState()
            val tabs = sections.map { TabItem<WardrobeSection?>(it, it.label, it.icon) }
            ScrollContainer(sectionScroll, Modifier.weight(1f), orientation = Orientation.Horizontal, scrollbar = false) {
                TabRow(
                    tabs = tabs,
                    selected = if (showingHistory) null else selected,
                    onSelect = { section -> if (section != null) onSelectSection(section) },
                    style = TabStyle.Pill,
                    fill = false,
                    sound = null,
                )
            }
            if (storeEnabled) {
                TextButton("Cart ($cartSize)", "assets/polyplus/ico/shopping-cart/0.svg", onCart, style = ControlStyle.Primary, sound = null)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(33.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showingHistory) {
                Text("Purchase history", modifier = Modifier.weight(1f), color = theme.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
            } else {
                val anchorScroll = rememberScrollState()
                ScrollContainer(anchorScroll, Modifier.weight(1f), orientation = Orientation.Horizontal, scrollbar = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (anchors.size > 1) {
                            for ((label, index) in anchors) {
                                AnchorChip(label, active = index == activeAnchor, onClick = { onAnchor(index) })
                            }
                        }
                    }
                }
            }
            if (storeEnabled && !showingHistory) {
                TextButton(sort.label, "align", onCycleSort, sound = null)
            }
            if (storeEnabled) {
                val historyLabel = if (showingHistory) "Back to the wardrobe" else "Purchase history"
                IconButton(
                    icon = if (showingHistory) "left-arrow" else "clipboard",
                    contentDescription = historyLabel,
                    onClick = onToggleHistory,
                    style = IconButtonStyle.Framed,
                    tooltipPlacement = TooltipPlacement.AboveStart,
                    sound = null,
                )
            }
            IconButton(
                icon = "refresh",
                contentDescription = "Refresh",
                onClick = onRefresh,
                style = IconButtonStyle.Framed,
                tooltipPlacement = TooltipPlacement.AboveStart,
                sound = null,
            )
        }
    }
}

@Composable
private fun AnchorChip(label: String, active: Boolean, onClick: () -> Unit) {
    val hover = rememberHoverState()
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(6.dp)
    Box(
        modifier = Modifier.height(26.dp)
            .clip(shape)
            .background(if (active) theme.chipBackground else Color.Transparent)
            .hoverable(hover.interactionSource)
            .oneConfigClickable(label, onClick, selected = active, shape = shape, indication = false, sound = null, interactionSource = hover.interactionSource)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active || hover.hovered) theme.textColor else theme.textColorSecondary,
            style = theme.typography.body.copy(fontSize = 12.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1,
        )
    }
}

@Composable
internal fun PreviewModeToggle(wheelMode: Boolean, onChange: (Boolean) -> Unit) {
    TabRow(
        tabs = listOf(
            TabItem(false, "Character", "profiles"),
            TabItem(true, "Emote wheel", "dots-grid"),
        ),
        selected = wheelMode,
        onSelect = onChange,
        style = TabStyle.Pill,
        fill = false,
        sound = null,
    )
}
