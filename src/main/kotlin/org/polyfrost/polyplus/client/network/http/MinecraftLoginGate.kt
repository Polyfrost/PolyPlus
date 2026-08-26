package org.polyfrost.polyplus.client.network.http

import kotlinx.coroutines.delay
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object MinecraftLoginGate {
    private const val MAX_HOLD_MS = 20_000L

    private const val LOGIN_WAIT_MS = 10_000L

    private const val POLL_MS = 25L

    private val gate = Semaphore(1, true)

    private val loginDepth = ThreadLocal.withInitial { 0 }

    private val awaitingOutcome = AtomicBoolean(false)

    @Volatile
    private var holdUntilMs = 0L

    fun register() {
        ClientConfigurationConnectionEvents.INIT.register { _, _ -> loginSettled() }
        ClientLoginConnectionEvents.DISCONNECT.register { _, _ -> loginSettled() }
    }

    @JvmStatic
    fun begin(): Boolean {
        val depth = loginDepth.get()
        if (depth > 0) {
            loginDepth.set(depth + 1)
            return true
        }
        val held = gate.tryAcquire(LOGIN_WAIT_MS, TimeUnit.MILLISECONDS)
        if (held) loginDepth.set(1)
        return held
    }

    @JvmStatic
    fun end(held: Boolean) {
        if (!held) return
        val depth = loginDepth.get() - 1
        if (depth > 0) {
            loginDepth.set(depth)
            return
        }
        loginDepth.remove()
        holdUntilMs = System.currentTimeMillis() + MAX_HOLD_MS
        awaitingOutcome.set(true)
    }

    internal fun loginSettled() {
        if (awaitingOutcome.compareAndSet(true, false)) gate.release()
    }

    suspend fun <T> whileNotLoggingIn(timeoutMs: Long, block: suspend () -> T): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (gate.tryAcquire(0, TimeUnit.MILLISECONDS)) {
                try {
                    return block()
                } finally {
                    gate.release()
                }
            }
            if (awaitingOutcome.get() && System.currentTimeMillis() >= holdUntilMs) loginSettled()
            delay(POLL_MS)
        }
        return block()
    }
}
