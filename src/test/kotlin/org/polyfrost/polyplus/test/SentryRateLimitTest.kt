package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.SentryEventRateLimiter
import org.polyfrost.polyplus.libs.sentry.SentryEvent
import org.polyfrost.polyplus.libs.sentry.SentryLevel
import org.polyfrost.polyplus.libs.sentry.exception.ExceptionMechanismException
import org.polyfrost.polyplus.libs.sentry.protocol.Mechanism
import java.io.File

class SentryRateLimitTest {

    private companion object {
        const val WINDOW_MS = 60L * 60 * 1000
    }

    private var now = 1_700_000_000_000L

    private fun limiter(dir: File) = SentryEventRateLimiter(File(dir, "recent-events.txt"), WINDOW_MS) { now }

    private fun sameErrorTwice(): Pair<Throwable, Throwable> =
        List(2) { IllegalStateException("boom") }.let { it[0] to it[1] }

    private fun event(throwable: Throwable, level: SentryLevel): SentryEvent =
        SentryEvent(throwable).apply { this.level = level }

    @Test
    fun `a repeat within the window is dropped`(@TempDir dir: File) {
        val limiter = limiter(dir)
        assertTrue(limiter.allow("crash"))
        assertFalse(limiter.allow("crash"))

        now += WINDOW_MS / 2
        assertFalse(limiter.allow("crash"))
        assertTrue(limiter.allow("a different crash"))
    }

    @Test
    fun `the error is reportable again once the window passes`(@TempDir dir: File) {
        val limiter = limiter(dir)
        assertTrue(limiter.allow("crash"))

        now += WINDOW_MS + 1
        assertTrue(limiter.allow("crash"))
        assertFalse(limiter.allow("crash"))
    }

    @Test
    fun `the window outlives the process, which is the point of it`(@TempDir dir: File) {
        assertTrue(limiter(dir).allow("crash"))
        assertFalse(limiter(dir).allow("crash"))

        now += WINDOW_MS + 1
        assertTrue(limiter(dir).allow("crash"))
    }

    @Test
    fun `a clock moved backwards does not silence reports`(@TempDir dir: File) {
        assertTrue(limiter(dir).allow("crash"))

        now -= 7L * 24 * 60 * 60 * 1000
        assertTrue(limiter(dir).allow("crash"))
    }

    @Test
    fun `the same error at the same severity shares a key`() {
        val (first, second) = sameErrorTwice()
        assertEquals(
            PolyPlusSentry.rateLimitKey(event(first, SentryLevel.FATAL)),
            PolyPlusSentry.rateLimitKey(event(second, SentryLevel.FATAL)),
        )
    }

    @Test
    fun `severity keeps otherwise identical errors apart`() {
        val (first, second) = sameErrorTwice()
        assertNotEquals(
            PolyPlusSentry.rateLimitKey(event(first, SentryLevel.FATAL)),
            PolyPlusSentry.rateLimitKey(event(second, SentryLevel.WARNING)),
        )
    }

    @Test
    fun `unrelated errors keep their own keys`() {
        assertNotEquals(
            PolyPlusSentry.rateLimitKey(event(IllegalStateException("boom"), SentryLevel.FATAL)),
            PolyPlusSentry.rateLimitKey(event(IllegalStateException("boom"), SentryLevel.FATAL)),
        )
    }

    @Test
    fun `the mechanism wrapper the live path adds is seen through`() {
        val (first, second) = sameErrorTwice()
        val wrapped = ExceptionMechanismException(Mechanism(), second, Thread.currentThread())
        assertEquals(
            PolyPlusSentry.rateLimitKey(event(first, SentryLevel.FATAL)),
            PolyPlusSentry.rateLimitKey(event(wrapped, SentryLevel.FATAL)),
        )
    }

    /** The postmortem uploader has no throwable, only the fingerprint it derives from the file. */
    private fun postmortem(topFrame: String): SentryEvent = SentryEvent().apply {
        level = SentryLevel.FATAL
        fingerprints = listOf("mc-crash-report", "java.lang.NullPointerException", topFrame)
    }

    @Test
    fun `throwable-less events fall back to their fingerprint`() {
        assertEquals(
            PolyPlusSentry.rateLimitKey(postmortem("org.example.Mod.render")),
            PolyPlusSentry.rateLimitKey(postmortem("org.example.Mod.render")),
        )
        assertNotEquals(
            PolyPlusSentry.rateLimitKey(postmortem("org.example.Mod.render")),
            PolyPlusSentry.rateLimitKey(postmortem("org.example.Mod.tick")),
        )
    }
}
