//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.pets

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
//? if >= 26.2 {
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityType
//?} else {
/*import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
*///?}
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import org.polyfrost.polyplus.PolyPlusConstants

object PetEntities {
    private val PET_ENTITY_TYPE_KEY: ResourceKey<EntityType<*>> =
        ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pet"))

    //? if < 1.21.4 {
    /*@Suppress("UNCHECKED_CAST")
    private inline fun <R> withUnfrozenEntityTypes(block: () -> R): R {
        val registry = BuiltInRegistries.ENTITY_TYPE as net.minecraft.core.MappedRegistry<EntityType<*>>
        val accessor =
            registry as org.polyfrost.polyplus.mixin.client.Mixin_UnfreezeRegistry<EntityType<*>>
        val previous = accessor.`polyplus$getIntrusiveHolders`()
        accessor.`polyplus$setFrozen`(false)
        if (previous == null) accessor.`polyplus$setIntrusiveHolders`(java.util.IdentityHashMap())
        try {
            return block()
        } finally {
            registry.freeze()
        }
    }
    *///?}

    val PET_ENTITY_TYPE: EntityType<PetEntity> =
        //? if < 1.21.4 {
        /*withUnfrozenEntityTypes {
        *///?}
        Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PET_ENTITY_TYPE_KEY,
        //? if >= 26.2 {
        FabricEntityType.Builder.createMob(::PetEntity, MobCategory.MISC) { it }
            .sized(0.8f, 0.8f)
            .noSummon()
            .noSave()
            .clientTrackingRange(8)
            .build(PET_ENTITY_TYPE_KEY),
        //?} elif >= 1.21.4 {
        /*FabricEntityTypeBuilder.create(MobCategory.MISC, ::PetEntity)
            .dimensions(EntityDimensions.scalable(0.8f, 0.8f))
            .disableSummon()
            .disableSaving()
            .trackRangeChunks(8)
            .build(PET_ENTITY_TYPE_KEY),
        *///?} else {
        /*FabricEntityTypeBuilder.create(MobCategory.MISC, ::PetEntity)
            .dimensions(EntityDimensions.scalable(0.8f, 0.8f))
            .disableSummon()
            .disableSaving()
            .trackRangeChunks(8)
            .build(),
        *///?}
        )
        //? if < 1.21.4 {
        /*}
        *///?}

    fun register() {
        FabricDefaultAttributeRegistry.register(PET_ENTITY_TYPE, PetEntity.createAttributes())
        EntityRendererRegistry.register(PET_ENTITY_TYPE, ::PetEntityRenderer)
        org.apache.logging.log4j.LogManager.getLogger("PetEntities").info("Registered pet entity type + renderer")
    }
}
//?}
