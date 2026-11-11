package org.polyfrost.polyplus.nameplate.engine.internal.strip

internal class PixelStrips private constructor(
    val owners: IntArray,
    val geometry: IntArray,
    val patterns: IntArray,
    val covered: Int,
) {
    val count: Int
        get() = geometry.size / STRIDE

    val bytes: Int
        get() = (owners.size + geometry.size + patterns.size) * Int.SIZE_BYTES

    private class Builder(val rects: IntArray, count: Int) {
        val owners = IntArray(count) { -1 }
        val geometry = IntList()
        val patterns = IntList()
        var covered = 0

        fun build(): PixelStrips =
            if (geometry.size == 0) NONE else PixelStrips(owners, geometry.toIntArray(), patterns.toIntArray(), covered)

        fun run(run: IntList) {
            val m = run.size
            val bounds = IntArray(m + 1)
            for (k in 0 until m) {
                val at = run[k] * RECT_STRIDE
                bounds[k + 1] = bounds[k] + rects[at + 2] - rects[at]
            }
            val refs = IntArray(bounds[m])
            for (k in 0 until m) {
                refs.fill(rects[run[k] * RECT_STRIDE + RECT_REF], bounds[k], bounds[k + 1])
            }

            var a = 0
            while (a + MIN_RECTS <= m) {
                val start = bounds[a]
                val limit = minOf(refs.size, start + MAX_LENGTH)
                var reach = start
                var period = 0
                for (p in 1..MAX_PERIOD) {
                    if (start + p > limit) break
                    var end = start + p
                    while (end < limit && refs[end] == refs[end - p]) {
                        end++
                    }
                    if (end > reach) {
                        reach = end
                        period = p
                    }
                }

                var b = a
                while (b < m && bounds[b + 1] <= reach) {
                    b++
                }
                val length = bounds[b] - start
                if (period > 0 && b - a >= MIN_RECTS && length >= period) {
                    emit(run, a, b, length, period, refs, start)
                    a = b
                } else {
                    a++
                }
            }
        }

        private fun emit(run: IntList, from: Int, to: Int, length: Int, period: Int, refs: IntArray, start: Int) {
            val first = run[from] * RECT_STRIDE
            val index = geometry.size / STRIDE
            geometry.add(rects[first])
            geometry.add(rects[first + 1])
            geometry.add(rects[first] + length)
            geometry.add(rects[first + 3])
            geometry.add(period)
            geometry.add(patterns.size)
            for (k in 0 until period) {
                patterns.add(refs[start + k])
            }
            for (k in from until to) {
                owners[run[k]] = index
            }
            covered += to - from
        }
    }

    companion object {
        const val RECT_STRIDE = 5
        const val RECT_REF = 4
        const val STRIDE = 6
        const val PERIOD = 4
        const val PATTERN = 5
        const val MAX_PERIOD = 16
        const val MAX_LENGTH = 512
        const val MIN_RECTS = 2

        val NONE: PixelStrips = PixelStrips(IntArray(0), IntArray(0), IntArray(0), 0)

        fun of(rects: IntArray): PixelStrips {
            val count = rects.size / RECT_STRIDE
            if (count < MIN_RECTS) return NONE

            val order = (0 until count).sortedWith(
                compareBy<Int>({ rects[it * RECT_STRIDE + 1] }, { rects[it * RECT_STRIDE + 3] }, { rects[it * RECT_STRIDE] }),
            )
            val builder = Builder(rects, count)
            val run = IntList()
            var i = 0
            while (i < count) {
                run.clear()
                run.add(order[i])
                var j = i + 1
                while (j < count && continues(rects, order[j - 1], order[j])) {
                    run.add(order[j])
                    j++
                }
                if (run.size >= MIN_RECTS) {
                    builder.run(run)
                }
                i = j
            }
            return builder.build()
        }

        private fun continues(rects: IntArray, previous: Int, next: Int): Boolean {
            val p = previous * RECT_STRIDE
            val n = next * RECT_STRIDE
            return rects[n + 1] == rects[p + 1] && rects[n + 3] == rects[p + 3] && rects[n] == rects[p + 2]
        }
    }
}
