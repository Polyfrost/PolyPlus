@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.polyplus.client.gui.formatCooldown
import org.polyfrost.polyplus.client.gui.rememberCooldownRemaining
import org.polyfrost.polyplus.client.social.SpecialChatRepository

internal const val CHAT_LINES = 5
private const val CHIP_TITLE_LENGTH = 12

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
internal fun ConversationChip(conversation: ChatConversation) {
    val title = conversation.title
    val label = if (title.length > CHIP_TITLE_LENGTH) title.take(CHIP_TITLE_LENGTH).trimEnd() + "..." else title
    Box(Modifier) { Chip(label, selected = true) { } }
}
