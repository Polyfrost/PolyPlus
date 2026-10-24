@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape

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
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (section in sections) {
                    SectionChip(section, selected = section == selected && !showingHistory, onClick = { onSelectSection(section) })
                }
            }
            if (storeEnabled) {
                SmallButton("Cart ($cartSize)", iconPath = "assets/polyplus/ico/shopping-cart/0.svg", primary = true, onClick = onCart)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(33.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showingHistory) {
                SocialText("Purchase history", color = theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            } else {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (anchors.size > 1) {
                        for ((label, index) in anchors) {
                            AnchorChip(label, active = index == activeAnchor, onClick = { onAnchor(index) })
                        }
                    }
                }
            }
            if (storeEnabled && !showingHistory) {
                SmallButton(sort.label, iconPath = "align", primary = false, onClick = onCycleSort)
            }
            if (storeEnabled) {
                HeaderIconButton(
                    iconPath = if (showingHistory) "left-arrow" else "clipboard",
                    tooltip = if (showingHistory) "Back to the wardrobe" else "Purchase history",
                    onClick = onToggleHistory,
                )
            }
            HeaderIconButton(iconPath = "refresh", tooltip = "Refresh", onClick = onRefresh)
        }
    }
}

@Composable
private fun SectionChip(section: WardrobeSection, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    val color = if (selected) theme.accentTextColor else theme.textColor
    Row(
        modifier = Modifier.height(33.dp)
            .clip(ppShape(8.dp))
            .background(if (selected) OneConfigTheme.accent else theme.chipBackground)
            .border(1.dp, if (hovered && !selected) theme.textColorSecondary else theme.borderColor, ppShape(8.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon, color = color, modifier = Modifier.size(14.dp))
        SocialText(section.label, color = color, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun AnchorChip(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    Box(
        modifier = Modifier.height(26.dp)
            .clip(ppShape(6.dp))
            .background(if (active) theme.chipBackground else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(
            label,
            color = if (active || hovered) theme.textColor else theme.textColorSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderIconButton(iconPath: String, tooltip: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    Box(
        modifier = Modifier.size(33.dp)
            .clip(ppShape(8.dp))
            .background(theme.chipBackground)
            .border(1.dp, if (hovered) theme.textColorSecondary else theme.borderColor, ppShape(8.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconPath, color = theme.textColor, modifier = Modifier.size(14.dp))
        if (hovered) TooltipPopup(tooltip)
    }
}

@Composable
internal fun PreviewModeToggle(wheelMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewModeOption("Character", "profiles", selected = !wheelMode, modifier = Modifier.weight(1f), onClick = { onChange(false) })
        PreviewModeOption("Emote wheel", "dots-grid", selected = wheelMode, modifier = Modifier.weight(1f), onClick = { onChange(true) })
    }
}

@Composable
internal fun PreviewModeOption(label: String, iconPath: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    val color = if (selected) theme.accentTextColor else theme.textColor
    Row(
        modifier = modifier.fillMaxHeight()
            .clip(ppShape(8.dp))
            .background(if (selected) OneConfigTheme.accent else theme.chipBackground)
            .border(1.dp, if (hovered && !selected) theme.textColorSecondary else theme.borderColor, ppShape(8.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPath != null) Icon(iconPath, color = color, modifier = Modifier.size(14.dp))
        SocialText(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
