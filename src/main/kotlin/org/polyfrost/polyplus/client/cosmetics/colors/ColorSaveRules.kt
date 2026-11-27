package org.polyfrost.polyplus.client.cosmetics.colors

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal fun ownPalettes(
    saved: Map<Int, Map<String, Int>>,
    worn: Map<Int, Map<String, Int>>,
    current: Map<Int, Map<String, Int>>,
    pending: (Int) -> Boolean,
): Map<Int, Map<String, Int>> = saved + worn + current.filterKeys(pending)

internal fun snapshotWorn(
    activeSaved: Map<Int, Map<String, Int>>?,
    worn: Map<Int, Map<String, Int>>,
    equipped: Collection<Int>,
    current: Map<Int, Map<String, Int>>,
): Map<Int, Map<String, Int>> =
    if (activeSaved != null) activeSaved + worn else current.filterKeys { it !in equipped } + worn

internal const val AURA_SLOT = "aura"

internal class LookChanges {
    private var current = 0L
    private val changedAt = HashMap<Int, Long>()

    @Synchronized
    fun generation(): Long = current

    @Synchronized
    fun changed(ids: Collection<Int>) {
        current++
        for (id in ids) changedAt[id] = current
    }

    @Synchronized
    fun stale(id: Int, seen: Long): Boolean = (changedAt[id] ?: 0L) > seen
}

internal class ColourChoices {
    private val made = AtomicLong()

    fun made() {
        made.incrementAndGet()
    }

    fun seen(): Long = made.get()

    fun since(seen: Long): Boolean = made.get() != seen
}

internal class LookQueue {
    private val turn = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = turn.withLock { block() }

    suspend fun <T, R> handOff(lock: Mutex, queued: suspend () -> T, after: suspend (T) -> R): R {
        var held = false
        try {
            val value = run {
                lock.lock()
                held = true
                queued()
            }
            return after(value)
        } finally {
            if (held) lock.unlock()
        }
    }
}

internal suspend fun <T> holdUntilSettled(release: () -> Unit, work: suspend (settling: (Job) -> Unit) -> T): T {
    var settling: Job? = null
    try {
        return work { settling = it }
    } finally {
        settling?.invokeOnCompletion { release() } ?: release()
    }
}

internal enum class ParticleSaveBlock { OutfitApplied, AuraChanged }

internal fun particleSaveBlock(
    outfitApplied: Boolean,
    selectedAura: Int?,
    equippedAura: Int?,
    seenAuraChange: Long = 0L,
    auraChange: Long = 0L,
): ParticleSaveBlock? = when {
    outfitApplied -> ParticleSaveBlock.OutfitApplied
    seenAuraChange != auraChange || selectedAura != equippedAura -> ParticleSaveBlock.AuraChanged
    else -> null
}

internal class AuraChanges {
    private var generation = 0L
    private var target: Int? = null
    private var known = false

    @Synchronized
    fun generation(): Long = generation

    @Synchronized
    fun started(target: Int?) {
        generation++
        this.target = target
        known = true
    }

    @Synchronized
    fun observed(target: Int?, equipped: Int?) {
        val current = if (known) this.target else equipped
        if (target != current) generation++
        this.target = target
        known = true
    }

    @Synchronized
    fun forget() {
        generation++
        known = false
        target = null
    }
}

internal class PaletteJobs {
    private val running = HashSet<Int>()
    private val idle = HashMap<Int, CompletableDeferred<Unit>>()

    @Synchronized
    fun busy(id: Int): Boolean = id in running

    @Synchronized
    fun claim(id: Int): Boolean = running.add(id)

    @Synchronized
    private fun free(ids: Collection<Int>) {
        for (id in ids) {
            running.remove(id)
            idle.remove(id)?.complete(Unit)
        }
    }

    @Synchronized
    private fun tryHold(ids: List<Int>): CompletableDeferred<Unit>? {
        val busy = ids.firstOrNull { it in running } ?: run {
            running.addAll(ids)
            return null
        }
        return idle.getOrPut(busy) { CompletableDeferred() }
    }

    private suspend fun acquire(ids: List<Int>): Boolean {
        var waited = false
        while (true) {
            val wait = tryHold(ids) ?: return waited
            waited = true
            wait.await()
        }
    }

    suspend fun <T> hold(ids: Collection<Int>, block: suspend () -> T): T {
        val held = ids.distinct()
        acquire(held)
        try {
            return block()
        } finally {
            free(held)
        }
    }

    suspend fun run(id: Int, job: suspend (Boolean) -> Unit) {
        val waited = acquire(listOf(id))
        try {
            job(waited)
        } finally {
            free(listOf(id))
        }
    }

    suspend fun runClaimed(id: Int, job: suspend () -> Unit) {
        try {
            job()
        } finally {
            free(listOf(id))
        }
    }
}

