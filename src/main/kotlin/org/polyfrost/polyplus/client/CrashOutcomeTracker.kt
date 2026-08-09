package org.polyfrost.polyplus.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Outcome is read off the client because CrashPatch and NotEnoughCrashes recover further up the
// stack Still ticking means recovered shutdown or silence means fatal
object CrashOutcomeTracker {
    private const val SURVIVAL_TICKS = 40L

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

    fun installHeartbeat() {
        if (!heartbeatInstalled.compareAndSet(false, true)) return
        //? if fabric {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { ticks.incrementAndGet() })
        //?}
    }

    // Resolves on a watcher thread or on the shutdown hook if the game is already dying
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
