package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.render.DetailBudget

class DetailBudgetTest {
    private fun run(budget: DetailBudget, nanos: Long, frames: Int): Int {
        repeat(frames) { budget.sample(nanos) }
        return budget.pressure
    }

    @Test
    fun `frames under budget keep full pressure off`() {
        val budget = DetailBudget()
        assertEquals(0, run(budget, 500_000L, 1_000))
    }

    @Test
    fun `a single slow frame does not raise pressure`() {
        val budget = DetailBudget()
        run(budget, 500_000L, 100)
        budget.sample(20_000_000L)
        assertEquals(0, run(budget, 500_000L, 200))
    }

    @Test
    fun `sustained cost raises pressure step by step up to the cap`() {
        val budget = DetailBudget()
        val first = run(budget, 3_000_000L, 40)
        assertEquals(true, first in 1 until DetailBudget.MAX_PRESSURE)
        assertEquals(DetailBudget.MAX_PRESSURE, run(budget, 3_000_000L, 500))
    }

    @Test
    fun `cost between the thresholds holds pressure`() {
        val budget = DetailBudget()
        run(budget, 3_000_000L, 60)
        val held = run(budget, 1_200_000L, 50)
        assertEquals(true, held > 0)
        assertEquals(held, run(budget, 1_200_000L, 2_000))
    }

    @Test
    fun `pressure recovers slowly once cost drops well under budget`() {
        val budget = DetailBudget()
        run(budget, 3_000_000L, 500)
        assertEquals(DetailBudget.MAX_PRESSURE, budget.pressure)
        assertEquals(DetailBudget.MAX_PRESSURE, run(budget, 200_000L, 60))
        val steps = DetailBudget.MAX_PRESSURE * DetailBudget.RECOVER_FRAMES + 100
        assertEquals(0, run(budget, 200_000L, steps))
    }

    @Test
    fun `levels without pressure follow distance and the full count`() {
        val near = 10.0 * 10.0
        val far = 40.0 * 40.0
        val gone = 70.0 * 70.0
        val fullSq = 24.0 * 24.0
        assertEquals(Renderer.DETAIL_FULL, DetailBudget.level(0, near, fullSq, 32, 0))
        assertEquals(Renderer.DETAIL_REDUCED, DetailBudget.level(32, near, fullSq, 32, 0))
        assertEquals(Renderer.DETAIL_REDUCED, DetailBudget.level(0, far, fullSq, 32, 0))
        assertEquals(Renderer.DETAIL_MINIMAL, DetailBudget.level(0, gone, fullSq, 32, 0))
        assertEquals(Renderer.DETAIL_MINIMAL, DetailBudget.level(0, Double.NaN, fullSq, 32, 0))
    }

    @Test
    fun `pressure drops distant wearers first`() {
        val near = 10.0 * 10.0
        val fullSq = 24.0 * 24.0
        assertEquals(Renderer.DETAIL_FULL, DetailBudget.level(15, near, fullSq, 32, 1))
        assertEquals(Renderer.DETAIL_REDUCED, DetailBudget.level(16, near, fullSq, 32, 1))
        assertEquals(Renderer.DETAIL_FULL, DetailBudget.level(3, near, fullSq, 32, 3))
        assertEquals(Renderer.DETAIL_REDUCED, DetailBudget.level(4, near, fullSq, 32, 3))
        assertEquals(Renderer.DETAIL_REDUCED, DetailBudget.level(31, near, fullSq, 32, 4))
        assertEquals(Renderer.DETAIL_MINIMAL, DetailBudget.level(32, near, fullSq, 32, 4))
        assertEquals(Renderer.DETAIL_FULL, DetailBudget.level(0, near, fullSq, 32, DetailBudget.MAX_PRESSURE))
        assertEquals(Renderer.DETAIL_MINIMAL, DetailBudget.level(8, near, fullSq, 32, DetailBudget.MAX_PRESSURE))
    }
}
