package org.polyfrost.polyplus.client.cosmetics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.polyfrost.polyplus.client.cosmetics.colors.LookQueue
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.concurrent.atomic.AtomicBoolean

internal val EQUIPMENT_RETRY_MS = listOf(500L, 1_500L)
internal val EQUIPMENT_RECONCILE_MS = listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L)
internal const val EQUIPMENT_CONFIRM_MS = 5_000L

internal class EquipmentRefreshTimedOut : IllegalStateException("Your cosmetics took too long to load")

internal class SkinChangeGate {
    private val pending = MutableStateFlow(0)

    fun begin() {
        pending.update { it + 1 }
    }

    fun end() {
        pending.update { (it - 1).coerceAtLeast(0) }
    }

    suspend fun settled() {
        pending.first { it == 0 }
    }
}

internal fun outfitLoadout(
    equipped: Map<BodySlot, Int>,
    owned: Set<Int>,
    allows: (Int, BodySlot) -> Boolean,
): Map<BodySlot, Int> = equipped.filter { (slot, id) -> id in owned && allows(id, slot) }

internal suspend fun confirmEquipment(
    refresh: suspend () -> Result<Unit>,
    expected: Map<BodySlot, Int>?,
    assume: suspend (Map<BodySlot, Int>) -> Unit,
    reconcileLater: () -> Unit,
    retryDelays: List<Long> = EQUIPMENT_RETRY_MS,
    pause: suspend (Long) -> Unit = { delay(it) },
    deadline: Long = EQUIPMENT_CONFIRM_MS,
): Result<Unit> {
    var result: Result<Unit> = Result.failure(EquipmentRefreshTimedOut())
    withTimeoutOrNull(deadline) {
        result = refresh()
        for (wait in retryDelays) {
            if (result.isSuccess) break
            pause(wait)
            result = refresh()
        }
    }
    if (result.isSuccess) return result
    reconcileLater()
    if (expected == null) return result
    assume(expected)
    return Result.success(Unit)
}

internal class EquipmentSync(
    private val scope: CoroutineScope,
    private val queue: LookQueue,
    private val refresh: suspend () -> Result<Unit>,
    private val assume: suspend (Map<BodySlot, Int>) -> Unit,
    private val publish: suspend () -> Unit,
    private val load: suspend () -> Unit,
    private val retryDelays: List<Long> = EQUIPMENT_RETRY_MS,
    private val reconcileDelays: List<Long> = EQUIPMENT_RECONCILE_MS,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val deadline: Long = EQUIPMENT_CONFIRM_MS,
) {
    private val stale = AtomicBoolean(false)
    private val reconciling = AtomicBoolean(false)

    suspend fun confirm(expected: Map<BodySlot, Int>? = null): Result<Unit> {
        val result = confirmEquipment(::refreshed, expected, assume, ::reconcileLater, retryDelays, pause, deadline)
        if (result.isSuccess) {
            publish()
            loadLater()
        }
        return result
    }

    suspend fun sync(): Result<Unit> = queue.run { confirm() }

    private suspend fun refreshed(): Result<Unit> = refresh().onSuccess { stale.set(false) }

    private fun loadLater() {
        scope.launch { load() }
    }

    private fun reconcileLater() {
        stale.set(true)
        if (!reconciling.compareAndSet(false, true)) return
        scope.launch {
            try {
                for (wait in reconcileDelays) {
                    pause(wait)
                    if (!stale.get()) return@launch
                    val synced = queue.run {
                        if (!stale.get()) return@run Result.success(false)
                        val refresh = withTimeoutOrNull(deadline) { refreshed() } ?: Result.failure(EquipmentRefreshTimedOut())
                        refresh.map {
                            publish()
                            true
                        }
                    }
                    if (synced.getOrNull() == true) loadLater()
                    if (synced.isSuccess) return@launch
                }
            } finally {
                reconciling.set(false)
            }
        }
    }
}
