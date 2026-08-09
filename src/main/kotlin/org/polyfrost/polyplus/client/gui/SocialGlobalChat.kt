package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.network.http.responses.GlobalChatMessage
import org.polyfrost.polyplus.client.social.GlobalChatRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun GlobalChatView(selfId: String) {
    val messages by GlobalChatRepository.messages.collectAsState()

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) GlobalChatRepository.refreshHistory()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialText("Global Chat", fontSize = 16.sp, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
        GlobalChatTimeline(Modifier.weight(1f), messages, selfId)
        MessageComposer(placeholder = "Message everyone...") { content -> GlobalChatRepository.send(content) }
    }
}

@Composable
private fun GlobalChatTimeline(modifier: Modifier, messages: List<GlobalChatMessage>, selfId: String) {
    if (messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SocialText("No messages yet. Say hello to everyone!", fontSize = 13.sp, color = SocialTextSecondary)
        }
        return
    }
    val scrollState = remember { ScrollState(0) }
    var contentHeight by remember { mutableStateOf(0) }
    LaunchedEffect(contentHeight) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .onSizeChanged { contentHeight = it.height },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        messages.forEachIndexed { index, message ->
            val groupedWithPrevious = index > 0 && messages[index - 1].sender == message.sender
            val groupedWithNext = index < messages.lastIndex && messages[index + 1].sender == message.sender
            GlobalChatMessageRow(message, outgoing = message.sender == selfId, showHeader = !groupedWithPrevious)
            Spacer(Modifier.height(if (groupedWithNext) 2.dp else 10.dp))
        }
    }
}

@Composable
private fun GlobalChatMessageRow(message: GlobalChatMessage, outgoing: Boolean, showHeader: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(32.dp)) {
            if (showHeader) SocialAvatar(message.sender, 32.dp)
        }
        Column(Modifier.weight(1f)) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialText(
                        PlayerNamesRepository.displayName(message.sender),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (outgoing) Accent else SocialTextPrimary,
                    )
                    SocialText(timeFor(message.sentAt), fontSize = 10.sp, color = SocialTextSecondary)
                }
            }
            SocialText(message.content, fontSize = 14.sp, color = SocialTextPrimary, modifier = Modifier.widthIn(max = 480.dp))
        }
    }
}
