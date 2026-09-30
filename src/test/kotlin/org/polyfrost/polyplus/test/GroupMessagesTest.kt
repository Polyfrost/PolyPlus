package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.social.GroupsRepository

class GroupMessagesTest {
    private fun message(id: Long, content: String = "hi") =
        GroupMessage(id = id, sender = "a", content = content, sentAt = "")

    private fun ids(messages: List<GroupMessage>) = messages.map { it.id }

    @Test
    fun `newer messages are appended in order`() {
        val result = GroupsRepository.withMessage(listOf(message(1), message(2)), message(3), limit = 10)

        assertEquals(listOf(1L, 2L, 3L), ids(result))
    }

    @Test
    fun `out of order messages are sorted in and existing ids replaced`() {
        val current = listOf(message(1), message(3, "old"), message(PENDING))

        val sorted = GroupsRepository.withMessage(current, message(2), limit = 10)
        val replaced = GroupsRepository.withMessage(current, message(3, "new"), limit = 10)

        assertEquals(listOf(1L, 2L, 3L, PENDING), ids(sorted))
        assertEquals(listOf(1L, 3L, PENDING), ids(replaced))
        assertEquals("new", replaced[1].content)
    }

    @Test
    fun `the oldest messages are dropped beyond the limit`() {
        val current = (1L..3L).map(::message) + message(PENDING)

        val result = GroupsRepository.withMessage(current, message(4), limit = 3)

        assertEquals(listOf(3L, 4L, PENDING), ids(result))
    }

    private companion object {
        const val PENDING = Long.MAX_VALUE - 5
    }
}
