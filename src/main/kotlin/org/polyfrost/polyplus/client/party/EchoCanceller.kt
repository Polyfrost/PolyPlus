package org.polyfrost.polyplus.client.party

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class EchoCanceller(tailMillis: Int = 250, private val residualSuppression: Boolean = true) {
    private val tailSamples = tailMillis.coerceIn(MIN_TAIL_MILLIS, MAX_TAIL_MILLIS) * PartyAudio.SAMPLE_RATE / 1000
    private val partitions = (tailSamples + BLOCK - 1) / BLOCK
    private val holdSamples = tailSamples + PartyAudio.SAMPLE_RATE / 2
    private val holdNanos = holdSamples * 1_000_000_000L / PartyAudio.SAMPLE_RATE
    private val regularization = REGULARIZATION_LEVEL * FFT_SIZE * partitions
    private val maxBacklog = max(tailSamples, MAX_FRAME * 2)

    private val lock = Any()
    private val ring = FloatArray(RING_SIZE)
    private var readPos = 0L
    private var writeHead = 0L
    private var epoch = 0L
    private var windowMin = Long.MAX_VALUE
    private var windowFrames = 0
    private val cursors = ThreadLocal.withInitial { longArrayOf(0L, -1L) }

    @Volatile private var lastLoudRenderNanos = System.nanoTime() - holdNanos * 2
    @Volatile private var lastProcessNanos = System.nanoTime() - IDLE_NANOS * 2
    @Volatile private var resetPending = false
    @Volatile private var hold = 0

    private val fft = RealFft(FFT_SIZE)
    private val reference = FloatArray(MAX_FRAME)
    private val farTime = FloatArray(FFT_SIZE)
    private val work = FloatArray(FFT_SIZE)
    private val farRe = FloatArray(partitions * BINS)
    private val farIm = FloatArray(partitions * BINS)
    private val foreRe = FloatArray(partitions * BINS)
    private val foreIm = FloatArray(partitions * BINS)
    private val backRe = FloatArray(partitions * BINS)
    private val backIm = FloatArray(partitions * BINS)
    private val slotPower = FloatArray(partitions * BINS)
    private val tailPower = DoubleArray(BINS)
    private val foreAccRe = FloatArray(BINS)
    private val foreAccIm = FloatArray(BINS)
    private val backAccRe = FloatArray(BINS)
    private val backAccIm = FloatArray(BINS)
    private val errorRe = FloatArray(BINS)
    private val errorIm = FloatArray(BINS)
    private val foreError = FloatArray(BLOCK)
    private val backError = FloatArray(BLOCK)
    private val backEcho = FloatArray(BLOCK)
    private val echoRe = FloatArray(BINS)
    private val echoIm = FloatArray(BINS)
    private val errorPower = FloatArray(BINS)
    private val echoPower = FloatArray(BINS)
    private val errorMean = FloatArray(BINS)
    private val echoMean = FloatArray(BINS)

    private var head = 0
    private var nextConstraint = 0
    private var running = false
    private var micLevel = 0.0
    private var foreLevel = 0.0
    private var backLevel = 0.0
    private var copyStreak = 0
    private var revertStreak = 0
    private var harmStreak = 0
    private var leak = 1.0
    private var leakCovariance = LEAK_SEED
    private var leakVariance = LEAK_SEED
    private var gain = 1.0

    val active: Boolean
        get() = hold > 0 || System.nanoTime() - lastLoudRenderNanos < holdNanos

    fun render(reference: ShortArray, offset: Int = 0, length: Int = reference.size) {
        if (length <= 0 || offset < 0 || offset + length > reference.size) return
        var energy = 0.0
        for (i in offset until offset + length) {
            val sample = reference[i].toDouble()
            energy += sample * sample
        }
        val now = System.nanoTime()
        if (energy > LOUD_MEAN_SQUARE * length) lastLoudRenderNanos = now
        val cursor = cursors.get()
        synchronized(lock) {
            var start = offset
            var count = length
            if (count > maxBacklog) {
                start += count - maxBacklog
                count = maxBacklog
            }
            var position = if (cursor[1] == epoch && cursor[0] >= readPos) cursor[0] else readPos
            position += start - offset
            val end = position + count
            val limit = if (now - lastProcessNanos > IDLE_NANOS) PartyAudio.FRAME_SAMPLES.toLong() else maxBacklog.toLong()
            if (end - readPos > limit) discardUntil(end - limit)
            val skip = max(0L, readPos - position).toInt()
            for (i in skip until count) {
                val index = ((position + i) and RING_MASK).toInt()
                ring[index] += reference[start + i].toFloat()
            }
            if (end > writeHead) writeHead = end
            cursor[0] = end
            cursor[1] = epoch
        }
    }

    fun process(mic: ShortArray) {
        val size = mic.size
        if (size == 0 || size % BLOCK != 0 || size > MAX_FRAME) return
        if (resetPending) {
            resetPending = false
            clearFilters()
            clearHistory()
            hold = 0
        }
        val now = System.nanoTime()
        synchronized(lock) {
            val resumed = now - lastProcessNanos > IDLE_NANOS
            lastProcessNanos = now
            var available = writeHead - readPos
            if (resumed) {
                if (available > size) discardUntil(writeHead - size)
                windowMin = Long.MAX_VALUE
                windowFrames = 0
            } else {
                windowMin = min(windowMin, available)
                if (++windowFrames >= REGULATE_FRAMES) {
                    if (windowMin > size + REGULATE_SLACK) discardUntil(readPos + windowMin - size - REGULATE_SLACK / 2)
                    windowMin = Long.MAX_VALUE
                    windowFrames = 0
                }
            }
            available = writeHead - readPos
            val take = min(available, size.toLong()).toInt()
            for (i in 0 until take) {
                val index = ((readPos + i) and RING_MASK).toInt()
                reference[i] = ring[index]
                ring[index] = 0f
            }
            for (i in take until size) reference[i] = 0f
            readPos += take
        }

        var energy = 0.0
        for (i in 0 until size) energy += reference[i].toDouble() * reference[i]
        hold = if (energy > LOUD_MEAN_SQUARE * size) holdSamples else max(0, hold - size)
        if (hold == 0) {
            running = false
            return
        }
        if (!running) {
            clearHistory()
            running = true
        }
        var at = 0
        while (at < size) {
            processBlock(mic, at)
            at += BLOCK
        }
    }

    fun reset() {
        synchronized(lock) {
            ring.fill(0f)
            readPos = 0L
            writeHead = 0L
            epoch++
            windowMin = Long.MAX_VALUE
            windowFrames = 0
        }
        lastLoudRenderNanos = System.nanoTime() - holdNanos * 2
        resetPending = true
    }

    private fun discardUntil(target: Long) {
        if (target <= readPos) return
        val clearEnd = min(target, writeHead)
        var position = readPos
        while (position < clearEnd) {
            ring[(position and RING_MASK).toInt()] = 0f
            position++
        }
        readPos = target
        if (writeHead < readPos) writeHead = readPos
    }

    private fun processBlock(mic: ShortArray, at: Int) {
        System.arraycopy(farTime, BLOCK, farTime, 0, FFT_SIZE - BLOCK)
        var farEnergy = 0.0
        for (i in 0 until BLOCK) {
            val sample = reference[at + i]
            farTime[FFT_SIZE - BLOCK + i] = sample
            farEnergy += sample.toDouble() * sample
        }
        head = if (head + 1 == partitions) 0 else head + 1
        val headBase = head * BINS
        fft.forward(farTime, farRe, farIm, headBase)
        for (f in 0 until BINS) {
            val re = farRe[headBase + f]
            val im = farIm[headBase + f]
            val power = re * re + im * im
            tailPower[f] = max(0.0, tailPower[f] - slotPower[headBase + f] + power)
            slotPower[headBase + f] = power
        }

        filter()

        fft.inverse(foreAccRe, foreAccIm, 0, work)
        var micEnergy = 0.0
        var foreEnergy = 0.0
        var echoEnergy = 0.0
        for (i in 0 until BLOCK) {
            val sample = mic[at + i].toFloat()
            val echo = work[FFT_SIZE - BLOCK + i]
            foreError[i] = sample - echo
            micEnergy += sample.toDouble() * sample
            foreEnergy += foreError[i].toDouble() * foreError[i]
            echoEnergy += echo.toDouble() * echo
        }
        fft.inverse(backAccRe, backAccIm, 0, work)
        var backEnergy = 0.0
        for (i in 0 until BLOCK) {
            val echo = work[FFT_SIZE - BLOCK + i]
            val error = mic[at + i] - echo
            backEcho[i] = echo
            backError[i] = error
            backEnergy += error.toDouble() * error
        }

        if (!(foreEnergy + backEnergy + echoEnergy).isFinite()) {
            clearFilters()
            clearHistory()
            return
        }

        val farActive = farEnergy > LOUD_MEAN_SQUARE * BLOCK
        if (farActive) adapt()
        compareFilters(micEnergy, foreEnergy, backEnergy, farActive)

        val previousGain = gain
        if (residualSuppression) updateGain(foreEnergy, echoEnergy) else gain = 1.0
        for (i in 0 until BLOCK) {
            val blend = previousGain + (gain - previousGain) * (i + 1) / BLOCK
            val value = (foreError[i] * blend).roundToInt()
            mic[at + i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun filter() {
        foreAccRe.fill(0f)
        foreAccIm.fill(0f)
        backAccRe.fill(0f)
        backAccIm.fill(0f)
        for (k in 0 until partitions) {
            var slot = head - k
            if (slot < 0) slot += partitions
            val farBase = slot * BINS
            val weightBase = k * BINS
            for (f in 0 until BINS) {
                val xr = farRe[farBase + f]
                val xi = farIm[farBase + f]
                val fr = foreRe[weightBase + f]
                val fi = foreIm[weightBase + f]
                val br = backRe[weightBase + f]
                val bi = backIm[weightBase + f]
                foreAccRe[f] += xr * fr - xi * fi
                foreAccIm[f] += xr * fi + xi * fr
                backAccRe[f] += xr * br - xi * bi
                backAccIm[f] += xr * bi + xi * br
            }
        }
    }

    private fun adapt() {
        for (i in 0 until FFT_SIZE - BLOCK) work[i] = 0f
        System.arraycopy(backError, 0, work, FFT_SIZE - BLOCK, BLOCK)
        fft.forward(work, errorRe, errorIm, 0)
        System.arraycopy(backEcho, 0, work, FFT_SIZE - BLOCK, BLOCK)
        fft.forward(work, echoRe, echoIm, 0)
        updateLeak()
        for (f in 0 until BINS) {
            val scale = min(1.0, max(leak, leak * echoPower[f] / (errorPower[f] + SILENT_ENERGY)))
            val step = (STEP * scale / (tailPower[f] + regularization)).toFloat()
            errorRe[f] *= step
            errorIm[f] *= step
        }
        for (k in 0 until partitions) {
            var slot = head - k
            if (slot < 0) slot += partitions
            val farBase = slot * BINS
            val weightBase = k * BINS
            for (f in 0 until BINS) {
                val xr = farRe[farBase + f]
                val xi = farIm[farBase + f]
                val er = errorRe[f]
                val ei = errorIm[f]
                backRe[weightBase + f] += xr * er + xi * ei
                backIm[weightBase + f] += xr * ei - xi * er
            }
        }
        repeat(CONSTRAINTS_PER_BLOCK) {
            val base = nextConstraint * BINS
            fft.inverse(backRe, backIm, base, work)
            for (i in BLOCK until FFT_SIZE) work[i] = 0f
            fft.forward(work, backRe, backIm, base)
            nextConstraint = if (nextConstraint + 1 == partitions) 0 else nextConstraint + 1
        }
    }

    private fun updateLeak() {
        var covariance = 0.0
        var variance = 0.0
        var errorTotal = 0.0
        var echoTotal = 0.0
        for (f in 0 until BINS) {
            val error = errorRe[f] * errorRe[f] + errorIm[f] * errorIm[f]
            val echo = echoRe[f] * echoRe[f] + echoIm[f] * echoIm[f]
            errorPower[f] += SPECTRUM_SMOOTHING * (error - errorPower[f])
            echoPower[f] += SPECTRUM_SMOOTHING * (echo - echoPower[f])
            val errorDeviation = (error - errorMean[f]).toDouble()
            val echoDeviation = (echo - echoMean[f]).toDouble()
            errorMean[f] += MEAN_SMOOTHING * (error - errorMean[f])
            echoMean[f] += MEAN_SMOOTHING * (echo - echoMean[f])
            covariance += errorDeviation * echoDeviation
            variance += echoDeviation * echoDeviation
            errorTotal += error
            echoTotal += echo
        }
        val norm = echoTotal * echoTotal + SILENT_ENERGY
        val rate = LEAK_RATE * min(1.0, echoTotal / (errorTotal + SILENT_ENERGY))
        leakCovariance += rate * (covariance / norm - leakCovariance)
        leakVariance += rate * (variance / norm - leakVariance)
        val estimate = LEAK_BOOST * leakCovariance / leakVariance
        if (estimate.isFinite()) {
            leak = estimate.coerceIn(LEAK_MIN, 1.0)
        } else {
            leak = 1.0
            leakCovariance = LEAK_SEED
            leakVariance = LEAK_SEED
        }
    }

    private fun compareFilters(micEnergy: Double, foreEnergy: Double, backEnergy: Double, farActive: Boolean) {
        micLevel = LEVEL_SMOOTHING * micLevel + (1 - LEVEL_SMOOTHING) * micEnergy
        foreLevel = LEVEL_SMOOTHING * foreLevel + (1 - LEVEL_SMOOTHING) * foreEnergy
        backLevel = LEVEL_SMOOTHING * backLevel + (1 - LEVEL_SMOOTHING) * backEnergy
        if (!farActive) {
            copyStreak = 0
            revertStreak = 0
            return
        }
        copyStreak = if (backLevel < COPY_RATIO * foreLevel && backLevel < micLevel) copyStreak + 1 else 0
        if (copyStreak >= COPY_BLOCKS) {
            System.arraycopy(backRe, 0, foreRe, 0, backRe.size)
            System.arraycopy(backIm, 0, foreIm, 0, backIm.size)
            foreLevel = backLevel
            copyStreak = 0
        }
        revertStreak = if (backLevel > REVERT_RATIO * foreLevel && foreLevel < micLevel) revertStreak + 1 else 0
        if (revertStreak >= REVERT_BLOCKS) {
            System.arraycopy(foreRe, 0, backRe, 0, foreRe.size)
            System.arraycopy(foreIm, 0, backIm, 0, foreIm.size)
            backLevel = foreLevel
            revertStreak = 0
        }
        harmStreak = if (foreLevel > HARM_RATIO * micLevel) harmStreak + 1 else 0
        if (harmStreak >= HARM_BLOCKS) {
            foreRe.fill(0f)
            foreIm.fill(0f)
            foreLevel = micLevel
            if (backLevel > HARM_RATIO * micLevel) {
                backRe.fill(0f)
                backIm.fill(0f)
                backLevel = micLevel
            }
            harmStreak = 0
        }
    }

    private fun updateGain(errorEnergy: Double, echoEnergy: Double) {
        val residual = leak * echoEnergy
        val raw = 1.0 - OVER_SUBTRACTION * residual / (errorEnergy + SILENT_ENERGY)
        val target = if (raw.isFinite()) raw.coerceIn(GAIN_FLOOR, 1.0) else 1.0
        gain += (if (target > gain) GAIN_RELEASE else GAIN_ATTACK) * (target - gain)
    }

    private fun clearHistory() {
        farTime.fill(0f)
        farRe.fill(0f)
        farIm.fill(0f)
        slotPower.fill(0f)
        tailPower.fill(0.0)
        micLevel = 0.0
        foreLevel = 0.0
        backLevel = 0.0
        copyStreak = 0
        revertStreak = 0
        harmStreak = 0
        gain = 1.0
    }

    private fun clearFilters() {
        foreRe.fill(0f)
        foreIm.fill(0f)
        backRe.fill(0f)
        backIm.fill(0f)
        leak = 1.0
        leakCovariance = LEAK_SEED
        leakVariance = LEAK_SEED
        errorPower.fill(0f)
        echoPower.fill(0f)
        errorMean.fill(0f)
        echoMean.fill(0f)
    }

    private companion object {
        const val BLOCK = 240
        const val FFT_SIZE = 512
        const val BINS = FFT_SIZE / 2 + 1
        const val MAX_FRAME = 4800
        const val MIN_TAIL_MILLIS = 20
        const val MAX_TAIL_MILLIS = 600
        const val RING_SIZE = 1 shl 16
        const val RING_MASK = (RING_SIZE - 1).toLong()
        const val IDLE_NANOS = 100_000_000L
        const val REGULATE_FRAMES = 100
        const val REGULATE_SLACK = 480
        const val LOUD_MEAN_SQUARE = 16.0
        const val SILENT_ENERGY = 1.0
        const val STEP = 1.0
        const val REGULARIZATION_LEVEL = 1000.0
        const val CONSTRAINTS_PER_BLOCK = 2
        const val LEVEL_SMOOTHING = 0.85
        const val COPY_RATIO = 0.85
        const val COPY_BLOCKS = 3
        const val REVERT_RATIO = 4.0
        const val REVERT_BLOCKS = 8
        const val HARM_RATIO = 4.0
        const val HARM_BLOCKS = 50
        const val LEAK_MIN = 1e-4
        const val LEAK_BOOST = 4.0
        const val LEAK_SEED = 1e-3
        const val LEAK_RATE = 0.05
        const val SPECTRUM_SMOOTHING = 0.3f
        const val MEAN_SMOOTHING = 0.1f
        const val OVER_SUBTRACTION = 2.0
        const val GAIN_FLOOR = 0.25
        const val GAIN_ATTACK = 0.2
        const val GAIN_RELEASE = 0.6
    }
}

internal class RealFft(val size: Int) {
    private val half = size / 2
    private val reversed = IntArray(half)
    private val twiddleCos = FloatArray(half / 2)
    private val twiddleSin = FloatArray(half / 2)
    private val splitCos = FloatArray(half)
    private val splitSin = FloatArray(half)
    private val re = FloatArray(half)
    private val im = FloatArray(half)

    init {
        require(size >= 4 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        var bits = 0
        while (1 shl bits < half) bits++
        for (i in 0 until half) reversed[i] = Integer.reverse(i) ushr (32 - bits)
        for (i in 0 until half / 2) {
            val angle = 2.0 * PI * i / half
            twiddleCos[i] = cos(angle).toFloat()
            twiddleSin[i] = sin(angle).toFloat()
        }
        for (k in 0 until half) {
            val angle = 2.0 * PI * k / size
            splitCos[k] = cos(angle).toFloat()
            splitSin[k] = sin(angle).toFloat()
        }
    }

    fun forward(input: FloatArray, outRe: FloatArray, outIm: FloatArray, outOffset: Int) {
        for (n in 0 until half) {
            re[n] = input[2 * n]
            im[n] = input[2 * n + 1]
        }
        transform(-1f)
        outRe[outOffset] = re[0] + im[0]
        outIm[outOffset] = 0f
        outRe[outOffset + half] = re[0] - im[0]
        outIm[outOffset + half] = 0f
        for (k in 1 until half) {
            val j = half - k
            val evenRe = 0.5f * (re[k] + re[j])
            val evenIm = 0.5f * (im[k] - im[j])
            val oddRe = 0.5f * (im[k] + im[j])
            val oddIm = -0.5f * (re[k] - re[j])
            val c = splitCos[k]
            val s = splitSin[k]
            outRe[outOffset + k] = evenRe + c * oddRe + s * oddIm
            outIm[outOffset + k] = evenIm + c * oddIm - s * oddRe
        }
    }

    fun inverse(inRe: FloatArray, inIm: FloatArray, inOffset: Int, output: FloatArray) {
        for (k in 0 until half) {
            val j = half - k
            val ar = inRe[inOffset + k]
            val ai = inIm[inOffset + k]
            val br = inRe[inOffset + j]
            val bi = -inIm[inOffset + j]
            val evenRe = 0.5f * (ar + br)
            val evenIm = 0.5f * (ai + bi)
            val diffRe = 0.5f * (ar - br)
            val diffIm = 0.5f * (ai - bi)
            val c = splitCos[k]
            val s = splitSin[k]
            val oddRe = diffRe * c - diffIm * s
            val oddIm = diffRe * s + diffIm * c
            re[k] = evenRe - oddIm
            im[k] = evenIm + oddRe
        }
        transform(1f)
        val scale = 1f / half
        for (n in 0 until half) {
            output[2 * n] = re[n] * scale
            output[2 * n + 1] = im[n] * scale
        }
    }

    private fun transform(sign: Float) {
        for (i in 0 until half) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var length = 2
        while (length <= half) {
            val halfLength = length / 2
            val stride = half / length
            var start = 0
            while (start < half) {
                var t = 0
                for (i in 0 until halfLength) {
                    val c = twiddleCos[t]
                    val s = sign * twiddleSin[t]
                    val a = start + i
                    val b = a + halfLength
                    val xr = re[b] * c - im[b] * s
                    val xi = re[b] * s + im[b] * c
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    t += stride
                }
                start += length
            }
            length *= 2
        }
    }
}

