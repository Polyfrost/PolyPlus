package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft

internal object PetClock {
    private var ticks = 0L

    fun tick() {
        val mc = Minecraft.getInstance()
        if (mc.isPaused) return
        //? if > 1.8.9 {
        val level = mc.level
        if (level != null && !level.tickRateManager().runsNormally()) return
        //?}
        ticks++
    }

    fun seconds(partialTick: Float): Double = (ticks + partialTick.coerceIn(0f, 1f).toDouble()) * PetSimulation.TICK_SECONDS
}
