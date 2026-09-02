package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupLastMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.social.SpecialChatResponses

class SpecialChatResponsesTest {
    private fun specialChat(id: Int, player: String, lastSender: String?) = GroupSummary(
        id = id,
        kind = GroupKind.Group,
        members = listOf(ME, LYNITH, player),
        lastMessage = lastSender?.let { GroupLastMessage(content = "hi", sender = it, sentAt = "") },
        special = true,
    )

    @Test
    fun `staff are the members every special chat has in common`() {
        val chats = listOf(specialChat(1, "a", ME), specialChat(2, "b", ME))

        assertEquals(setOf(ME, LYNITH), SpecialChatResponses.targets(chats, ME))
    }

    @Test
    fun `a chat whose last message is theirs needs no history fetch`() {
        val chats = listOf(
            specialChat(1, "a", "a"),
            specialChat(2, "b", ME),
            specialChat(3, "c", LYNITH),
            specialChat(4, "d", null),
        )

        assertEquals(setOf(1), SpecialChatResponses.respondedFromSummaries(chats, setOf(ME, LYNITH)))
    }

    @Test
    fun `a single special chat falls back to only ourselves being staff`() {
        val chats = listOf(specialChat(1, "a", LYNITH))

        assertEquals(setOf(ME), SpecialChatResponses.targets(chats, ME))
    }

    private companion object {
        const val ME = "me"
        const val LYNITH = "lynith"
    }
}
