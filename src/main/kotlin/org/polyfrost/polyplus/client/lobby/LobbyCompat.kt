package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import java.lang.reflect.Method
import java.util.concurrent.Executor
import net.minecraft.client.Camera
//? if >= 26.1
import net.minecraft.client.DeltaTracker
import java.net.InetAddress
import java.net.InetSocketAddress
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import org.polyfrost.polyplus.mixin.client.access.ConnectionAccessor
//? if >= 26.2 {
import net.minecraft.client.renderer.extract.LevelExtractor
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer
*///?}
//? if >= 26.1
import net.minecraft.client.renderer.state.GameRenderState
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
//? if < 1.21.4
/*import net.minecraft.world.level.LevelHeightAccessor*/
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerRO
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.Vec3
import com.mojang.serialization.Codec
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
//? if < 1.21.8 {
/*import org.joml.Matrix4f
import org.joml.Quaternionf
*///?}
import net.minecraft.core.Holder
//? if >= 1.21.10 {
import net.minecraft.world.level.chunk.PalettedContainerFactory
//?} else {
/*import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
*///?}

//? if >= 1.21.10 {
class LobbyContainers(private val factory: PalettedContainerFactory) {
    val blockCodec: Codec<PalettedContainer<BlockState>> get() = factory.blockStatesContainerCodec()
    val biomeCodec: Codec<PalettedContainerRO<Holder<Biome>>> get() = factory.biomeContainerCodec()

    fun emptyBlocks(): PalettedContainer<BlockState> = factory.createForBlockStates()

    fun emptySection() = LevelChunkSection(factory)
}
//?} else {
/*class LobbyContainers(private val biomes: Registry<Biome>) {
    val blockCodec: Codec<PalettedContainer<BlockState>> =
        PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState())
    val biomeCodec: Codec<PalettedContainerRO<Holder<Biome>>> =
        PalettedContainer.codecRO(biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, plains())

    fun emptyBlocks(): PalettedContainer<BlockState> =
        PalettedContainer(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES)

    fun emptySection() = LevelChunkSection(biomes)

    //? if >= 1.21.4 {
    /^private fun plains(): Holder<Biome> = biomes.getOrThrow(Biomes.PLAINS)
    ^///?} else {
    /^private fun plains(): Holder<Biome> = biomes.getHolderOrThrow(Biomes.PLAINS)
    ^///?}
}
*///?}

//? if < 1.21.4 {
/*val LevelHeightAccessor.minY: Int get() = minBuildHeight
val LevelHeightAccessor.minSectionY: Int get() = minSection
val LevelHeightAccessor.maxSectionY: Int get() = maxSection - 1
*///?}

object LobbyCompat {
    @Volatile
    private var quiet = false

    @JvmStatic
    fun quietConnection(): Boolean = quiet

    fun <T> quietly(block: () -> T): T {
        quiet = true
        try {
            return block()
        } finally {
            quiet = false
        }
    }

    fun offlineConnection(): Connection {
        val connection = Connection(PacketFlow.CLIENTBOUND)
        (connection as ConnectionAccessor).`polyplus$setAddress`(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        return connection
    }

    fun blockAt(point: Vec3): BlockPos = BlockPos.containing(point)

    fun blockAt(x: Double, y: Double, z: Double): BlockPos = BlockPos.containing(x, y, z)

    fun between(from: BlockPos, to: BlockPos): Iterable<BlockPos> = BlockPos.betweenClosed(from, to)

    fun mainExecutor(mc: Minecraft): Executor = mc

    fun gameTime(level: ClientLevel): Long = level.gameTime

    fun removeEntity(level: ClientLevel, entity: Entity) = level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)

    fun mouseX(mc: Minecraft): Double = mc.mouseHandler.xpos()

    fun mouseY(mc: Minecraft): Double = mc.mouseHandler.ypos()

    fun sectionsDone(mc: Minecraft): Boolean = mc.levelRenderer.hasRenderedAllSections() && levelSink(mc).countRenderedSections() > 0

    fun renderedSections(mc: Minecraft): Int = levelSink(mc).countRenderedSections()

    private const val SODIUM_HAS_LIGHT_DATA = 2

