package org.polyfrost.polyplus.client.network.http

import kotlinx.coroutines.delay
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.mixin.client.access.ClientCommonPacketListenerAccessor
import org.polyfrost.polyplus.mixin.client.access.ClientHandshakePacketListenerAccessor
import java.util.IdentityHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object MinecraftLoginGate {
    private val LOGGER = LogManager.getLogger("PolyPlus/LoginGate")

    internal const val MAX_HOLD_MS = 20_000L

    private const val LOGIN_WAIT_MS = 500L

    private const val POLL_MS = 25L

    private val gate = Semaphore(1, true)

    private val loginDepths = IdentityHashMap<Any, Int>()

    private val loginsInFlight = IdentityHashMap<Any, Unit>()

    private val failOpen = AtomicInteger(0)

    private val outcomeLock = Any()

    private data class PendingOutcome(var deadlineMs: Long)

    private val pendingOutcomes = IdentityHashMap<Any, PendingOutcome>()

    private val settledBeforePending = IdentityHashMap<Any, Long>()

    private var heldGateAwaitingOutcome = false

    private var loginHoldsPermit = false

    val failOpenGeneration: Int
        get() = failOpen.get()

    fun register() {
        ClientConfigurationConnectionEvents.INIT.register { handler, _ ->
            loginSettled((handler as ClientCommonPacketListenerAccessor).`polyplus$getConnection`())
        }
        ClientLoginConnectionEvents.DISCONNECT.register { handler, _ ->
            loginSettled((handler as ClientHandshakePacketListenerAccessor).`polyplus$getConnection`())
        }
    }

    @JvmStatic
    fun begin(connection: Any): Boolean {
        synchronized(outcomeLock) {
            val depth = loginDepths[connection]
            if (depth != null) {
                loginDepths[connection] = depth + 1
                return true
            }
            loginsInFlight[connection] = Unit
        }
        releaseStaleHold()
        val held = try {
            gate.tryAcquire(LOGIN_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (held) {
            synchronized(outcomeLock) {
                loginDepths[connection] = 1
                loginHoldsPermit = true
            }
        } else {
            markOutcomePending(connection, heldGate = false)
            failOpen.incrementAndGet()
        }
        return held
    }

    @JvmStatic
    fun end(connection: Any, held: Boolean) {
        if (!held) {
            synchronized(outcomeLock) {
                if (!loginDepths.containsKey(connection)) loginsInFlight.remove(connection)
                extendOutcomeDeadline(connection)
            }
            return
        }
        synchronized(outcomeLock) {
            val depth = (loginDepths.remove(connection) ?: 0) - 1
            if (depth > 0) {
                loginDepths[connection] = depth
                return
            }
            loginsInFlight.remove(connection)
            if (depth < 0) {
                LOGGER.warn("A login ended without a matching begin; leaving the permit to the stale sweep")
            }
            markOutcomePending(connection, heldGate = depth == 0)
        }
    }

    internal fun loginSettled(connection: Any) {
        synchronized(outcomeLock) {
            settledBeforePending.entries.removeIf { it.value <= System.currentTimeMillis() }
            if (pendingOutcomes.remove(connection) == null) {
                if (loginsInFlight.containsKey(connection)) {
                    settledBeforePending[connection] = System.currentTimeMillis() + MAX_HOLD_MS
                }
                return
            }
            releaseGateIfSettled()
        }
    }

    internal fun releaseStaleHold(now: Long = System.currentTimeMillis()) {
        synchronized(outcomeLock) {
            pendingOutcomes.entries.removeIf { it.value.deadlineMs <= now }
            settledBeforePending.entries.removeIf { it.value <= now }
            releaseGateIfSettled()
        }
    }

    private fun markOutcomePending(connection: Any, heldGate: Boolean) = synchronized(outcomeLock) {
        heldGateAwaitingOutcome = heldGateAwaitingOutcome || heldGate
        if (settledBeforePending.remove(connection) != null) {
            releaseGateIfSettled()
            return@synchronized
        }
        pendingOutcomes[connection] = PendingOutcome(System.currentTimeMillis() + MAX_HOLD_MS)
    }

    private fun extendOutcomeDeadline(connection: Any) = synchronized(outcomeLock) {
        pendingOutcomes[connection]?.deadlineMs = System.currentTimeMillis() + MAX_HOLD_MS
    }

    private fun releaseGateIfSettled() {
        if (pendingOutcomes.isEmpty() && heldGateAwaitingOutcome) {
            heldGateAwaitingOutcome = false
            if (loginHoldsPermit) {
                loginHoldsPermit = false
                gate.release()
            }
        }
    }

    private fun hasPendingOutcome(): Boolean = synchronized(outcomeLock) { pendingOutcomes.isNotEmpty() }

    suspend fun <T> whileNotLoggingIn(queueMs: Long, block: suspend () -> T): T {
        val deadline = System.currentTimeMillis() + queueMs
        val ceiling = deadline + MAX_HOLD_MS
        while (true) {
            val now = System.currentTimeMillis()
            releaseStaleHold(now)
            if (now >= ceiling || (now >= deadline && !hasPendingOutcome())) return block()
            if (!hasPendingOutcome() && gate.tryAcquire(0, TimeUnit.MILLISECONDS)) {
                if (hasPendingOutcome()) {
                    gate.release()
                    delay(POLL_MS)
                    continue
                }
                try {
                    return block()
                } finally {
                    gate.release()
                }
            }
            delay(POLL_MS)
        }
    }
}
