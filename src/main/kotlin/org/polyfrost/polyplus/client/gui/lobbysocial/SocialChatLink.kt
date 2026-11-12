@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialEmojiText
import org.polyfrost.polyplus.client.gui.formatCooldown
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.rememberCooldownRemaining
import org.polyfrost.polyplus.client.social.SpecialChatRepository

internal const val CHAT_LINES = 5
private const val CHIP_TITLE_LENGTH = 12
private val ChatNameWidth = 120.dp

internal class ChatLine(val name: String, val text: String)

internal interface ChatConversation {
    val id: Int
    val title: String

    @Composable
    fun lines(): List<ChatLine>

    fun send(text: String)
}

internal object SocialChatLink {
    var active by mutableStateOf<ChatConversation?>(null)
        private set

    fun open(conversation: ChatConversation) {
        active = conversation
    }

    fun close() {
        active = null
    }

}

@Composable
internal fun cooldownReason(conversationId: Int): String? {
    val status by SpecialChatRepository.status.collectAsState()
    val remaining = status?.takeIf { it.groupId == conversationId }?.cooldownUntil?.let { rememberCooldownRemaining(it) } ?: 0L
    return if (remaining > 0) "Next Special Chat message available in ${formatCooldown(remaining)}" else null
}

@Composable
internal fun ChatLines(lines: List<ChatLine>, modifier: Modifier) {
    Box(modifier.clipToBounds(), contentAlignment = Alignment.BottomStart) {
        if (lines.isEmpty()) {
            Label("No messages yet", 12.sp, color = MutedText)
            return@Box
        }
        Column(
            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { ChatLineRow(it) }
        }
    }
}

@Composable
private fun ChatLineRow(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Label(line.name, 14.sp, Modifier.widthIn(max = ChatNameWidth), weight = FontWeight.Medium)
            Label(":", 14.sp, color = MutedText, weight = FontWeight.Medium)
        }
        SocialEmojiText(line.text, 14.sp, Modifier.weight(1f))
    }
}

@Composable
internal fun ConversationChip(conversation: ChatConversation) {
    val title = conversation.title
    val label = if (title.length > CHIP_TITLE_LENGTH) title.take(CHIP_TITLE_LENGTH).trimEnd() + "..." else title
    Box(Modifier) { Chip(label, selected = true) { } }
}
