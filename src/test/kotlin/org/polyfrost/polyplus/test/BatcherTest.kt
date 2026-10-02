package org.polyfrost.polyplus.test

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.utils.Batcher
import java.time.Duration

class BatcherTest {
    @Test
    fun `a failed batch does not stop later flushes`() = runBlocking {
        val batches = Channel<Set<String>>(Channel.UNLIMITED)
        var failNext = true
        val batcher = Batcher(Duration.ofMillis(10)) { batch ->
            batches.send(batch)
            if (failNext) {
                failNext = false
                throw IllegalStateException("simulated batch failure")
            }
        }

        batcher.add("a")
        assertEquals(setOf("a"), withTimeout(5_000) { batches.receive() })

        batcher.add("b")
        assertEquals(setOf("b"), withTimeout(5_000) { batches.receive() })
    }
}
