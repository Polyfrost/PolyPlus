package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun WardrobeRail(
    sections: List<WardrobeSection>,
    selected: WardrobeSection,
    anchors: List<Pair<String, Int>>,
    modifier: Modifier,
    onSelect: (WardrobeSection) -> Unit,
    onAnchor: (Int) -> Unit,
) {
    Column(
        modifier = modifier.wardrobePanel()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (section in sections) {
            RailEntry(section, active = section == selected, onClick = { onSelect(section) })
            if (section == selected && anchors.size > 1) {
                Column(
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for ((label, index) in anchors) {
                        RailAnchor(label, onClick = { onAnchor(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RailEntry(section: WardrobeSection, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = LocalTheme.current
    val color = if (active) theme.accentTextColor else theme.textColor
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp)
            .clip(ppShape(8.dp))
            .background(
                when {
                    active -> Accent
                    hovered -> theme.chipBackground
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(section.icon, color = color, modifier = Modifier.size(16.dp))
        SocialText(section.label, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun RailAnchor(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = LocalTheme.current
    SocialText(
        label,
        color = if (hovered) theme.textColor else theme.textColorSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    )
}
