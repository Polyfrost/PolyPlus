package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

object PetManager {
    private val LOGGER = LogManager.getLogger()
    private val activeByOwner = ConcurrentHashMap<UUID, PetEntity>()
    private val activeCosmeticId = ConcurrentHashMap<UUID, Int>()

    //? if > 1.8.9 {
    fun currentPetCosmeticId(owner: UUID): Int? = activeCosmeticId[owner]
    //?} else {
    /*fun currentPetCosmeticId(owner: UUID): Int? {
        val entity = activeByOwner[owner] ?: return null
        if (entity.removed || entity.world !== Minecraft.getInstance().world) return null
        return activeCosmeticId[owner]
    }
    *///?}

    fun ensurePet(owner: UUID, cosmeticId: Int) {
        if (currentPetCosmeticId(owner) == cosmeticId) return
        val definition = CosmeticAssetCache.getPetDefinition(cosmeticId) ?: run {
            LOGGER.warn("no parsed PetDefinition cached for cosmetic {} (owner {})", cosmeticId, owner)
            return
        }

        ClientPlatform.runOnMain {
            //? if > 1.8.9 {
            val level = Minecraft.getInstance().level ?: run {
            //?} else {
            /*val level = Minecraft.getInstance().world ?: run {
            *///?}
                LOGGER.warn("no client level loaded, deferring spawn of cosmetic {} for {}", cosmeticId, owner)
                return@runOnMain
            }
            //? if > 1.8.9 {
            val ownerEntity = level.players().firstOrNull { it.uuid == owner } ?: run {
            //?} else {
            /*val ownerEntity = level.getPlayer(owner) ?: run {
            *///?}
                LOGGER.warn("owner {} has no loaded player entity, cannot spawn cosmetic {}", owner, cosmeticId)
                return@runOnMain
            }

            despawn(owner)

            //? if > 1.8.9 {
            val entity = PetEntity(PetEntities.PET_ENTITY_TYPE, level)
            entity.initialize(definition, owner)
            val angle = Math.toRadians(ownerEntity.yRot.toDouble()) + Math.PI
            //?} else {
            /*PetEntities.register()
            val entity = PetEntity(level)
            entity.networkId = PetEntities.nextNetworkId()
            entity.initialize(definition, owner)
            val angle = Math.toRadians(ownerEntity.yaw.toDouble()) + Math.PI
            *///?}
            val spawnX = ownerEntity.x + sin(angle) * 1.5
            val spawnZ = ownerEntity.z - cos(angle) * 1.5
            //? if >= 1.21.5 {
            entity.snapTo(spawnX, ownerEntity.y, spawnZ, 0f, 0f)
            //?} elif > 1.8.9 {
            /*entity.moveTo(spawnX, ownerEntity.y, spawnZ, 0f, 0f)
            *///?} else {
            /*entity.setPositionAndAngles(spawnX, ownerEntity.y, spawnZ, 0f, 0f)
            entity.lastYaw = 0f
            entity.lastPitch = 0f
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
        //? if > 1.8.9 {
        ClientPlatform.runOnMain { entity.discard() }
        //?} else {
        /*ClientPlatform.runOnMain { entity.world.removeEntity(entity) }
        *///?}
    }

    fun despawnAll() {
        activeByOwner.keys.toList().forEach(::despawn)
    }
}
