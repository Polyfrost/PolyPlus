package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.LightLayer
import kotlin.math.abs

data class LobbyMood(
    val clock: Long,
    val night: Float,
    val golden: Float,
    val aurora: Float,
    val exposure: Float,
    val snow: Boolean,
    val lanterns: Int,
) {
    val dark: Boolean get() = night > LANTERN_NIGHT

    companion object {
        private const val DAY_TICKS = 24000L
        private const val SUNSET = 12800f
        private const val SUNRISE = 23200f
        private const val TWILIGHT = 900f
        private const val GOLDEN_SPAN = 2600f
        private const val COLD_MAX = 0.3f
        private const val COLD_NIGHT_EXPOSURE = 1.08f
        private const val MILD_NIGHT_EXPOSURE = 1.8f
        private const val COLD_DAY_EXPOSURE = 1.3f
        private const val DAY_EXPOSURE = 1.75f
        private const val MAX_LANTERNS = 6
        private const val LIT_RADIUS = 14
        private const val LIT_SEEN = 2500
        private const val LANTERN_NIGHT = 0.5f

        val NEUTRAL = LobbyMood(6000L, 0f, 0f, 0f, DAY_EXPOSURE, false, 0)

        fun of(level: ClientLevel, aim: BlockPos, capturedClock: Long): LobbyMood {
            val clock = Math.floorMod(capturedClock, DAY_TICKS)
            val night = nightAmount(clock.toFloat())
            val biome = level.getBiome(aim).value()
            val cold = 1f - Mth.clamp(biome.baseTemperature / COLD_MAX, 0f, 1f)
            val lanterns = if (night > LANTERN_NIGHT) (MAX_LANTERNS - litCells(level, aim) / LIT_SEEN).coerceIn(0, MAX_LANTERNS) else 0
            return LobbyMood(
                clock,
                night,
                goldenAmount(clock.toFloat()),
                cold * night,
                Mth.lerp(night, Mth.lerp(cold, DAY_EXPOSURE, COLD_DAY_EXPOSURE), Mth.lerp(cold, MILD_NIGHT_EXPOSURE, COLD_NIGHT_EXPOSURE)),
                //? if >= 1.21.4 {
                biome.coldEnoughToSnow(aim, level.seaLevel),
                //?} else {
                /*biome.coldEnoughToSnow(aim),
                *///?}
                lanterns,
            )
        }

        private fun nightAmount(clock: Float): Float {
            val intoNight = Mth.clamp((clock - SUNSET) / TWILIGHT, 0f, 1f)
            val intoDay = Mth.clamp((clock - (SUNRISE - TWILIGHT)) / TWILIGHT, 0f, 1f)
            return intoNight * (1f - intoDay)
        }

        private fun goldenAmount(clock: Float): Float {
            val toSunset = abs(clock - SUNSET)
            val toSunrise = minOf(abs(clock - SUNRISE), abs(clock + DAY_TICKS - SUNRISE))
            return (1f - minOf(toSunset, toSunrise) / GOLDEN_SPAN).coerceAtLeast(0f)
        }

        private fun litCells(level: ClientLevel, aim: BlockPos): Int {
            var lit = 0
            for (pos in LobbyCompat.between(aim.offset(-LIT_RADIUS, -6, -LIT_RADIUS), aim.offset(LIT_RADIUS, 6, LIT_RADIUS))) {
                if (level.getBrightness(LightLayer.BLOCK, pos) >= 8) lit++
            }
            return lit
        }
    }
}
