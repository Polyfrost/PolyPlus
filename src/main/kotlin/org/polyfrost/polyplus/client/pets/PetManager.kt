//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PetManager {
    private val LOGGER = LogManager.getLogger()
    private val activeByOwner = ConcurrentHashMap<UUID, PetEntity>()
    private val activeCosmeticId = ConcurrentHashMap<UUID, Int>()

    fun currentPetCosmeticId(owner: UUID): Int? = activeCosmeticId[owner]

    fun ensurePet(owner: UUID, cosmeticId: Int) {
        if (activeCosmeticId[owner] == cosmeticId) return
        val definition = CosmeticAssetCache.getPetDefinition(cosmeticId) ?: run {
            LOGGER.warn("no parsed PetDefinition cached for cosmetic {} (owner {})", cosmeticId, owner)
            return
        }

        ClientPlatform.runOnMain {
            val level = Minecraft.getInstance().level ?: run {
                LOGGER.warn("no client level loaded, deferring spawn of cosmetic {} for {}", cosmeticId, owner)
                return@runOnMain
            }
            val ownerEntity = level.players().firstOrNull { it.uuid == owner } ?: run {
                LOGGER.warn("owner {} has no loaded player entity, cannot spawn cosmetic {}", owner, cosmeticId)
                return@runOnMain
            }

            despawn(owner)

            val entity = PetEntity(PetEntities.PET_ENTITY_TYPE, level)
            entity.initialize(definition, owner)
            val angle = Math.toRadians(ownerEntity.yRot.toDouble()) + Math.PI
            val spawnX = ownerEntity.x + kotlin.math.sin(angle) * 1.5
            val spawnZ = ownerEntity.z - kotlin.math.cos(angle) * 1.5
            //? if >= 1.21.5 {
            entity.snapTo(spawnX, ownerEntity.y, spawnZ, 0f, 0f)
            //?} else {
            /*entity.moveTo(spawnX, ownerEntity.y, spawnZ, 0f, 0f)
            *///?}
            level.addEntity(entity)
            activeByOwner[owner] = entity
            activeCosmeticId[owner] = cosmeticId
            LOGGER.info("Spawned pet entity {} for cosmetic {} owned by {}", entity.id, cosmeticId, owner)
        }
    }

    fun despawn(owner: UUID) {
        val entity = activeByOwner.remove(owner) ?: return
        activeCosmeticId.remove(owner)
        LOGGER.info("Despawning pet entity {} for owner {}", entity.id, owner)
        ClientPlatform.runOnMain { entity.discard() }
    }

    fun despawnAll() {
        activeByOwner.keys.toList().forEach(::despawn)
    }
}
//?}
