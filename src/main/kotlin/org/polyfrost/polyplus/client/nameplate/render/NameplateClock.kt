package org.polyfrost.polyplus.client.nameplate.render

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.nameplate.engine.api.shader.FrameClock
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}

object NameplateClock {
    private const val NANOS_PER_TICK = 50_000_000L

    private val world = FrameClock()
    private val menu = FrameClock()

    fun frame(nameplate: BoundNameplate): Int {
        val bundle = nameplate.bundle
        if (NameplateBinding.settings.motion == NameplateMotion.OFF) {
            return if (bundle.perPlayerPhase) -nameplate.seed.mod(bundle.loop) else 0
        }
        val minecraft = Minecraft.getInstance()
        //? if >= 1.21.1 {
        val level = minecraft.level
        //?} else {
        /*val level = minecraft.world
        *///?}
        if (level == null) {
            val nanos = System.nanoTime()
            return menu.frame(nanos.floorDiv(NANOS_PER_TICK), nanos.mod(NANOS_PER_TICK).toDouble() / NANOS_PER_TICK, bundle.fps, bundle.loop)
        }
        //? if >= 1.21.4 {
        val partial = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        //?} elif >= 1.21.1 {
        /*val partial = minecraft.timer.getGameTimeDeltaPartialTick(false)
        *///?} else {
        /*val partial = (minecraft as MinecraftAccessor).`polyplus$nameplateTimer`().partialTick
        *///?}
        //? if >= 1.21.1 {
        return world.frame(level.gameTime, partial.toDouble(), bundle.fps, bundle.loop)
        //?} else {
        /*return world.frame(level.time, partial.toDouble(), bundle.fps, bundle.loop)
        *///?}
    }
}
