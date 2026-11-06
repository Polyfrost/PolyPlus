@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxItem
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead

private val InviteRowHeight = 52.dp
private val RoomyRowHeight = 60.dp
private val MaxListHeight = InviteRowHeight * 5
private val PopoverWidth = 320.dp
private val BellReach = 42.dp
private val BellDrop = 44.dp
private val PillSize = 16.dp

@Composable
internal fun CountPill(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(min = PillSize)
            .height(PillSize)
            .clip(OneConfigTheme.current.checkBoxShape)
            .background(OneConfigTheme.accent)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(count.toString(), 10.sp, color = OneConfigTheme.current.accentTextColor, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
internal fun InvitesBell(items: List<InboxItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val offset = with(density) { IntOffset(BellReach.roundToPx(), BellDrop.roundToPx()) }
    Box {
        SocialIconButton(SOCIAL_ASSETS + "bell-01.svg", tint = OneConfigTheme.accent, tooltip = "Invites") { open = !open }
        CountPill(items.size, Modifier.align(Alignment.TopEnd))
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = offset,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val scroll = rememberScrollState()
                Column(Modifier.width(PopoverWidth).popup().heightIn(max = MaxListHeight).verticalScroll(scroll)) {
                    items.forEachIndexed { index, item -> InviteRow(item, index > 0) }
                }
            }
        }
    }
}

@Composable
internal fun InviteRow(item: InboxItem, divided: Boolean, roomy: Boolean = false) {
    if (divided) PaneRule()
    Row(
        Modifier.fillMaxWidth().height(if (roomy) RoomyRowHeight else InviteRowHeight).padding(start = if (roomy) 16.dp else 12.dp, end = if (roomy) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (roomy) 12.dp else 8.dp),
    ) {
        PersonHead(item.person, if (roomy) 38.dp else 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(item.title, 14.sp, weight = FontWeight.Medium)
            Label(item.brief, 12.sp, color = MutedText)
        }
        item.verb?.let { verb -> SocialButton(verb, modifier = Modifier.height(34.dp), filled = true, onClick = item.accept) }
        SocialIconButton(LobbyIcons.CLOSE, tooltip = item.dismissLabel, onClick = item.dismiss)
    }
}
