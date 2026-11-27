package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffect
import org.polyfrost.polyplus.client.cosmetics.sets.BonusState
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.CosmeticClock
import java.util.UUID

internal object CosmeticEquips {
    private const val PRUNE_FRAMES = 600

    private val cues = EquipCues(BodySlot.values().size)
    private val bonusCues = EquipCues(BonusState.MAX_ACTIVE)
    private val bonusSlots = HashMap<UUID, IntArray>()
    private var prunedFrame = 0

    init {
        eventHandler<WorldEvent.Unload> {
            cues.clear()
            bonusCues.clear()
            bonusSlots.clear()
        }
    }

    fun changed(player: UUID, slot: BodySlot, cosmeticId: Int?) {
        if (slot == BodySlot.Unknown) return
        ClientPlatform.runOnMain {
            cues.changed(wearerOf(player), slot.ordinal, cosmeticId, CosmeticClock.seconds())
        }
    }

    fun bonusesChanged(player: UUID, before: List<BonusRef>, after: List<BonusRef>) {
        ClientPlatform.runOnMain {
            val slots = bonusSlots.getOrPut(player) { emptyBonusSlots(BonusState.MAX_ACTIVE) }
            val changes = bonusCueChanges(slots, before, after)
            if (slots.all { it == NO_BONUS }) bonusSlots.remove(player)
            if (changes.isEmpty()) return@runOnMain
            val wearer = wearerOf(player)
            val now = CosmeticClock.seconds()
            for ((slot, target) in changes) bonusCues.changed(wearer, slot, target, now)
        }
    }

    private fun wearerOf(player: UUID): Long {
        val shown = if (player == ClientPlatform.localPlayerUuid()) Minecraft.getInstance().player?.uuid ?: player else player
        return CosmeticWearers.wearerId(shown)
    }

    fun seen(wearer: Long) {
        CosmeticWearers.begin()
        cues.seen(wearer, CosmeticWearers.frame, CosmeticWearers.clock)
        bonusCues.seen(wearer, CosmeticWearers.frame, CosmeticWearers.clock)
    }

    fun bonusEvents(wearer: CosmeticWearers.Wearer, owner: UUID?, bonus: BonusEffect): Int {
        if (wearer.preview || wearer.hand || owner == null) return 0
        val slot = bonusSlots[BonusState.accountId(owner)]?.indexOf(bonus.ref.id) ?: return 0
        if (slot < 0) return 0
        val equipped = bonusCues.drawn(wearer.id, CosmeticWearers.frame, slot, bonusCueId(bonus.ref), bonus.cosmetic.colorway, CosmeticWearers.clock)
        return if (equipped) Renderer.EVENT_EQUIP else 0
    }

    fun events(wearer: CosmeticWearers.Wearer, cosmetic: AttachedCosmetic): Int {
        if (wearer.preview || wearer.hand) return 0
        val frame = CosmeticWearers.frame
        val now = CosmeticWearers.clock
        if (frame - prunedFrame >= PRUNE_FRAMES) {
            prunedFrame = frame
            cues.prune(now)
            bonusCues.prune(now)
        }
        val lobby = PreviewTryOn.lobbyEquipEvents(frame, cosmetic.cosmeticId)
        val equipped = cues.drawn(wearer.id, frame, cosmetic.slot.ordinal, cosmetic.cosmeticId, cosmetic.colorway, now)
        return if (equipped) Renderer.EVENT_EQUIP else lobby
    }
}

internal fun bonusCueId(ref: BonusRef): Int = -ref.id

internal const val NO_BONUS = Int.MIN_VALUE

internal fun emptyBonusSlots(size: Int): IntArray = IntArray(size) { NO_BONUS }

internal fun bonusCueChanges(slots: IntArray, before: List<BonusRef>, after: List<BonusRef>): List<Pair<Int, Int?>> {
    val changes = ArrayList<Pair<Int, Int?>>()
    releaseBonusSlots(slots, before, changes)
    claimBonusSlots(slots, before, null)
    releaseBonusSlots(slots, after, changes)
    claimBonusSlots(slots, after, changes)
    return changes
}

private fun releaseBonusSlots(slots: IntArray, active: List<BonusRef>, changes: MutableList<Pair<Int, Int?>>) {
    for (slot in slots.indices) {
        val id = slots[slot]
        if (id == NO_BONUS || active.any { it.id == id }) continue
        slots[slot] = NO_BONUS
        changes += slot to null
    }
}

private fun claimBonusSlots(slots: IntArray, active: List<BonusRef>, changes: MutableList<Pair<Int, Int?>>?) {
    for (ref in active) {
        if (slots.contains(ref.id)) continue
        val free = slots.indexOf(NO_BONUS)
        if (free < 0) return
        slots[free] = ref.id
        changes?.add(free to bonusCueId(ref))
    }
}
