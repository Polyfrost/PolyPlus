package org.polyfrost.polyplus.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CatalogRefresh
import org.polyfrost.polyplus.client.cosmetics.CatalogUpdateRetry
import org.polyfrost.polyplus.client.cosmetics.refreshAfterUpdate

class CatalogUpdateRetryTest {
    private fun outcome(loaded: Boolean) = if (loaded) CatalogRefresh.Done else CatalogRefresh.Failed

    @Test
    fun `a failed refresh is retried with a growing backoff until one succeeds`() = runBlocking {
        val retry = CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000)
        val outcomes = ArrayDeque(listOf(false, false, false, false, true))
        var current = false
        var fetches = 0
        val pauses = ArrayList<Long>()
        retry.run(
            current = { current },
            allowed = { true },
            fetch = {
                fetches++
                outcome(outcomes.removeFirst().also { if (it) current = true })
            },
            pause = { pauses += it },
        )
        assertEquals(5, fetches)
        assertEquals(9_000L, pauses.sum())
    }

    @Test
    fun `a change announced during a successful refresh is fetched again`() = runBlocking {
        val retry = CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000)
        var fetches = 0
        var current = false
        retry.run(
            current = { current },
            allowed = { true },
            fetch = {
                fetches++
                if (fetches == 2) current = true
                CatalogRefresh.Done
            },
            pause = { error("a successful refresh never waits") },
        )
        assertEquals(2, fetches)
    }

    @Test
    fun `the backoff resets after a success`() = runBlocking {
        val retry = CatalogUpdateRetry(baseMs = 1_000, maxMs = 8_000)
        val outcomes = ArrayDeque(listOf(false, false, true, false, true))
        var fetches = 0
        val pauses = ArrayList<Long>()
        retry.run(
            current = { fetches == 5 },
            allowed = { true },
            fetch = {
                fetches++
                outcome(outcomes.removeFirst())
            },
            pause = { pauses += it },
        )
        assertEquals(4_000L, pauses.sum())
    }

    @Test
    fun `nothing is fetched once online services are turned off`() = runBlocking {
        val retry = CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000)
        var allowed = true
        var fetches = 0
        retry.run(
            current = { false },
            allowed = { allowed },
            fetch = {
                fetches++
                allowed = false
                CatalogRefresh.Failed
            },
            pause = {},
        )
        assertEquals(1, fetches)
    }

    @Test
    fun `an already current catalog is not fetched`() = runBlocking {
        var fetches = 0
        CatalogUpdateRetry().run(
            current = { true },
            allowed = { true },
            fetch = {
                fetches++
                CatalogRefresh.Done
            },
            pause = {},
        )
        assertEquals(0, fetches)
    }

    @Test
    fun `hidden cosmetics are refreshed even when an ordinary load already caught up with the catalog`() = runBlocking {
        var unlistedCurrent = false
        var loads = 0
        val unlisted = ArrayDeque(listOf(false, true))
        val pauses = ArrayList<Long>()
        CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000).run(
            current = { unlistedCurrent },
            allowed = { true },
            fetch = {
                refreshAfterUpdate(
                    catalogCurrent = { true },
                    unlistedCurrent = { unlistedCurrent },
                    loadCatalog = {
                        loads++
                        true
                    },
                    refreshUnlisted = { unlisted.removeFirst().also { if (it) unlistedCurrent = true } },
                )
            },
            pause = { pauses += it },
        )
        assertEquals(0, loads)
        assertEquals(listOf(1_000L), pauses)
        assertEquals(true, unlistedCurrent)
    }

    @Test
    fun `hidden cosmetics held by a missing lookup route wait for the route to come back`() = runBlocking {
        var routeWaitMs = 30_000L
        val outcomes = ArrayDeque(listOf(false, false, true))
        var current = false
        val pauses = ArrayList<Long>()
        CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000).run(
            current = { current },
            allowed = { true },
            fetch = {
                val loaded = outcomes.removeFirst().also { if (it) current = true }
                if (loaded) CatalogRefresh.Done else CatalogRefresh.PublicOnly
            },
            pause = {
                pauses += it
                routeWaitMs = 0L
            },
            hold = { routeWaitMs },
        )
        assertEquals(32_000L, pauses.sum())
        assertEquals(true, current)
    }

    @Test
    fun `a catalog change during a route hold is fetched without waiting out the hold`() = runBlocking {
        var catalogStale = false
        var fetches = 0
        var current = false
        val pauses = ArrayList<Long>()
        CatalogUpdateRetry(baseMs = 1_000, maxMs = 3_000).run(
            current = { current },
            allowed = { true },
            fetch = {
                fetches++
                current = fetches == 2
                if (current) CatalogRefresh.Done else CatalogRefresh.PublicOnly
            },
            pause = {
                pauses += it
                if (pauses.size == 3) catalogStale = true
            },
            hold = { 1_800_000L },
            updates = { if (catalogStale) 1L else 0L },
        )
        assertEquals(2, fetches)
        assertEquals(3_000L, pauses.sum())
    }

    @Test
    fun `hidden cosmetics wait for the public catalog to load`() = runBlocking {
        var refreshed = 0
        val loaded = refreshAfterUpdate(
            catalogCurrent = { false },
            unlistedCurrent = { false },
            loadCatalog = { false },
            refreshUnlisted = {
                refreshed++
                true
            },
        )
        assertEquals(CatalogRefresh.Failed, loaded)
        assertEquals(0, refreshed)
    }

    @Test
    fun `a missing hidden lookup route does not slow down later public catalog changes`() = runBlocking {
        var update = 0L
        var fetches = 0
        val pauses = ArrayList<Long>()
        CatalogUpdateRetry(baseMs = 1_000, maxMs = 300_000).run(
            current = { fetches == 4 },
            allowed = { true },
            fetch = {
                fetches++
                CatalogRefresh.PublicOnly
            },
            pause = {
                pauses += it
                if (pauses.size % 2 == 0) update++
            },
            hold = { 1_800_000L },
            updates = { update },
        )
        assertEquals(4, fetches)
        assertEquals(8_000L, pauses.sum())
    }

    @Test
    fun `a loaded public catalog resets the public backoff even when hidden cosmetics fail`() = runBlocking {
        val outcomes = ArrayDeque(listOf(CatalogRefresh.Failed, CatalogRefresh.Failed, CatalogRefresh.PublicOnly, CatalogRefresh.Failed))
        val pauses = ArrayList<Long>()
        var fetches = 0
        CatalogUpdateRetry(baseMs = 1_000, maxMs = 8_000).run(
            current = { fetches == 4 && outcomes.isEmpty() },
            allowed = { true },
            fetch = {
                fetches++
                outcomes.removeFirst()
            },
            pause = { pauses += it },
        )
        assertEquals(5_000L, pauses.sum())
    }
}
