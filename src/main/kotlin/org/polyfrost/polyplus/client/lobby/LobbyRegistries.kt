package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
//? if < 1.21.4 {
/*import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.tags.TagKey
*///?}
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
//? if >= 26.1
import net.minecraft.util.Util

class LobbyRegistries private constructor(
    val access: RegistryAccess.Frozen,
    //? if >= 1.21.4 {
    private val staticTags: List<Registry.PendingTags<*>>,
    //?} else {
    /*private val staticTags: List<() -> Unit>,
    *///?}
) {
    fun bindStaticTags() {
        if (Minecraft.getInstance().level != null) return
        //? if >= 1.21.4 {
        staticTags.forEach { it.apply() }
        //?} else {
        /*staticTags.forEach { it() }
        *///?}
    }

    companion object {
        @Volatile
        private var vanillaOnly = false

        fun load(): LobbyRegistries {
            if (!vanillaOnly) {
                try {
                    return load(trustedPacks())
                } catch (error: Throwable) {
                    vanillaOnly = true
                    LOBBY_LOGGER.warn("Lobby registries failed to load with mod data, retrying with vanilla data only", error)
                }
            }
            //? if >= 26.3 {
            return load(listOf(ServerPacksSource.createVanillaPackSource().fullResources()))
            //?} else {
            /*return load(listOf<PackResources>(ServerPacksSource.createVanillaPackSource()))
            *///?}
        }

        private fun trustedPacks(): List<PackResources> {
            val packs = ServerPacksSource.createVanillaTrustedRepository()
            packs.reload()
            packs.setSelected(listOf("vanilla"))
            return packs.openAllSelected()
        }

        private fun load(sources: List<PackResources>): LobbyRegistries {
            return MultiPackResourceManager(PackType.SERVER_DATA, sources).use { resources ->
                val layers = RegistryLayer.createRegistryAccess()
                //? if < 1.21.4 {
                /*val world = RegistryDataLoader.load(resources, layers.getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES)
                val dimensions = RegistryDataLoader.load(resources, layers.getAccessForLoading(RegistryLayer.DIMENSIONS), RegistryDataLoader.DIMENSION_REGISTRIES)
                val merged = layers.replaceFrom(RegistryLayer.WORLDGEN, world).replaceFrom(RegistryLayer.DIMENSIONS, dimensions)
                val access = merged.compositeAccess()
                LobbyRegistries(access, access.registries().map { binder(resources, it) }.toList())
                *///?} else {
                val staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC))
                //? if >= 26.3 {
                val worldLayer = RegistryLayer.WORLD
                val worldRegistries = RegistryDataLoader.WORLD_REGISTRIES
                //?} else {
                /*val worldLayer = RegistryLayer.WORLDGEN
                val worldRegistries = RegistryDataLoader.WORLDGEN_REGISTRIES
                *///?}
                val worldContext = layers.getAccessForLoading(worldLayer)
                val lookups: List<HolderLookup.RegistryLookup<*>> = TagLoader.buildUpdatedLookups(worldContext, staticTags)
                //? if >= 26.1
                val executor = Util.backgroundExecutor()
                //? if >= 26.1 {
                val world = RegistryDataLoader.load(resources, lookups, worldRegistries, executor).join()
                val dimensionContext = lookups + world.listRegistries().toList()
                val dimensions = RegistryDataLoader.load(resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, executor).join()
                //?} else {
                /*val world = RegistryDataLoader.load(resources, lookups, worldRegistries)
                val dimensionContext = lookups + world.listRegistries().toList()
                val dimensions = RegistryDataLoader.load(resources, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES)
                *///?}
                val merged = layers.replaceFrom(worldLayer, world, dimensions)
                LobbyRegistries(merged.compositeAccess(), staticTags)
                //?}
            }
        }

        //? if < 1.21.4 {
        /*private fun <T> binder(resources: ResourceManager, entry: RegistryAccess.RegistryEntry<T>): () -> Unit {
            val registry = entry.value()
            val loaded = TagLoader<Holder<T>>({ registry.getHolder(it) }, Registries.tagsDirPath(entry.key())).loadAndBuild(resources)
            val tags = loaded.entries.associate { TagKey.create(entry.key(), it.key) to it.value.toList() }
            return { registry.bindTags(tags) }
        }
        *///?}
    }
}
//?}
