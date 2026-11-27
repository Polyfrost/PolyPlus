package org.polyfrost.polyplus.client.cosmetics.colors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal object ColourSaves {
    const val STILL_SAVING = "Your colors are still saving. Try again in a moment."

    private val locks = ColourSaveLocks()

    val particleChoices = ColourChoices()

    private val notes = mutableStateMapOf<Int, String>()

    private var revision by mutableIntStateOf(0)

    fun pending(ids: Collection<Int>): Boolean {
        revision
        return locks.locked(ids)
    }

    fun auraPending(): Boolean = pending(auraHold(equippedAura()))

    fun blocksAuraSwap(next: Int?, ticket: Long = Long.MAX_VALUE): Boolean =
        auraSwapBlocked(equippedAura(), next) { locks.locked(listOf(it), ticket) }

    fun reserveOutfit(next: Int?, writesColour: Boolean): Hold? {
        val ids = outfitColourLocks(equippedAura(), next, writesColour)
        if (ids.isEmpty()) return Hold(null)
        return start(ids)?.let(::Hold)
    }

    fun ticket(): Long = locks.ticket()

    fun blocks(ids: Collection<Int>, ticket: Long): Boolean = locks.locked(ids, ticket)

    fun start(ids: Collection<Int>): Long? = locks.acquire(group(ids))?.also { changed() }

    fun finish(token: Long) {
        if (locks.release(token)) changed()
    }

    fun holdAura(): Hold? = start(auraHold(equippedAura()))?.let(::Hold)

    fun <T> refused(): Result<T> = Result.failure(ColorSaveException(STILL_SAVING))

    fun note(ids: Collection<Int>, message: String?) {
        for (id in ids) {
            if (message == null) notes.remove(id) else notes[id] = message
        }
    }

    fun noteFor(ids: Collection<Int>): String? = ids.firstNotNullOfOrNull(notes::get)

    fun group(ids: Collection<Int>): Set<Int> {
        val group = ids.toHashSet()
        for (id in ids) CosmeticCatalog.groupContaining(id)?.variants?.mapTo(group) { it.id }
        return group
    }

    private fun equippedAura(): Int? = CosmeticCatalog.localEquipped().equipped[BodySlot.Aura]

    private fun changed() {
        ClientPlatform.runOnMain { revision++ }
    }

    class Hold internal constructor(private val token: Long?) {
        fun release() {
            token?.let(::finish)
        }
    }
}
