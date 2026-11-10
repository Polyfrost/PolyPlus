package org.polyfrost.polyplus.nameplate.engine.internal.flag

internal object FlagFitting {
    fun fit(weights: IntArray, rows: Int): IntArray? {
        val bands = weights.size
        if (bands == 0 || rows < bands) return null

        val total = weights.sum().toLong()
        val fitted = IntArray(bands)
        val remainders = LongArray(bands)
        var assigned = 0
        for (i in 0 until bands) {
            val quota = weights[i].toLong() * rows
            fitted[i] = (quota / total).toInt()
            remainders[i] = quota % total
            assigned += fitted[i]
        }

        repeat(rows - assigned) {
            var best = 0
            for (i in 1 until bands) {
                if (remainders[i] > remainders[best]) {
                    best = i
                }
            }
            fitted[best]++
            remainders[best] = -1
        }

        for (i in 0 until bands) {
            if (fitted[i] == 0) {
                var donor = 0
                for (j in 1 until bands) {
                    if (fitted[j] > fitted[donor]) {
                        donor = j
                    }
                }
                fitted[donor]--
                fitted[i] = 1
            }
        }

        return fitted
    }

    fun fitSymmetric(weights: IntArray, rows: Int): IntArray? {
        val bands = weights.size
        val pairs = bands / 2
        val odd = bands % 2 == 1
        if (bands == 0 || rows < bands || (!odd && rows % 2 == 1)) return fit(weights, rows)
        if (pairs == 0) return intArrayOf(rows)

        var middle = 0
        if (odd) {
            middle = maxOf(1, (weights[pairs].toLong() * rows / weights.sum()).toInt())
            if ((rows - middle) % 2 != 0) {
                middle++
            }
            while (rows - middle < 2 * pairs && middle - 2 >= 1) {
                middle -= 2
            }
            if (rows - middle < 2 * pairs) return fit(weights, rows)
        }

        val half = fit(weights.copyOfRange(0, pairs), (rows - middle) / 2) ?: return fit(weights, rows)
        return IntArray(bands) { i ->
            when {
                i < pairs -> half[i]
                odd && i == pairs -> middle
                else -> half[bands - 1 - i]
            }
        }
    }

    fun bandAt(fitted: IntArray, row: Int): Int {
        var end = 0
        for ((band, rows) in fitted.withIndex()) {
            end += rows
            if (row < end) return band
        }
        return fitted.size - 1
    }

    fun chevronStripe(column: Int, row: Int, rows: Int, width: Int, stripes: Int): Int {
        val depth = minOf(row, rows - 1 - row)
        val stripe = (column - depth).floorDiv(width)
        return if (column < depth + stripes * width) maxOf(stripe, 0) else -1
    }

    fun circlePart(column: Int, row: Int, columns: Int, rows: Int, width: Int): Int {
        val outer = rows - 2
        if (outer < 2) return -1

        val dx = 2 * column + 1 - columns
        val dy = 2 * row + 1 - rows
        val distance = dx * dx + dy * dy
        val inner = maxOf(outer - 2 * width, 0)
        return when {
            distance > outer * outer -> -1
            distance > inner * inner -> 0
            else -> 1
        }
    }
}