internal class ColourSaveLocks {
    private val held = HashMap<Long, Set<Int>>()
    private var next = 0L

    @Synchronized
    fun acquire(ids: Collection<Int>): Long? {
        if (ids.isEmpty() || locked(ids)) return null
        val token = ++next
        held[token] = ids.toHashSet()
        return token
    }

    @Synchronized
    fun release(token: Long): Boolean = held.remove(token) != null

    @Synchronized
    fun ticket(): Long = next

    @Synchronized
    fun locked(ids: Collection<Int>, ticket: Long = Long.MAX_VALUE): Boolean =
        held.any { (token, set) -> token <= ticket && ids.any(set::contains) }
}

internal const val PARTICLE_COLOUR_LOCK = Int.MIN_VALUE

internal fun auraHold(equipped: Int?): List<Int> = listOfNotNull(PARTICLE_COLOUR_LOCK, equipped)

internal fun auraSwapBlocked(equipped: Int?, next: Int?, saving: (Int) -> Boolean): Boolean =
    next != equipped && auraHold(equipped).any(saving)

internal fun outfitColourLocks(equipped: Int?, next: Int?, writesColour: Boolean): List<Int> = when {
    writesColour -> (auraHold(equipped) + listOfNotNull(next)).distinct()
    next != equipped -> auraHold(equipped)
    else -> emptyList()
}

internal fun outfitWritesColour(blank: Boolean?, particleColor: Int?): Boolean = blank != true || particleColor != null

internal fun outfitResultChangesLook(keepLook: Boolean, color: Int?, colorBefore: Int?): Boolean =
    !keepLook || color != colorBefore

internal class EditorSessions {
    private var current = 0L
    private var open = false

    fun open(): Long {
        open = true
        return ++current
    }

    fun close() {
        open = false
    }

    fun owns(session: Long): Boolean = open && session == current
}

internal fun outfitAuraWrite(own: Map<String, Int>?, latest: Map<String, Int>?, color: Int?): Map<String, Int>? {
    val applied = auraPalette(own, color)
    val merged = auraPalette(latest ?: own, color)
    return merged.takeIf { it != applied }
}

internal sealed interface OutfitAuraStep {
    class Write(val colors: Map<String, Int>) : OutfitAuraStep
    object Clear : OutfitAuraStep
    object Settled : OutfitAuraStep
}

internal fun outfitAuraStep(own: Map<String, Int>?, latest: Map<String, Int>?, color: Int?, resend: Boolean): OutfitAuraStep {
    outfitAuraWrite(own, latest, color)?.let { return OutfitAuraStep.Write(it) }
    if (!resend) return OutfitAuraStep.Settled
    return auraPalette(own, color)?.let { OutfitAuraStep.Write(it) } ?: OutfitAuraStep.Clear
}

internal fun auraColorCleared(worn: Map<String, Int>?): Map<String, Int>? = auraPalette(worn, null)

internal fun auraPalette(stored: Map<String, Int>?, color: Int?): Map<String, Int>? {
    val kept = stored.orEmpty() - AURA_SLOT
    val next = if (color == null) kept else kept + (AURA_SLOT to (color and 0x00FF_FFFF))
    return next.takeIf { it.isNotEmpty() }
}

internal fun snapshotCarriesAccount(
    account: UUID,
    requested: Collection<String>?,
    players: Collection<String>,
    rejected: Collection<String>,
): Boolean {
    fun Collection<String>.hasAccount() = any { runCatching { UUID.fromString(it) }.getOrNull() == account }
    if (rejected.hasAccount()) return false
    return (requested ?: players).hasAccount()
}

internal suspend fun pairedOutcome(results: List<Result<Unit>>, undo: suspend (List<Int>) -> Boolean): Result<Unit> {
    val failure = results.firstOrNull { it.isFailure } ?: return Result.success(Unit)
    if (results.all { it.isFailure }) return failure
    val undone = undo(results.indices.filter { results[it].isSuccess })
    return Result.failure(
        ColorSaveException(
            when {
                results.size > 2 && undone -> "Some of your colors didn't save, so nothing changed. Try again."
                results.size > 2 -> "Some of your colors didn't save. Try again."
                undone -> "Only one side saved, so neither changed. Try again."
                else -> "Only one side saved. Try again to match both sides."
            },
        ),
    )
}

internal class SendPacer(private val limit: Int, private val windowMs: Long) {
    private val reserved = ArrayDeque<Long>()

    @Synchronized
    fun reserve(now: Long): Long {
        while (reserved.isNotEmpty() && reserved.first() <= now - windowMs) reserved.removeFirst()
        var at = maxOf(now, reserved.lastOrNull() ?: now)
        if (reserved.size >= limit) at = maxOf(at, reserved[reserved.size - limit] + windowMs)
        reserved.addLast(at)
        return at
    }
}
