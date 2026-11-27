package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.EquipCues
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.CosmeticClock
import java.util.UUID

internal object PetEquips {
    private const val SLOT = 0
    private const val PRUNE_FRAMES = 600

    private val cues = EquipCues(1)
    private var prunedFrame = 0

    init {
        eventHandler<WorldEvent.Unload> { cues.clear() }
    }

    fun changed(player: UUID, cosmeticId: Int?) {
        ClientPlatform.runOnMain {
            val shown = if (player == ClientPlatform.localPlayerUuid()) Minecraft.getInstance().player?.uuid ?: player else player
            cues.changed(CosmeticWearers.wearerId(shown), SLOT, cosmeticId, CosmeticClock.seconds())
        }
    }

    fun seen(owner: UUID, frame: Int, now: Double) {
        cues.seen(CosmeticWearers.wearerId(owner), frame, now)
    }

    fun hidden(owner: UUID) {
        cues.hidden(CosmeticWearers.wearerId(owner))
    }

    fun events(owner: UUID, cosmeticId: Int, colorway: Int, frame: Int, now: Double): Int {
        if (frame - prunedFrame >= PRUNE_FRAMES) {
            prunedFrame = frame
            cues.prune(now)
        }
        return if (cues.drawn(CosmeticWearers.wearerId(owner), frame, SLOT, cosmeticId, colorway, now)) Renderer.EVENT_EQUIP else 0
    }
}
