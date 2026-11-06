@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import org.polyfrost.polyplus.client.gui.EmojiPickerButton
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialEmojiText
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.lobbysocial.ChatLine
import org.polyfrost.polyplus.client.gui.lobbysocial.ConversationChip
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.cooldownReason
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberPartyConversation
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val MESSAGE_LIMIT = 500

private val BarPadding = 12.dp
private val BarGap = 12.dp
private val BarEntryWidth = 124.dp
private val BarHeadSize = 30.dp
private val ChatNameWidth = 120.dp
private val DockCloseSize = 28.dp

@Composable
internal fun PlayerBar(lobby: LobbyShell, modifier: Modifier) {
    val party by PartyRepository.party.collectAsState()
    Row(
        modifier.height(LobbyLayout.BarHeight).panel().padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val current = party
            if (current != null) PartyRoster(lobby, current, maxWidth) else SoloRoster()
        }
        PartyControls(lobby, party)
    }
}

@Composable
private fun SoloRoster() {
    BarEntry(ClientPlatform.localPlayerName(), "You") { Head(ClientPlatform.localPlayerUuid(), BarHeadSize, realSkin = true) }
}

@Composable
private fun BarEntry(name: String, detail: String, head: @Composable () -> Unit) {
    Row(
        Modifier.widthIn(max = BarEntryWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        head()
        Column(lobbyHover(name), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(name, 14.sp, weight = FontWeight.Medium)
            Label(detail, 11.sp, color = MutedText)
        }
    }
}

@Composable
internal fun ChatPanel(open: Boolean) {
    var draft by remember { mutableStateOf("") }
    val party by PartyRepository.party.collectAsState()
    val partyChat = party?.chatGroupId?.let { rememberPartyConversation(it) }
    val active = SocialChatLink.active ?: partyChat
    val conversation = active.takeIf { open }
    val height by animateDpAsState(if (conversation != null) LobbyLayout.ChatHeight else LobbyLayout.BarHeight)
    LaunchedEffect(active?.id) { draft = "" }
    val lines = conversation?.lines().orEmpty()
    Column(Modifier.width(LobbyLayout.ChatWidth).height(height).panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversation == null) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedIcon("text", MutedText, 16.dp)
                Label("Press Enter to open Social", 12.sp, color = MutedText)
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConversationChip(conversation)
            if (conversation !== partyChat) {
                Spacer(Modifier.weight(1f))
                DockCloseButton()
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
            if (lines.isEmpty()) Label("No messages yet", 12.sp, color = MutedText)
            lines.forEach { ChatRow(it) }
        }
        val cooldown = cooldownReason(conversation.id)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialTextField(
                value = draft,
                onValueChange = { if (cooldown == null) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = cooldown ?: "Message ${conversation.title}",
                maxLength = MESSAGE_LIMIT,
                onSubmit = {
                    val text = draft.trim()
                    if (cooldown == null && text.isNotEmpty()) {
                        conversation.send(EmojiRegistry.toShortcodes(text))
                        draft = ""
                    }
                },
            )
            EmojiPickerButton { alias ->
                val insert = ":$alias:"
                if (cooldown == null && draft.length + insert.length <= MESSAGE_LIMIT) draft += insert
            }
        }
    }
}

@Composable
private fun DockCloseButton() {
    val (interaction, hovered) = rememberSocialHover()
    SocialIconButton(
        LobbyIcons.CLOSE,
        Modifier.size(DockCloseSize).hoverable(interaction),
        background = SocialControlBackground,
        tint = if (hovered) SocialDangerText else SocialTextPrimary,
        tooltip = "Close conversation",
        onClick = SocialChatLink::close,
    )
}

@Composable
private fun ChatRow(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(line.name, 14.sp, Modifier.widthIn(max = ChatNameWidth), weight = FontWeight.Medium)
            Label(":", 14.sp, color = MutedText, weight = FontWeight.Medium)
        }
        SocialEmojiText(line.text, 14.sp, Modifier.weight(1f), maxLines = 1)
    }
}
