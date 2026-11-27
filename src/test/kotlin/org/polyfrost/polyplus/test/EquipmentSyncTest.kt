package org.polyfrost.polyplus.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.EquipmentSync
import org.polyfrost.polyplus.client.cosmetics.SkinChangeGate
import org.polyfrost.polyplus.client.cosmetics.colors.LookQueue
import org.polyfrost.polyplus.client.cosmetics.confirmEquipment
import org.polyfrost.polyplus.client.cosmetics.outfitLoadout
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

class EquipmentSyncTest {
    @Test
    fun `an outfit apply keeps its queue place while the library lock is busy`() = runBlocking {
        val queue = LookQueue()
        val library = Mutex()
        val order = mutableListOf<String>()
        library.lock()
        val outfit = launch { queue.handOff(library, { order += "outfit" }) { order += "outfit skin" } }
        yield()
        val hat = launch { queue.run { order += "hat" } }
        yield()
        library.unlock()
        withTimeout(5_000) {
            outfit.join()
            hat.join()
        }
        assertEquals("outfit", order.first())
        assertTrue(order.indexOf("hat") > order.indexOf("outfit"))
    }

    @Test
    fun `the queue moves on while the hand-off finishes under the lock`() = runBlocking {
        val queue = LookQueue()
        val library = Mutex()
        var queuedDuringFinish = false
        var lockedDuringFinish = false
        queue.handOff(library, { }) {
            val next = launch { queue.run { queuedDuringFinish = true } }
            withTimeout(5_000) { next.join() }
            lockedDuringFinish = library.isLocked
        }
        assertTrue(queuedDuringFinish)
        assertTrue(lockedDuringFinish)
        assertFalse(library.isLocked)
    }

    @Test
    fun `a failed hand-off releases the queue and the lock`() = runBlocking {
        val queue = LookQueue()
        val library = Mutex()
        val failure = runCatching { queue.handOff<Unit, Unit>(library, { error("apply failed") }) { } }
        assertTrue(failure.isFailure)
        assertFalse(library.isLocked)
        withTimeout(5_000) { queue.run { } }
    }

    @Test
    fun `model-specific equips wait for a pending skin change`() = runBlocking {
        val gate = SkinChangeGate()
        gate.begin()
        gate.begin()
        var resolved = false
        val equip = launch {
            gate.settled()
            resolved = true
        }
        yield()
        gate.end()
        yield()
        assertFalse(resolved)
        gate.end()
        withTimeout(5_000) { equip.join() }
        assertTrue(resolved)
    }

    @Test
    fun `a settled gate never waits`() = runBlocking {
        val gate = SkinChangeGate()
        gate.end()
        withTimeout(5_000) { gate.settled() }
    }

    @Test
    fun `a transient refresh failure is retried`() = runBlocking {
        var attempts = 0
        var assumed: Map<BodySlot, Int>? = null
        var reconciles = 0
        val result = confirmEquipment(
            refresh = { if (++attempts < 2) Result.failure(IllegalStateException("offline")) else Result.success(Unit) },
            expected = mapOf(BodySlot.Hat to 2),
            assume = { assumed = it },
            reconcileLater = { reconciles++ },
            retryDelays = listOf(1L, 1L),
            pause = { },
        )
        assertTrue(result.isSuccess)
        assertEquals(2, attempts)
        assertNull(assumed)
        assertEquals(0, reconciles)
    }

    @Test
    fun `a stalled refresh gives up at the deadline and falls back to the known loadout`() = runBlocking {
        var assumed: Map<BodySlot, Int>? = null
        var reconciles = 0
        val result = withTimeout(5_000) {
            confirmEquipment(
                refresh = { awaitCancellation() },
                expected = mapOf(BodySlot.Hat to 2),
                assume = { assumed = it },
                reconcileLater = { reconciles++ },
                retryDelays = listOf(1L, 1L),
                pause = { },
                deadline = 50L,
            )
        }
        assertTrue(result.isSuccess)
        assertEquals(mapOf(BodySlot.Hat to 2), assumed)
        assertEquals(1, reconciles)
    }

    @Test
    fun `a refresh that keeps failing falls back to the known loadout`() = runBlocking {
        var attempts = 0
        var assumed: Map<BodySlot, Int>? = null
        var reconciles = 0
        val result = confirmEquipment(
            refresh = {
                attempts++
                Result.failure(IllegalStateException("offline"))
            },
            expected = mapOf(BodySlot.Hat to 2),
            assume = { assumed = it },
            reconcileLater = { reconciles++ },
            retryDelays = listOf(1L, 1L),
            pause = { },
        )
        assertTrue(result.isSuccess)
        assertEquals(3, attempts)
        assertEquals(mapOf(BodySlot.Hat to 2), assumed)
        assertEquals(1, reconciles)
    }

    @Test
    fun `a refresh failure without a known loadout is reported`() = runBlocking {
        var reconciles = 0
        val result = confirmEquipment(
            refresh = { Result.failure(IllegalStateException("offline")) },
            expected = null,
            assume = { error("nothing to assume") },
            reconcileLater = { reconciles++ },
            retryDelays = emptyList(),
            pause = { },
        )
        assertTrue(result.isFailure)
        assertEquals(1, reconciles)
    }

    @Test
    fun `slow asset loads don't hold the equip queue`() = runBlocking {
        val queue = LookQueue()
        val assets = CompletableDeferred<Unit>()
        var loaded = false
        var published = 0
        val sync = EquipmentSync(
            scope = this,
            queue = queue,
            refresh = { Result.success(Unit) },
            assume = { },
            publish = { published++ },
            load = {
                assets.await()
                loaded = true
            },
            pause = { },
        )
        val result = withTimeout(5_000) { queue.run { sync.confirm() } }
        assertTrue(result.isSuccess)
        assertEquals(1, published)
        assertFalse(loaded)
        withTimeout(5_000) { queue.run { } }
        assets.complete(Unit)
        withTimeout(5_000) { while (!loaded) yield() }
        assertTrue(loaded)
    }

    @Test
    fun `a failed refresh assumes the loadout and reconciles through the queue later`() = runBlocking {
        val queue = LookQueue()
        var serverReachable = false
        var assumed: Map<BodySlot, Int>? = null
        var published = 0
        var refreshes = 0
        val reconciled = CompletableDeferred<Unit>()
        val sync = EquipmentSync(
            scope = this,
            queue = queue,
            refresh = {
                refreshes++
                if (serverReachable) Result.success(Unit) else Result.failure(IllegalStateException("offline"))
            },
            assume = { assumed = it },
            publish = {
                published++
                if (serverReachable) reconciled.complete(Unit)
            },
            load = { },
            retryDelays = emptyList(),
            reconcileDelays = listOf(1L, 1L),
            pause = { yield() },
        )
        val result = queue.run {
            sync.confirm(mapOf(BodySlot.Hat to 7)).also { serverReachable = true }
        }
        assertTrue(result.isSuccess)
        assertEquals(mapOf(BodySlot.Hat to 7), assumed)
        withTimeout(5_000) { reconciled.await() }
        assertEquals(2, refreshes)
        assertEquals(2, published)
    }

    @Test
    fun `the fallback loadout keeps only owned pieces in allowed slots`() {
        val loadout = outfitLoadout(
            mapOf(BodySlot.Hat to 1, BodySlot.Cape to 2, BodySlot.Wings to 3),
            owned = setOf(1, 3),
        ) { id, slot -> !(id == 3 && slot == BodySlot.Wings) }
        assertEquals(mapOf(BodySlot.Hat to 1), loadout)
    }
}