    private val sodiumTracker: Pair<Method, Method>? by lazy {
        runCatching {
            val holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder")
            val tracker = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker")
            holder.getMethod("get", ClientLevel::class.java) to
                tracker.getMethod("onChunkStatusAdded", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    fun lightReady(level: ClientLevel, x: Int, z: Int) {
        val (get, added) = sodiumTracker ?: return
        added.invoke(get.invoke(null, level), x, z, SODIUM_HAS_LIGHT_DATA)
    }

    fun requeueChunks(mc: Minecraft, level: ClientLevel, centerX: Int, centerZ: Int, radius: Int) {
        val cache = level.chunkSource
        for (x in centerX - radius..centerX + radius) {
            for (z in centerZ - radius..centerZ + radius) {
                val chunk = cache.getChunk(x, z, ChunkStatus.FULL, false) ?: continue
                //? if >= 26.2 {
                cache.addedLoadedChunks().add(chunkKey(x, z))
                chunk.sections.forEachIndexed { index, section ->
                    cache.onSectionEmptinessChanged(x, level.getSectionYFromSectionIndex(index), z, section.hasOnlyAir())
                }
                //?} elif >= 1.21.4 {
                /*mc.levelRenderer.onChunkReadyToRender(chunk.pos)
                *///?} else {
                /*mc.levelRenderer.onChunkLoaded(chunk.pos)
                *///?}
            }
        }
    }

    //? if >= 1.21.10 {
    fun containers(level: ClientLevel) = LobbyContainers(level.palettedContainerFactory())
    //?} else {
    /*fun containers(level: ClientLevel) = LobbyContainers(biomeRegistry(level))
    *///?}

    //? if < 1.21.4 {
    /*private fun biomeRegistry(level: ClientLevel): Registry<Biome> = level.registryAccess().registryOrThrow(Registries.BIOME)

    fun copySection(section: LevelChunkSection) = LevelChunkSection(section.states.copy(), section.biomes)
    *///?} elif < 1.21.10 {
    /*private fun biomeRegistry(level: ClientLevel): Registry<Biome> = level.registryAccess().lookupOrThrow(Registries.BIOME)

    fun copySection(section: LevelChunkSection): LevelChunkSection = section.copy()
    *///?} else {
    fun copySection(section: LevelChunkSection): LevelChunkSection = section.copy()
    //?}

    //? if >= 26.2 {
    fun levelSink(mc: Minecraft): LevelExtractor = mc.levelExtractor

    fun camera(renderer: GameRenderer): Camera = renderer.mainCamera()

    fun renderState(renderer: GameRenderer): GameRenderState = renderer.gameRenderState()

    fun mainTarget(renderer: GameRenderer): RenderTarget = renderer.mainRenderTarget()

    fun extractLevel(mc: Minecraft, deltaTracker: DeltaTracker, camera: Camera, partialTicks: Float) = mc.levelExtractor.extract(deltaTracker, camera, partialTicks)
    //?} elif >= 26.1 {
    /*fun levelSink(mc: Minecraft): LevelRenderer = mc.levelRenderer

    fun camera(renderer: GameRenderer): Camera = renderer.mainCamera

    fun renderState(renderer: GameRenderer): GameRenderState = renderer.gameRenderState

    fun mainTarget(renderer: GameRenderer): RenderTarget = Minecraft.getInstance().mainRenderTarget

    fun extractLevel(mc: Minecraft, deltaTracker: DeltaTracker, camera: Camera, partialTicks: Float) = mc.levelRenderer.extractLevel(deltaTracker, camera, partialTicks)
    *///?} else {
    /*fun levelSink(mc: Minecraft): LevelRenderer = mc.levelRenderer

    fun camera(renderer: GameRenderer): Camera = renderer.mainCamera

    fun mainTarget(renderer: GameRenderer): RenderTarget = Minecraft.getInstance().mainRenderTarget
    *///?}

    //? if >= 26.1 {
    fun setGameTime(level: ClientLevel, time: Long) = level.setTimeFromServer(time)

    fun clockTime(level: ClientLevel): Long = level.defaultClockTime
    //?} else {
    /*fun clockTime(level: ClientLevel): Long = level.dayTime
    *///?}

    //? if >= 1.21.4 && < 26.1 {
    /*fun setGameTime(level: ClientLevel, time: Long) = level.setTimeFromServer(time, level.dayTime, false)

    fun setDayTime(level: ClientLevel, time: Long) = level.setTimeFromServer(0L, time, false)
    *///?} elif < 1.21.4 {
    /*fun setGameTime(level: ClientLevel, time: Long) = level.setGameTime(time)

    fun setDayTime(level: ClientLevel, time: Long) {
        level.setGameTime(0L)
        level.setDayTime(time)
    }
    *///?}

    //? if >= 26.1 {
    fun chunkOf(pos: BlockPos): ChunkPos = ChunkPos.containing(pos)

    fun chunkKey(x: Int, z: Int): Long = ChunkPos.pack(x, z)
    //?} else {
    /*fun chunkOf(pos: BlockPos): ChunkPos = ChunkPos(pos)

    fun chunkKey(x: Int, z: Int): Long = ChunkPos.asLong(x, z)
    *///?}

    //? if >= 1.21.5 {
    fun blockEntityPos(chunk: ChunkPos, tag: CompoundTag): BlockPos = BlockEntity.getPosFromTag(chunk, tag)

    fun snapEntity(entity: Entity, pos: Vec3, yaw: Float) = entity.snapTo(pos.x, pos.y, pos.z, yaw, 0f)
    //?} else {
    /*fun blockEntityPos(chunk: ChunkPos, tag: CompoundTag): BlockPos = BlockEntity.getPosFromTag(tag)

    fun snapEntity(entity: Entity, pos: Vec3, yaw: Float) = entity.moveTo(pos.x, pos.y, pos.z, yaw, 0f)
    *///?}

    //? if >= 1.21.8 {
    fun dataVersion(): Int = SharedConstants.getCurrentVersion().dataVersion().version()

    fun protocolVersion(): Int = SharedConstants.getCurrentVersion().protocolVersion()

    fun project(renderer: GameRenderer, point: Vec3, fov: Float): Vec3 = renderer.projectPointToScreen(point)
    //?} else {
    /*fun dataVersion(): Int = SharedConstants.getCurrentVersion().dataVersion.version

    fun protocolVersion(): Int = SharedConstants.getCurrentVersion().protocolVersion

    fun project(renderer: GameRenderer, point: Vec3, fov: Float): Vec3 {
        val view = Matrix4f().rotation(camera(renderer).rotation().conjugate(Quaternionf()))
        val offset = point.subtract(cameraPosition(renderer))
        //? if >= 1.21.4 {
        /^return Vec3(renderer.getProjectionMatrix(fov).mul(view).transformProject(offset.toVector3f()))
        ^///?} else {
        /^return Vec3(renderer.getProjectionMatrix(fov.toDouble()).mul(view).transformProject(offset.toVector3f()))
        ^///?}
    }
    *///?}

    //? if >= 1.21.11 {
    fun cameraPosition(renderer: GameRenderer): Vec3 = camera(renderer).position()
    //?} else {
    /*fun cameraPosition(renderer: GameRenderer): Vec3 = camera(renderer).position
    *///?}
}
//?}
