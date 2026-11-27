package org.polyfrost.polyplus.client.pets

import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PetManager {
    const val PET_SLOT = 0

    class Pet internal constructor(val owner: UUID, val cosmeticId: Int) {
        val wearer: Long = Renderer.actorWearer(CosmeticWearers.wearerId(owner), PET_SLOT)
        internal val body = PetBody(wearer)
        internal val ownerState = PetOwner()
        internal var asset: PetAsset? = null
        internal var assetVersion = -1
        internal var present = false
        internal val painted = PetPaintSlot<PetAsset>()
        internal var scene: PetScene? = null
        internal var row = -1
        internal var light = 0
        internal var colors: Map<String, Int>? = null
        internal var notice: Pet? = null
        internal var noticeSince = 0.0
        internal var noticeReadyAt = 0.0
        internal var pairSeen = 0
        internal var pairListed = 0
        internal var crowdIndex = -1
        internal var shoulderAnchor: String? = null
        internal val placement = PetPlacement()
    }

    private val pets = ConcurrentHashMap<UUID, Pet>()
    private val ordered = ArrayList<Pet>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        PetAssets.ensureNativesDir()
        eventHandler<TickEvent.End> {
            PetClock.tick()
            PetAssets.releaseRetired()
        }
        eventHandler<FramebufferRenderEvent.End> { PetFrames.advance() }
        eventHandler<WorldEvent.Unload> {
            PetRenderHook.reset()
            PetInteractions.reset()
        }
    }

    fun currentPetCosmeticId(owner: UUID): Int? = pets[owner]?.cosmeticId

    fun ensurePet(owner: UUID, cosmeticId: Int) = ClientPlatform.runOnMain {
        val current = pets[owner]
        if (current?.cosmeticId == cosmeticId) return@runOnMain
        PetAssets.ensureNativesDir()
        val pet = Pet(owner, cosmeticId)
        pets[owner] = pet
        if (current != null) ordered.remove(current)
        var at = ordered.size
        while (at > 0 && ordered[at - 1].owner > owner) at--
        ordered.add(at, pet)
        current?.let(::release)
    }

    fun despawn(owner: UUID) = ClientPlatform.runOnMain {
        val removed = pets.remove(owner) ?: return@runOnMain
        ordered.remove(removed)
        release(removed)
    }

    fun despawnAll() = ClientPlatform.runOnMain {
        for (owner in pets.keys.toList()) pets.remove(owner)?.let(::release)
        ordered.clear()
    }

    internal fun pets(): List<Pet> = ordered

    internal fun pet(owner: UUID): Pet? = pets[owner]

    internal fun cosmeticIds(): Set<Int> = pets.values.mapTo(HashSet()) { it.cosmeticId }

    internal fun forgetAsset(asset: PetAsset) {
        for (pet in pets.values) {
            pet.painted.drop(asset)
            if (pet.asset === asset) pet.asset = PetAssets.get(asset.id)?.takeIf { it !== asset }
            if (pet.cosmeticId != asset.id) continue
            pet.scene = null
            pet.row = -1
            pet.body.spawned = false
            pet.placement.reset()
        }
    }

    private fun release(pet: Pet) {
        pet.scene = null
        pet.row = -1
        PetAssets.get(pet.cosmeticId)?.forget(pet.wearer)
    }
}
