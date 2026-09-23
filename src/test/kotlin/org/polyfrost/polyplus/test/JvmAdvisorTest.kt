package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.JvmAdvisor
import org.polyfrost.polyplus.client.features.JvmAdvisor.HostMemory
import org.polyfrost.polyplus.client.features.JvmAdvisor.Kind
import org.polyfrost.polyplus.client.features.JvmAdvisor.Snapshot

class JvmAdvisorTest {
    private fun snapshot(
        maxHeapMb: Long = 4096,
        liveSetMb: Long = 1024,
        nonHeapMb: Long = 300,
        isZgc: Boolean = false,
        cores: Int = 16,
        gcSpikeRatio: Double = 1.0,
        host: HostMemory? = HostMemory(totalMb = 16384, availableMb = 9000, pressure = 1),
    ) = Snapshot(maxHeapMb, liveSetMb, nonHeapMb, isZgc, cores, gcSpikeRatio, host)

    @Test
    fun `a healthy setup gets no advice`() {
        assertNull(JvmAdvisor.evaluate(snapshot()))
    }

    @Test
    fun `a tight heap on a machine with room is told to raise`() {
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 4096, liveSetMb = 3000))
        assertEquals(Kind.RAISE_HEAP, advice?.kind)
        assertEquals(4096 + JvmAdvisor.HEAP_STEP_MB, advice?.suggestedMb)
    }

    @Test
    fun `a tight heap on a pressured machine is never told to raise`() {
        val pressured = HostMemory(totalMb = 3889, availableMb = 400, pressure = 4)
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 2026, liveSetMb = 1440, nonHeapMb = 442, host = pressured),
        )
        assertEquals(Kind.FREE_SYSTEM_MEMORY, advice?.kind)
    }

    @Test
    fun `a pressured machine with room to shrink is told to shrink`() {
        val pressured = HostMemory(totalMb = 8192, availableMb = 300, pressure = 2)
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 6144, liveSetMb = 1000, nonHeapMb = 400, host = pressured),
        )
        assertEquals(Kind.LOWER_HEAP, advice?.kind)
        assertTrue(advice!!.suggestedMb < advice.currentMb)
    }

    @Test
    fun `macOS pressure alone blocks a raise with plenty of free memory`() {
        val pressured = HostMemory(totalMb = 24576, availableMb = 1300, pressure = 2)
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 4096, liveSetMb = 3000, host = pressured))
        assertTrue(advice?.kind != Kind.RAISE_HEAP)
    }

    @Test
    fun `a pressured machine is not told to shrink below its live set`() {
        val pressured = HostMemory(totalMb = 4096, availableMb = 100, pressure = 4)
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 3072, liveSetMb = 1900, nonHeapMb = 400, host = pressured),
        )
        assertEquals(Kind.FREE_SYSTEM_MEMORY, advice?.kind)
    }

    @Test
    fun `spikes that never coincide with a pause are not blamed on GC`() {
        assertNull(JvmAdvisor.evaluate(snapshot(liveSetMb = 3000, gcSpikeRatio = 0.1)))
    }

    @Test
    fun `too few spikes to correlate does not suppress advice`() {
        val advice = JvmAdvisor.evaluate(snapshot(liveSetMb = 3000, gcSpikeRatio = -1.0))
        assertEquals(Kind.RAISE_HEAP, advice?.kind)
    }

    @Test
    fun `a raise is not guessed at without host numbers`() {
        assertNull(JvmAdvisor.evaluate(snapshot(liveSetMb = 3000, host = null)))
    }

    @Test
    fun `a tight heap with nothing free is told to close apps instead`() {
        val full = HostMemory(totalMb = 8192, availableMb = 1000, pressure = 1)
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 4096, liveSetMb = 3000, host = full))
        assertEquals(Kind.FREE_SYSTEM_MEMORY, advice?.kind)
    }

    @Test
    fun `an oversized heap on a small machine is told to shrink`() {
        val small = HostMemory(totalMb = 8192, availableMb = 3000, pressure = 1)
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 6144, liveSetMb = 1000, host = small))
        assertEquals(Kind.LOWER_HEAP, advice?.kind)
    }

    @Test
    fun `a big idle heap on a big machine is left alone`() {
        val roomy = HostMemory(totalMb = 32768, availableMb = 20000, pressure = 1)
        assertNull(JvmAdvisor.evaluate(snapshot(maxHeapMb = 6144, liveSetMb = 1000, host = roomy)))
    }

    @Test
    fun `ZGC on an underprovisioned setup is sent back to G1`() {
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 4096, liveSetMb = 1000, isZgc = true, cores = 8),
        )
        assertEquals(Kind.SWITCH_TO_G1, advice?.kind)
        assertTrue(advice!!.message.isNotBlank())
    }

    @Test
    fun `every heap change names both numbers in its message`() {
        val pressured = HostMemory(totalMb = 8192, availableMb = 300, pressure = 2)
        val small = HostMemory(totalMb = 8192, availableMb = 3000, pressure = 1)
        listOf(
            snapshot(maxHeapMb = 4096, liveSetMb = 3000),
            snapshot(maxHeapMb = 6144, liveSetMb = 1000, nonHeapMb = 400, host = pressured),
            snapshot(maxHeapMb = 6144, liveSetMb = 1000, host = small),
        ).forEach { s ->
            val advice = JvmAdvisor.evaluate(s)!!
            assertTrue(advice.message.contains("${advice.currentMb} MB"), advice.message)
            assertTrue(advice.message.contains("${advice.suggestedMb} MB"), advice.message)
        }
    }

    @Test
    fun `normal pressure with little free memory gets no pressure advice`() {
        val busy = HostMemory(totalMb = 16384, availableMb = 1500, pressure = 1)
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 4096, liveSetMb = 1024, host = busy))
        assertNull(advice)
    }

    @Test
    fun `linux PSI maps onto pressure levels`() {
        fun psi(some: String, full: String) =
            "some avg10=0.00 avg60=0.00 avg300=$some total=11177545\nfull avg10=0.00 avg60=0.00 avg300=$full total=9776761\n"
        assertEquals(JvmAdvisor.PRESSURE_NORMAL, JvmAdvisor.linuxPressure(psi("0.00", "0.00")))
        assertEquals(JvmAdvisor.PRESSURE_NORMAL, JvmAdvisor.linuxPressure(psi("9.99", "0.00")))
        assertEquals(JvmAdvisor.PRESSURE_WARN, JvmAdvisor.linuxPressure(psi("10.00", "0.00")))
        assertEquals(JvmAdvisor.PRESSURE_CRITICAL, JvmAdvisor.linuxPressure(psi("45.12", "12.30")))
        assertEquals(JvmAdvisor.PRESSURE_UNKNOWN, JvmAdvisor.linuxPressure("garbage"))
        assertEquals(JvmAdvisor.PRESSURE_UNKNOWN, JvmAdvisor.linuxPressure(""))
    }

    @Test
    fun `windows memory load maps onto pressure levels`() {
        assertEquals(JvmAdvisor.PRESSURE_NORMAL, JvmAdvisor.loadPressure(1000, 110))
        assertEquals(JvmAdvisor.PRESSURE_WARN, JvmAdvisor.loadPressure(1000, 100))
        assertEquals(JvmAdvisor.PRESSURE_CRITICAL, JvmAdvisor.loadPressure(1000, 50))
        assertEquals(JvmAdvisor.PRESSURE_UNKNOWN, JvmAdvisor.loadPressure(0, 0))
    }

    @Test
    fun `a busy machine paging heavily is pressured before its physical load is`() {
        assertEquals(JvmAdvisor.PRESSURE_WARN, JvmAdvisor.loadPressure(8154, 2065, 7816, 9434))
        assertEquals(JvmAdvisor.PRESSURE_CRITICAL, JvmAdvisor.loadPressure(8154, 2065, 9000, 9434))
    }

    @Test
    fun `swap on an otherwise idle machine is not called pressure`() {
        assertEquals(JvmAdvisor.PRESSURE_NORMAL, JvmAdvisor.loadPressure(16384, 12000, 900, 1024))
    }

    @Test
    fun `a machine without a pagefile is judged on physical load alone`() {
        assertEquals(JvmAdvisor.PRESSURE_NORMAL, JvmAdvisor.loadPressure(8154, 2065, 0, 0))
    }

    @Test
    fun `a common 3 GB heap on an idle 8 GB box is left alone`() {
        val small = HostMemory(totalMb = 8154, availableMb = 3000, pressure = 1)
        assertNull(JvmAdvisor.evaluate(snapshot(maxHeapMb = 3072, liveSetMb = 605, host = small)))
    }

    @Test
    fun `an oversized heap is lowered one step at a time`() {
        val small = HostMemory(totalMb = 8154, availableMb = 3000, pressure = 1)
        val advice = JvmAdvisor.evaluate(snapshot(maxHeapMb = 6144, liveSetMb = 605, host = small))
        assertEquals(Kind.LOWER_HEAP, advice?.kind)
        assertEquals(6144 - JvmAdvisor.HEAP_LOWER_STEP_MB, advice?.suggestedMb)
    }

    @Test
    fun `a lowered heap keeps headroom above the live set and never drops below the floor`() {
        assertEquals(JvmAdvisor.MIN_HEAP_MB, JvmAdvisor.minHeapMb(snapshot(liveSetMb = 605)))
        assertEquals(JvmAdvisor.MIN_HEAP_MB, JvmAdvisor.minHeapMb(snapshot(liveSetMb = -1)))
        assertEquals(3000 * JvmAdvisor.LIVE_SET_HEADROOM, JvmAdvisor.minHeapMb(snapshot(liveSetMb = 3000)))
        val pressured = HostMemory(totalMb = 4096, availableMb = 100, pressure = 4)
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 3072, liveSetMb = 700, nonHeapMb = 400, host = pressured),
        )
        assertEquals(Kind.FREE_SYSTEM_MEMORY, advice?.kind)
    }

    @Test
    fun `a 3 GB heap on a thrashing 8 GB box is told to close apps`() {
        val host = HostMemory(
            totalMb = 8154,
            availableMb = 2065,
            pressure = JvmAdvisor.loadPressure(8154, 2065, 7816, 9434),
        )
        val advice = JvmAdvisor.evaluate(
            snapshot(maxHeapMb = 3072, liveSetMb = 605, nonHeapMb = 432, cores = 4, host = host),
        )
        assertEquals(Kind.FREE_SYSTEM_MEMORY, advice?.kind)
    }
}
