@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxItem
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead
import org.polyfrost.polyplus.client.gui.lobbysocial.menuItems
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

private val InviteRowHeight = 52.dp
private val RoomyRowHeight = 60.dp
private val MaxListHeight = InviteRowHeight * 5
private val PopoverWidth = 320.dp
private val BellReach = 42.dp
private val BellDrop = 44.dp

@Composable
internal fun InvitesBell(items: List<InboxItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val offset = with(LocalDensity.current) { IntOffset(BellReach.roundToPx(), BellDrop.roundToPx()) }
    Box {
        IconButton(
            icon = SOCIAL_ASSETS + "bell-01.svg",
            contentDescription = "Invites",
            onClick = { open = !open },
            modifier = Modifier.size(34.dp),
            tint = OneConfigTheme.accent,
            tooltipPlacement = TooltipPlacement.BelowStart,
        )
        Badge(items.size.toString(), Modifier.align(Alignment.TopEnd), BadgeTone.Accent)
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = offset,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val scroll = rememberScrollState()
                ScrollContainer(scroll, Modifier.width(PopoverWidth).popup().heightIn(max = MaxListHeight), scrollbar = false) {
                    Column(Modifier.fillMaxWidth()) {
                        items.forEachIndexed { index, item -> InviteRow(item, index > 0) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InviteRow(item: InboxItem, divided: Boolean, roomy: Boolean = false) {
    if (divided) Divider()
    Row(
        Modifier.fillMaxWidth().height(if (roomy) RoomyRowHeight else InviteRowHeight).contextMenu(entries = item::menuItems).padding(start = if (roomy) 16.dp else 12.dp, end = if (roomy) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (roomy) 12.dp else 8.dp),
    ) {
        PersonHead(item.person, if (roomy) 38.dp else 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(item.brief, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        item.verb?.let { verb -> TextButton(text = verb, onClick = item.accept, modifier = Modifier.height(34.dp), style = ControlStyle.Primary) }
        IconButton(
            icon = LobbyIcons.CLOSE,
            contentDescription = item.dismissLabel,
            onClick = item.dismiss,
            modifier = Modifier.size(34.dp),
            tint = OneConfigTheme.current.textColor,
            tooltipPlacement = TooltipPlacement.BelowStart,
        )
    }
}
