package org.polyfrost.polyplus.client.pets

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.mixin.client.access.HolderReferenceInvoker
import org.polyfrost.polyplus.mixin.client.access.MappedRegistryAccessor
import java.util.IdentityHashMap

//? if >= 26.2 {
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityType
//?}

//? if < 26.2 {
/*import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.world.entity.EntityDimensions
*///?}

object PetEntities {
    private val PET_ENTITY_TYPE_KEY: ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pet"))

    // registered entity types get synced to joining players, who then can't join a Poly+ user's world without Poly+
    // EntityType's constructor always requests an intrusive holder, so hand it a throwaway map that never gets registered
    private inline fun <T : Entity> createUnregistered(
        key: ResourceKey<EntityType<*>>,
        create: () -> EntityType<T>,
    ): EntityType<T> {
        val registry = BuiltInRegistries.ENTITY_TYPE as MappedRegistryAccessor
        val wasFrozen = registry.`polyplus$isFrozen`()
        val holders = registry.`polyplus$getIntrusiveHolders`()
        registry.`polyplus$setFrozen`(false)
        registry.`polyplus$setIntrusiveHolders`(IdentityHashMap<Any, Any>())
        val type = try {
            create()
        } finally {
            registry.`polyplus$setIntrusiveHolders`(holders)
            registry.`polyplus$setFrozen`(wasFrozen)
        }
        // keys and tags are only ever bound for registered holders, and reading either throws while unbound
        val holder = type.builtInRegistryHolder() as HolderReferenceInvoker
        holder.`polyplus$bindKey`(key)
        holder.`polyplus$bindTags`(emptyList<Any>())
        return type
    }

    val PET_ENTITY_TYPE: EntityType<PetEntity> = createUnregistered(PET_ENTITY_TYPE_KEY) {
        //? if >= 26.2 {
        FabricEntityType.Builder.createMob(::PetEntity, MobCategory.MISC) { it }
            .sized(0.8f, 0.8f)
            .noSummon()
            .noSave()
            .clientTrackingRange(8)
            .build(PET_ENTITY_TYPE_KEY)
        //?} elif >= 1.21.4 {
        /*FabricEntityTypeBuilder.create(MobCategory.MISC, ::PetEntity)
            .dimensions(EntityDimensions.scalable(0.8f, 0.8f))
            .disableSummon()
            .disableSaving()
            .trackRangeChunks(8)
            .build(PET_ENTITY_TYPE_KEY)
        *///?} else {
        /*FabricEntityTypeBuilder.create(MobCategory.MISC, ::PetEntity)
            .dimensions(EntityDimensions.scalable(0.8f, 0.8f))
            .disableSummon()
            .disableSaving()
            .trackRangeChunks(8)
            .build()
        *///?}
    }

    fun register() {
        FabricDefaultAttributeRegistry.register(PET_ENTITY_TYPE, PetEntity.createAttributes())
        EntityRendererRegistry.register(PET_ENTITY_TYPE, ::PetEntityRenderer)
        LogManager.getLogger("PetEntities").info("Registered pet entity type + renderer")
    }
}
