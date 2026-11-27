package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.playback.EmoteSceneClock

class EmoteSceneClockTest {
    private class Play(val wearer: Long, val origin: Double, val startLocal: Double) {
        val shift: Double get() = origin - startLocal

        fun age(local: Double): Double = local - startLocal

        fun wanted(local: Double): Double = origin + age(local)
    }

    private fun EmoteSceneClock.frame(play: Play, local: Double): Double =
        at(play.wearer, play.wanted(local), play.shift, local)

    @Test
    fun `keeps one rising clock for plays started under different server offsets`() {
        val clock = EmoteSceneClock()
        val ahead = Play(1L, origin = 5_000.020, startLocal = 100.0)
        val behind = Play(2L, origin = 5_000.000, startLocal = 100.0)
        var previous = Double.NEGATIVE_INFINITY
        var local = 100.0
        var sawDrop = false
        repeat(200) {
            val first = clock.frame(ahead, local)
            if (behind.wanted(local + 0.001) < first) sawDrop = true
            val second = clock.frame(behind, local + 0.001)
            assertTrue(first >= previous, "scene clock went back at $local")
            assertTrue(second >= first, "scene clock went back between wearers at $local")
            previous = second
            local += 0.016
        }
        assertTrue(sawDrop)
    }

    @Test
    fun `keeps every play's start fixed on the shared clock`() {
        val clock = EmoteSceneClock()
        val ahead = Play(1L, origin = 5_000.020, startLocal = 100.0)
        val behind = Play(2L, origin = 5_000.000, startLocal = 100.5)
        val starts = HashMap<Long, MutableList<Double>>()
        var local = 100.5
        repeat(200) {
            for (play in listOf(behind, ahead)) {
                val at = local + if (play === ahead) 0.002 else 0.0
                val time = clock.frame(play, at)
                starts.getOrPut(play.wearer) { ArrayList() } += time - play.age(at)
            }
            local += 0.05
        }
        for ((wearer, list) in starts) {
            val settled = list.drop(1)
            val spread = settled.max() - settled.min()
            assertTrue(spread < 1e-6, "start of wearer $wearer moved by $spread")
        }
    }

    @Test
    fun `matches a lone play's own server-aligned clock`() {
        val clock = EmoteSceneClock()
        val play = Play(7L, origin = 1_764_000_000.25, startLocal = 40.0)
        var local = 40.0
        repeat(50) {
            assertEquals(play.wanted(local), clock.frame(play, local))
            local += 0.05
        }
    }

    @Test
    fun `realigns to a new play once earlier wearers are forgotten`() {
        val clock = EmoteSceneClock()
        val ahead = Play(1L, origin = 5_000.080, startLocal = 100.0)
        val behind = Play(2L, origin = 5_000.000, startLocal = 100.0)
        clock.frame(ahead, 100.0)
        clock.frame(behind, 100.01)
        clock.forget(ahead.wearer)
        clock.forget(behind.wearer)
        val later = Play(3L, origin = 5_002.000, startLocal = 102.0)
        assertEquals(later.wanted(102.0), clock.frame(later, 102.0))
        assertEquals(later.wanted(102.5), clock.frame(later, 102.5))
    }

    @Test
    fun `realigns once earlier wearers stop drawing`() {
        val clock = EmoteSceneClock()
        val ahead = Play(1L, origin = 5_000.080, startLocal = 100.0)
        clock.frame(ahead, 100.0)
        val later = Play(2L, origin = 5_003.000, startLocal = 103.0)
        assertEquals(later.wanted(103.0), clock.frame(later, 103.0))
    }

    @Test
    fun `does not step back when a fresh play trails the last scene time`() {
        val clock = EmoteSceneClock()
        val ahead = Play(1L, origin = 5_000.080, startLocal = 100.0)
        val last = clock.frame(ahead, 100.0)
        clock.forget(ahead.wearer)
        val trailing = Play(2L, origin = 5_000.000, startLocal = 100.02)
        val first = clock.frame(trailing, 100.02)
        assertEquals(last, first, 1e-9)
        assertTrue(first >= last)
        val start = first - trailing.age(100.02)
        var local = 100.02
        repeat(20) {
            local += 0.05
            val time = clock.frame(trailing, local)
            assertEquals(start, time - trailing.age(local), 1e-9)
        }
    }

    @Test
    fun `keeps running wearers on their clock when a later play has a higher offset`() {
        val clock = EmoteSceneClock()
        val behind = Play(1L, origin = 5_000.000, startLocal = 100.0)
        val ahead = Play(2L, origin = 5_000.580, startLocal = 100.5)
        var local = 100.0
        repeat(10) {
            assertEquals(behind.wanted(local), clock.frame(behind, local), 1e-9)
            local += 0.05
        }
        var previous = Double.NEGATIVE_INFINITY
        repeat(40) {
            assertEquals(behind.wanted(local), clock.frame(behind, local), 1e-9)
            val joined = clock.frame(ahead, local + 0.001)
            assertTrue(joined >= previous, "joined wearer went back at $local")
            assertTrue(joined <= ahead.wanted(local + 0.001) + 1e-9)
            previous = joined
            local += 0.05
        }
    }

    @Test
    fun `keeps a late joiner's age and its server start`() {
        val clock = EmoteSceneClock()
        val watcher = Play(1L, origin = 5_000.000, startLocal = 100.0)
        clock.frame(watcher, 100.0)
        val joined = Play(2L, origin = 4_970.000, startLocal = 70.0)
        val time = clock.frame(joined, 100.001)
        assertEquals(joined.wanted(100.001), time, 1e-9)
        assertEquals(joined.origin, time - joined.age(100.001), 1e-9)
    }

    @Test
    fun `follows a custom clock scrubbed far back`() {
        val clock = EmoteSceneClock()
        val play = Play(1L, origin = 5_000.0, startLocal = 0.0)
        clock.frame(play, 10.0)
        val back = clock.frame(play, 2.0)
        assertEquals(play.wanted(2.0), back)
    }

    @Test
    fun `keeps a playing wearer's clock through a frame stall`() {
        val clock = EmoteSceneClock()
        val play = Play(1L, origin = 5_000.0, startLocal = 100.0)
        var local = 100.0
        repeat(10) {
            clock.frame(play, local)
            local += 0.05
        }
        local += 1.5
        val estimate = 0.5
        val time = clock.at(play.wearer, play.wanted(local) + estimate, play.shift + estimate, local)
        assertEquals(play.wanted(local), time, 1e-9)
    }

    @Test
    fun `holds through a small backward step`() {
        val clock = EmoteSceneClock()
        val play = Play(1L, origin = 5_000.0, startLocal = 0.0)
        val last = clock.frame(play, 10.0)
        assertEquals(last, clock.frame(play, 9.9))
    }
}
