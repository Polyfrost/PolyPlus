package org.polyfrost.polyplus.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Decides whether a crash report the game built was fatal or merely caught.
 *
 * The answer is not knowable where the report is created: CrashPatch recovers through
 * NotEnoughCrashes, which only steps in further up the stack, inside `Minecraft.run`'s catch blocks.
 * So the outcome is read off the client instead. If the game loop keeps ticking after the report was
 * built, something recovered; if the JVM starts shutting down or the client goes quiet, the game
 * died with it.
 */
object CrashOutcomeTracker {
    /** Client ticks that must elapse after a report before the session counts as recovered. */
    private const val SURVIVAL_TICKS = 40L

    /** How long a report may stay unresolved before it is assumed to have taken the game down. */
    private const val RESOLVE_TIMEOUT_MS = 15_000L

    private const val POLL_INTERVAL_MS = 250L

    private val ticks = AtomicLong(0)
    private val shuttingDown = AtomicBoolean(false)
    private val heartbeatInstalled = AtomicBoolean(false)
    private val shutdownHookInstalled = AtomicBoolean(false)
    private val pollerStarted = AtomicBoolean(false)

    private val pending = ConcurrentLinkedQueue<Pending>()

    private val watcher = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PolyPlus Crash Outcome").apply { isDaemon = true }
    }

    /** The recovery mods present, for triage. Both funnel through NotEnoughCrashes' catcher. */
    val handlerMods: String by lazy {
        val loader = FabricLoader.getInstance()
        listOf("crashpatch", "notenoughcrashes")
            .filter(loader::isModLoaded)
            .joinToString(",")
            .ifEmpty { "none" }
    }

    private class Pending(val ticksAtCrash: Long, val deadline: Long, val resolve: (CrashKind) -> Unit) {
        private val resolved = AtomicBoolean(false)

        fun complete(kind: CrashKind) {
            if (resolved.compareAndSet(false, true)) runCatching { resolve(kind) }
        }
    }

    /** Starts the tick heartbeat that tells a recovered crash apart from a fatal one. */
    fun installHeartbeat() {
        if (!heartbeatInstalled.compareAndSet(false, true)) return
        //? if fabric {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { ticks.incrementAndGet() })
        //?}
    }

    /**
     * Registers [resolve] to be called once with the outcome of a crash report built on the calling
     * thread. It runs on a watcher thread, or on the shutdown hook if the game is already dying.
     */
    fun track(resolve: (CrashKind) -> Unit) {
        installShutdownHook()
        if (shuttingDown.get()) {
            runCatching { resolve(CrashKind.HARD_CRASH) }
            return
        }
        pending += Pending(ticks.get(), System.currentTimeMillis() + RESOLVE_TIMEOUT_MS, resolve)
        startPoller()
    }

    private fun startPoller() {
        if (!pollerStarted.compareAndSet(false, true)) return
        watcher.scheduleWithFixedDelay(::poll, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun poll() {
        val now = System.currentTimeMillis()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val kind = when {
                shuttingDown.get() -> CrashKind.HARD_CRASH
                ticks.get() - entry.ticksAtCrash >= SURVIVAL_TICKS -> CrashKind.CAUGHT_CRASH
                now >= entry.deadline -> CrashKind.HARD_CRASH
                else -> continue
            }
            iterator.remove()
            entry.complete(kind)
        }
    }

    private fun installShutdownHook() {
        if (!shutdownHookInstalled.compareAndSet(false, true)) return
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread({
                    shuttingDown.set(true)
                    while (true) {
                        val entry = pending.poll() ?: break
                        entry.complete(CrashKind.HARD_CRASH)
                    }
                }, "PolyPlus Crash Outcome Shutdown"),
            )
        }
    }
}
