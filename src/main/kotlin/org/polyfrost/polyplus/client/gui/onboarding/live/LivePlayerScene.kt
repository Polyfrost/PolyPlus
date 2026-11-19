package org.polyfrost.polyplus.client.gui.onboarding.live

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

internal object LivePlayerScene {
    private val actors = LinkedHashSet<LivePlayerActor>()
    private val starts = HashMap<LiveTweak, Long>()
    private var running = false

    var ticks = 0L
        private set

    //? if = 1.8.9 {
    /*private var tickNanos = 0L
    *///?}

    fun groupStart(tweak: LiveTweak): Long = starts[tweak] ?: ticks

    fun attach(actor: LivePlayerActor) {
        if (actors.none { it.tweak == actor.tweak }) starts[actor.tweak] = ticks
        actors += actor
        if (running) return
        running = true
        eventHandler<TickEvent.End> {
            if (actors.isEmpty()) {
                running = false
                LivePlayerAvatars.release()
                return@eventHandler true
            }
            ticks++
            //? if = 1.8.9 {
            /*tickNanos = System.nanoTime()
            *///?}
            actors.toList().forEach { it.tick() }
            false
        }
    }

    fun detach(actor: LivePlayerActor) {
        actors -= actor
        actor.release()
    }

    //? if >= 1.21.4 {
    fun partial(): Float = runCatching { Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false) }.getOrDefault(0f)
    //?} elif > 1.8.9 {
    /*fun partial(): Float = runCatching { Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false) }.getOrDefault(0f)
    *///?} else {
    /*fun partial(): Float = ((System.nanoTime() - tickNanos) / NANOS_PER_TICK).toFloat().coerceIn(0f, 1f)

    private const val NANOS_PER_TICK = 50_000_000.0
    *///?}
}
