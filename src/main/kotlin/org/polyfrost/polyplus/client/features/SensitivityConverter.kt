package org.polyfrost.polyplus.client.features

import java.util.concurrent.TimeUnit
import kotlin.math.cbrt

object SensitivityConverter {
    const val DEFAULT_POINTER_SPEED = 10
    const val MAX_POINTER_SPEED = 20

    private val MULTIPLIERS = doubleArrayOf(
        1 / 32.0, 1 / 16.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0,
        1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5,
    )

    private val POINTER_SPEEDS = 1..MAX_POINTER_SPEED

    val isWindows = System.getProperty("os.name", "").startsWith("Windows")

    data class PointerSettings(val speed: Int, val enhancePrecision: Boolean)

    fun convert(percent: Double, pointerSpeed: Int): Double {
        val factor = percent / 200 * 0.6 + 0.2
        return (factor * cbrt(MULTIPLIERS[pointerSpeed.coerceIn(POINTER_SPEEDS) - 1]) - 0.2) / 0.6 * 200
    }

    fun windowsPointerSettings(): PointerSettings? {
        if (!isWindows) return null
        return runCatching {
            val process = ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Mouse")
                .redirectErrorStream(true)
                .start()
            process.outputStream.close()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            parsePointerSettings(process.inputStream.bufferedReader().readText())
        }.getOrNull()
    }

    internal fun parsePointerSettings(regOutput: String): PointerSettings? {
        fun value(name: String) = Regex("""$name\s+REG_SZ\s+(\d+)""").find(regOutput)?.groupValues?.get(1)?.toIntOrNull()
        val speed = value("MouseSensitivity") ?: return null
        return PointerSettings(speed.coerceIn(POINTER_SPEEDS), (value("MouseSpeed") ?: 0) != 0)
    }
}
