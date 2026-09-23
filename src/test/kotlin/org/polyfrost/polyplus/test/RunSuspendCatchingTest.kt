package org.polyfrost.polyplus.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.utils.runSuspendCatching

class RunSuspendCatchingTest {
    @Test
    fun `the caller's own cancellation is rethrown`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var resumedAfterCatch = false
        val job = launch {
            runSuspendCatching {
                started.complete(Unit)
                awaitCancellation()
            }
            resumedAfterCatch = true
        }
        started.await()
        job.cancel()
        job.join()
        assertFalse(resumedAfterCatch)
    }

    @Test
    fun `a nested timeout is captured as a failure`(): Unit = runBlocking {
        val result = runSuspendCatching { withTimeout(1) { awaitCancellation() } }
        assertInstanceOf(TimeoutCancellationException::class.java, result.exceptionOrNull())
    }
}
