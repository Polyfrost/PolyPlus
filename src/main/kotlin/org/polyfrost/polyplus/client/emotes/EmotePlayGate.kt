package org.polyfrost.polyplus.client.emotes

internal class EmotePlayGate {
    private var generation = 0L

    fun ticket(): Long = synchronized(this) { generation }

    fun open(ticket: Long?): Boolean = synchronized(this) { ticket == null || ticket == generation }

    fun <T> cancel(then: () -> T): T = synchronized(this) {
        generation++
        then()
    }

    fun <T : Any> send(ticket: Long?, action: () -> T): T? = synchronized(this) {
        if (ticket != null && ticket != generation) null else action()
    }
}
