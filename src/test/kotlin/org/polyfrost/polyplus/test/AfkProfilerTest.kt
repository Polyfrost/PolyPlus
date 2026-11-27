package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.AfkProfiler

class AfkProfilerTest {
    @Test
    fun `scrub masks home without changing length`() {
        val input = "-Dx=C:\\Users\\alice\\a C:/Users/alice/b /net/x".toByteArray()
        val out = String(AfkProfiler.scrub(input.copyOf(), "C:\\Users\\alice", "")!!)
        assertEquals("-Dx=~~~~~~~~~~~~~~\\a ~~~~~~~~~~~~~~/b /net/x", out)
        assertEquals(input.size, out.length)
    }

    @Test
    fun `scrub masks spark-redacted vm args before home`() {
        val written = "-Xmx4G -Dtoken=s3cr3t -Dlog=/home/<redacted>/x -Dw=C:\\Users\\<redacted>\\y"
        val input = "\u0012\u0040$written\u001a\u0010/home/alice/game".toByteArray()
        val vmArgs = "-Xmx4G -Dtoken=s3cr3t -Dlog=/home/alice/x -Dw=C:\\Users\\alice\\y"
        val out = String(AfkProfiler.scrub(input.copyOf(), "/home/alice", vmArgs)!!)
        assertEquals("\u0012\u0040${"~".repeat(written.length)}\u001a\u0010~~~~~~~~~~~/game", out)
        assertEquals(input.size, out.length)
    }

    @Test
    fun `scrub refuses when vm args are missing`() {
        val input = "-Xmx4G -Dtoken=s3cr3t".toByteArray()
        assertNull(AfkProfiler.scrub(input, "/home/alice", "-Xmx4G -Dtoken=other"))
    }
}
