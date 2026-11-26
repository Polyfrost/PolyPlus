package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.party.EchoCanceller
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.RealFft
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class EchoCancellerTest {
    private val rate = PartyAudio.SAMPLE_RATE
    private val frame = PartyAudio.FRAME_SAMPLES

    private fun whiteNoise(samples: Int, rms: Double, seed: Long): DoubleArray {
        val random = Random(seed)
        return DoubleArray(samples) { random.nextGaussian() * rms }
    }

    private fun speechLike(samples: Int, rms: Double, seed: Long): DoubleArray {
        val random = Random(seed)
        val out = DoubleArray(samples)
        var a = 0.0
        var b = 0.0
        for (n in 0 until samples) {
            val t = n.toDouble() / rate
            val envelope = 0.15 + 0.85 * sin(2 * PI * 3.7 * t).let { it * it } * (0.6 + 0.4 * sin(2 * PI * 0.7 * t))
            val excitation = random.nextGaussian() + 0.8 * sin(2 * PI * (140 + 30 * sin(2 * PI * 0.5 * t)) * t)
            val next = excitation + 1.3 * a - 0.6 * b
            b = a
            a = next
            out[n] = next * envelope
        }
        val scale = rms / rms(out, 0, samples)
        for (n in 0 until samples) out[n] *= scale
        return out
    }

    private fun roomResponse(delaySamples: Int, lengthSamples: Int, gain: Double, seed: Long): DoubleArray {
        val random = Random(seed)
        val response = DoubleArray(delaySamples + lengthSamples)
        var energy = 0.0
        for (t in 0 until lengthSamples) {
            val tap = random.nextGaussian() * exp(-t / (lengthSamples / 6.0))
            response[delaySamples + t] = tap
            energy += tap * tap
        }
        val scale = gain / sqrt(energy)
        for (t in response.indices) response[t] *= scale
        return response
    }

    private fun convolve(signal: DoubleArray, response: DoubleArray): DoubleArray {
        val first = response.indexOfFirst { it != 0.0 }
        val out = DoubleArray(signal.size)
        for (n in signal.indices) {
            var sum = 0.0
            var t = first
            while (t < response.size && t <= n) {
                sum += response[t] * signal[n - t]
                t++
            }
            out[n] = sum
        }
        return out
    }

    private fun rms(signal: DoubleArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (n in from until to) sum += signal[n] * signal[n]
        return sqrt(sum / (to - from))
    }

    private fun toShort(value: Double): Short = value.toInt().coerceIn(-32768, 32767).toShort()

    private fun run(canceller: EchoCanceller, far: DoubleArray, mic: DoubleArray, chunk: Int = frame, lead: Int = 0): DoubleArray {
        val out = DoubleArray(mic.size)
        val farShorts = ShortArray(far.size) { toShort(far[it]) }
        val micFrame = ShortArray(frame)
        var rendered = 0
        fun renderUntil(end: Int) {
            while (rendered < min(end, far.size)) {
                val length = min(chunk, min(end, far.size) - rendered)
                canceller.render(farShorts, rendered, length)
                rendered += length
            }
        }
        var at = 0
        while (at + frame <= mic.size) {
            renderUntil(at + frame * (1 + lead))
            for (i in 0 until frame) micFrame[i] = toShort(mic[at + i])
            canceller.process(micFrame)
            for (i in 0 until frame) out[at + i] = micFrame[i].toDouble()
            at += frame
        }
        return out
    }

    private fun erle(echo: DoubleArray, out: DoubleArray, from: Int, to: Int): Double =
        20 * log10(rms(echo, from, to) / rms(out, from, to))

    private fun tonePower(signal: DoubleArray, frequency: Double, from: Int, to: Int): Double {
        var re = 0.0
        var im = 0.0
        for (n in from until to) {
            val phase = 2 * PI * frequency * n / rate
            re += signal[n] * cos(phase)
            im += signal[n] * sin(phase)
        }
        return (re * re + im * im) / (to - from)
    }

    private fun echoScenario(far: DoubleArray, noiseSeed: Long): Pair<DoubleArray, DoubleArray> {
        val echo = convolve(far, roomResponse(rate * 80 / 1000, rate * 60 / 1000, 0.5, 11))
        val noise = whiteNoise(far.size, 5.0, noiseSeed)
        return echo to DoubleArray(far.size) { echo[it] + noise[it] }
    }

    @Test
    fun `fft matches a direct transform and round-trips`() {
        val size = 64
        val fft = RealFft(size)
        val random = Random(3)
        val input = FloatArray(size) { random.nextFloat() * 2 - 1 }
        val re = FloatArray(size / 2 + 1)
        val im = FloatArray(size / 2 + 1)
        fft.forward(input, re, im, 0)
        for (k in 0..size / 2) {
            var expectedRe = 0.0
            var expectedIm = 0.0
            for (n in 0 until size) {
                expectedRe += input[n] * cos(2 * PI * k * n / size)
                expectedIm -= input[n] * sin(2 * PI * k * n / size)
            }
            assertEquals(expectedRe, re[k].toDouble(), 1e-3)
            assertEquals(expectedIm, im[k].toDouble(), 1e-3)
        }
        val back = FloatArray(size)
        fft.inverse(re, im, 0, back)
        for (n in 0 until size) assertEquals(input[n].toDouble(), back[n].toDouble(), 1e-4)
    }

    @Test
    fun `cancels echo of noise after convergence`() {
        val far = whiteNoise(rate * 4, 3000.0, 1)
        val (echo, mic) = echoScenario(far, 2)
        val out = run(EchoCanceller(), far, mic)
        val enhancement = erle(echo, out, rate * 3, rate * 4)
        println("noise ERLE: %.1f dB".format(enhancement))
        assertTrue(enhancement >= 15.0, "ERLE was %.1f dB".format(enhancement))
    }

    @Test
    fun `cancels echo of speech-like audio after convergence`() {
        val far = speechLike(rate * 4, 3000.0, 4)
        val (echo, mic) = echoScenario(far, 5)
        val out = run(EchoCanceller(), far, mic)
        val enhancement = erle(echo, out, rate * 3, rate * 4)
        println("speech-like ERLE: %.1f dB".format(enhancement))
        assertTrue(enhancement >= 15.0, "ERLE was %.1f dB".format(enhancement))
    }

    @Test
    fun `cancels echo when the reference arrives early in uneven chunks`() {
        val far = whiteNoise(rate * 5, 3000.0, 15)
        val (echo, mic) = echoScenario(far, 16)
        val out = run(EchoCanceller(), far, mic, chunk = 441, lead = 2)
        val enhancement = erle(echo, out, rate * 4, rate * 5)
        println("uneven early reference ERLE: %.1f dB".format(enhancement))
        assertTrue(enhancement >= 15.0, "ERLE was %.1f dB".format(enhancement))
    }

    @Test
    fun `near-end talk survives double talk and the filter holds`() {
        val far = whiteNoise(rate * 6, 3000.0, 6)
        val (echo, mic) = echoScenario(far, 7)
        val toneFrequency = 440.0
        val near = DoubleArray(far.size) { n -> if (n in rate * 3 until rate * 5) 1500 * sin(2 * PI * toneFrequency * n / rate) else 0.0 }
        for (n in mic.indices) mic[n] += near[n]
        val out = run(EchoCanceller(), far, mic)

        val from = rate * 3 + rate / 2
        val to = rate * 5
        val kept = 10 * log10(tonePower(out, toneFrequency, from, to) / tonePower(near, toneFrequency, from, to))
        println("near-end tone level: %.2f dB".format(kept))
        assertTrue(abs(kept) <= 3.0, "near-end tone changed by %.2f dB".format(kept))

        val after = erle(echo, out, rate * 5 + rate / 4, rate * 6)
        println("ERLE after double talk: %.1f dB".format(after))
        assertTrue(after >= 15.0, "ERLE after double talk was %.1f dB".format(after))
    }

    @Test
    fun `silent far end leaves the mic untouched`() {
        val canceller = EchoCanceller()
        val random = Random(8)
        val silence = ShortArray(frame)
        repeat(100) { index ->
            val mic = ShortArray(frame) { (random.nextGaussian() * 2000).toInt().toShort() }
            val original = mic.copyOf()
            if (index % 2 == 0) canceller.render(silence)
            canceller.process(mic)
            assertArrayEquals(original, mic)
        }
        assertFalse(canceller.active)
    }

    @Test
    fun `random input never breaks the canceller`() {
        val canceller = EchoCanceller(120)
        val random = Random(9)
        val far = ShortArray(frame * 3)
        val mic = ShortArray(frame)
        repeat(3000) { index ->
            val mode = random.nextInt(6)
            for (i in far.indices) {
                far[i] = when (mode) {
                    0 -> 0
                    1 -> if (random.nextBoolean()) Short.MAX_VALUE else Short.MIN_VALUE
                    else -> (random.nextGaussian() * 8000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            val chunks = random.nextInt(4)
            repeat(chunks) {
                val length = 1 + random.nextInt(far.size)
                canceller.render(far, random.nextInt(far.size - length + 1), length)
            }
            for (i in mic.indices) {
                mic[i] = when (random.nextInt(4)) {
                    0 -> Short.MAX_VALUE
                    1 -> Short.MIN_VALUE
                    else -> (random.nextGaussian() * 12000).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            canceller.process(mic)
            if (index == 1500) canceller.reset()
        }
        val farClean = whiteNoise(rate * 3, 3000.0, 10)
        val (echo, micClean) = echoScenario(farClean, 12)
        val out = run(canceller, farClean, micClean)
        assertTrue(out.all { it.isFinite() })
        val enhancement = erle(echo, out, rate * 2, rate * 3)
        println("ERLE after abuse: %.1f dB".format(enhancement))
        assertTrue(enhancement >= 10.0, "ERLE after abuse was %.1f dB".format(enhancement))
    }

    @Test
    fun `concurrent render threads and capture stay safe`() {
        val canceller = EchoCanceller()
        val failures = AtomicInteger()
        val stop = AtomicBoolean()
        val renderers = List(2) { seed ->
            Thread {
                val random = Random(20L + seed)
                val chunk = ShortArray(frame)
                runCatching {
                    while (!stop.get()) {
                        for (i in chunk.indices) chunk[i] = (random.nextGaussian() * 6000).toInt().coerceIn(-32768, 32767).toShort()
                        canceller.render(chunk, 0, 1 + random.nextInt(frame))
                        Thread.yield()
                    }
                }.onFailure { failures.incrementAndGet() }
            }.apply { start() }
        }
        val random = Random(30)
        val mic = ShortArray(frame)
        repeat(1500) { index ->
            for (i in mic.indices) mic[i] = (random.nextGaussian() * 4000).toInt().coerceIn(-32768, 32767).toShort()
            canceller.process(mic)
            if (index % 500 == 499) canceller.reset()
        }
        stop.set(true)
        renderers.forEach { it.join(5000) }
        assertEquals(0, failures.get())
    }

    @Test
    fun `processing stays fast`() {
        val far = whiteNoise(rate * 4, 3000.0, 13)
        val (_, mic) = echoScenario(far, 14)
        val canceller = EchoCanceller()
        run(canceller, far, mic)
        val farFrame = ShortArray(frame)
        val micFrame = ShortArray(frame)
        val frames = 1000
        var total = 0L
        for (n in 0 until frames) {
            val at = (n % (far.size / frame)) * frame
            for (i in 0 until frame) {
                farFrame[i] = toShort(far[at + i])
                micFrame[i] = toShort(mic[at + i])
            }
            val start = System.nanoTime()
            canceller.render(farFrame)
            canceller.process(micFrame)
            total += System.nanoTime() - start
        }
        val perFrame = total / frames / 1000.0
        println("per-frame time: %.1f us".format(perFrame))
        assertTrue(perFrame < 5000.0)
    }
}
