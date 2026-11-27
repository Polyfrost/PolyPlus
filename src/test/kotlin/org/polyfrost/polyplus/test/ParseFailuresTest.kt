package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.ParseFailures

class ParseFailuresTest {
    @Test
    fun `a variant's own failure leaves its siblings alone`() {
        val failures = ParseFailures()
        failures.record("bundle-a", "h1", "red", bundleWide = false)
        assertTrue(failures.failed("bundle-a", "h1", "red"))
        assertFalse(failures.failed("bundle-a", "h1", "blue"))
        assertFalse(failures.failed("bundle-a", "h1", null))
    }

    @Test
    fun `a default variant's own failure leaves named siblings alone`() {
        val failures = ParseFailures()
        failures.record("bundle-a", "h1", null, bundleWide = false)
        assertTrue(failures.failed("bundle-a", "h1", null))
        assertFalse(failures.failed("bundle-a", "h1", "red"))
        assertFalse(failures.failed("bundle-a", "h2", null))
        failures.forget("bundle-a")
        assertFalse(failures.failed("bundle-a", "h1", null))
    }

    @Test
    fun `a bundle-wide failure covers every variant`() {
        val failures = ParseFailures()
        failures.record("bundle-a", "h1", "red", bundleWide = true)
        assertTrue(failures.failed("bundle-a", "h1", "blue"))
        assertTrue(failures.failed("bundle-a", "h1", null))
        assertFalse(failures.failed("bundle-a", "h2", "blue"))
    }

    @Test
    fun `forgetting a bundle drops its variant failures`() {
        val failures = ParseFailures()
        failures.record("bundle-a", "h1", "red", bundleWide = false)
        failures.record("bundle-ab", "h1", "red", bundleWide = false)
        failures.forget("bundle-a")
        assertFalse(failures.failed("bundle-a", "h1", "red"))
        assertTrue(failures.failed("bundle-ab", "h1", "red"))
    }
}
