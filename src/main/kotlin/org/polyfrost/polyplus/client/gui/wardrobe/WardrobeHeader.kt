package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun WardrobeHeader(
    title: String,
    sort: WardrobeSort,
    cartSize: Int,
    showingHistory: Boolean,
    storeEnabled: Boolean,
    onCycleSort: () -> Unit,
    onToggleHistory: () -> Unit,
    onRefresh: () -> Unit,
    onCart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SocialText(title, color = LocalTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.weight(1f))
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
        if (storeEnabled) {
            SmallButton("Cart ($cartSize)", iconPath = "assets/polyplus/ico/shopping-cart/0.svg", primary = true, onClick = onCart)
        }
    }
}

@Composable
private fun HeaderIconButton(iconPath: String, tooltip: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = LocalTheme.current
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
