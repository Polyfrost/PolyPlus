package org.polyfrost.polyplus.nameplate.engine.flag

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.polyfrost.polyplus.nameplate.engine.internal.flag.FlagFitting

class FlagFittingTest {
    @Test
    fun equalBandsSplitEvenly() {
        assertContentEquals(intArrayOf(4, 4, 4), FlagFitting.fit(intArrayOf(1, 1, 1), 12))
    }

    @Test
    fun tiedRemaindersGoToEarlierBands() {
        assertContentEquals(intArrayOf(3, 3, 2), FlagFitting.fit(intArrayOf(1, 1, 1), 8))
    }

    @Test
    fun largestRemaindersWin() {
        assertContentEquals(intArrayOf(2, 3, 2, 3, 2), FlagFitting.fit(intArrayOf(1, 2, 1, 2, 1), 12))
    }

    @Test
    fun everyBandGetsARow() {
        assertContentEquals(intArrayOf(1, 3), FlagFitting.fit(intArrayOf(1, 100), 4))
    }

    @Test
    fun moreBandsThanRowsCannotFit() {
        assertNull(FlagFitting.fit(intArrayOf(1, 1, 1), 2))
        assertNull(FlagFitting.fit(IntArray(0), 8))
    }

    @Test
    fun fittingAlwaysFillsEveryRow() {
        for (rows in 6..40) {
            for (weights in listOf(intArrayOf(1, 1, 1, 1, 1, 1), intArrayOf(3, 1, 2), intArrayOf(1, 7, 1, 7, 1))) {
                val fitted = FlagFitting.fit(weights, rows) ?: continue
                assertEquals(rows, fitted.sum(), "rows $rows")
                assertEquals(0, fitted.count { it < 1 })
            }
        }
    }

    @Test
    fun symmetricFlagsMirror() {
        assertContentEquals(intArrayOf(3, 2, 2, 2, 3), FlagFitting.fitSymmetric(intArrayOf(1, 1, 1, 1, 1), 12))
        assertContentEquals(intArrayOf(5, 4, 4, 4, 5), FlagFitting.fitSymmetric(intArrayOf(1, 1, 1, 1, 1), 22))
        assertContentEquals(intArrayOf(3, 2, 2, 3), FlagFitting.fitSymmetric(intArrayOf(1, 1, 1, 1), 10))
        assertContentEquals(intArrayOf(9), FlagFitting.fitSymmetric(intArrayOf(1), 9))
    }

    @Test
    fun symmetricFittingFallsBackWhenItCannotMirror() {
        assertContentEquals(FlagFitting.fit(intArrayOf(1, 1, 1, 1), 11), FlagFitting.fitSymmetric(intArrayOf(1, 1, 1, 1), 11))
        assertNull(FlagFitting.fitSymmetric(intArrayOf(1, 1, 1), 2))
    }

    @Test
    fun symmetricFittingStaysAPalindrome() {
        for (rows in 5..40) {
            for (weights in listOf(intArrayOf(1, 1, 1, 1, 1), intArrayOf(1, 2, 3, 2, 1), intArrayOf(2, 1, 1, 2))) {
                val fitted = FlagFitting.fitSymmetric(weights, rows) ?: continue
                assertEquals(rows, fitted.sum(), "rows $rows")
                assertEquals(0, fitted.count { it < 1 })
                if (weights.size % 2 == 1 || rows % 2 == 0) {
                    assertContentEquals(fitted.reversedArray(), fitted, "rows $rows")
                }
            }
        }
    }

    @Test
    fun rowsMapToTheirBand() {
        val fitted = intArrayOf(2, 3, 2)
        assertEquals(listOf(0, 0, 1, 1, 1, 2, 2), (0 until 7).map { FlagFitting.bandAt(fitted, it) })
    }

    @Test
    fun chevronStepsOneColumnPerRow() {
        assertEquals(listOf(0, 0, 1, 1, -1), (0..4).map { FlagFitting.chevronStripe(it, 0, 12, 2, 2) })
        assertEquals(listOf(0, 0, 1, 1, -1), (3..7).map { FlagFitting.chevronStripe(it, 3, 12, 2, 2) })
        assertEquals(listOf(0, 0, 1, 1, -1), (5..9).map { FlagFitting.chevronStripe(it, 6, 12, 2, 2) })
        assertEquals(listOf(0, 0, 1, 1, -1), (5..9).map { FlagFitting.chevronStripe(it, 5, 12, 2, 2) })
        assertEquals(0, FlagFitting.chevronStripe(0, 5, 12, 2, 2))
    }

    @Test
    fun circleHasARingAndAnInside() {
        assertEquals(1, FlagFitting.circlePart(14, 5, 30, 12, 1))
        assertEquals(0, FlagFitting.circlePart(19, 5, 30, 12, 1))
        assertEquals(-1, FlagFitting.circlePart(20, 5, 30, 12, 1))
        assertEquals(-1, FlagFitting.circlePart(14, 0, 30, 3, 1))
    }
}
