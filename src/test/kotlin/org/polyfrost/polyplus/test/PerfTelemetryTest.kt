package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.PerfTelemetry
import kotlin.math.abs

class PerfTelemetryTest {
    private fun histogramOf(vararg frameNanos: Long): IntArray {
        val histogram = IntArray(PerfTelemetry.bucket(1_999_999_999L) + 1)
        frameNanos.forEach { histogram[PerfTelemetry.bucket(it)]++ }
        return histogram
    }

    private fun near(expected: Long, actual: Long, tolerance: Long) =
        assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected, got $actual")

    @Test
    fun `percentiles pick the slow tail`() {
        val frames = LongArray(990) { 6_900_000L } + LongArray(9) { 20_000_000L } + longArrayOf(100_000_000L)
        val histogram = histogramOf(*frames)
        near(6_900_000L, PerfTelemetry.quantileNanos(histogram, frames.size, 0.5), 50_000L)
        near(20_000_000L, PerfTelemetry.quantileNanos(histogram, frames.size, 0.999), 50_000L)
        near(6_900_000L, PerfTelemetry.quantileNanos(histogram, frames.size, 0.99), 50_000L)
    }

    @Test
    fun `coarse buckets cover long frames`() {
        val histogram = histogramOf(500_000_000L)
        near(500_000_000L, PerfTelemetry.quantileNanos(histogram, 1, 0.5), 1_000_000L)
    }

    @Test
    fun `pack hash matches the backend`() {
        val hash = PerfTelemetry.packHash(mapOf("a-b" to "1", "a" to "2.0+mc1.21"))
        assertTrue(hash == "fcf4abafd64ad5196c140f3f56261f73d1c4f3eea0e259c46ab5841f42d0374d", hash)
    }
}
