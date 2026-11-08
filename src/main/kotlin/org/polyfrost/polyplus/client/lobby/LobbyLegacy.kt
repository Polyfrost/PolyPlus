package org.polyfrost.polyplus.client.lobby

//? if = 1.8.9 {
/*import net.minecraft.block.AbstractLeavesBlock
import net.minecraft.block.AbstractLogBlock
import net.minecraft.block.BedBlock
import net.minecraft.block.Block
import net.minecraft.block.CactusBlock
import net.minecraft.block.CakeBlock
import net.minecraft.block.CarpetBlock
import net.minecraft.block.ColoredBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FarmlandBlock
import net.minecraft.block.FenceBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.PaneBlock
import net.minecraft.block.PlanksBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.SnowLayerBlock
import net.minecraft.block.SoulSandBlock
import net.minecraft.block.StairsBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.block.WallBlock
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.ClientPlayerInteractionManager
import net.minecraft.client.ParticleManager
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity
import net.minecraft.client.network.handler.ClientPlayNetworkHandler
import net.minecraft.client.options.GameOptions
import net.minecraft.client.options.ServerListEntry
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.world.WorldRenderer
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.particle.ParticleType
import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.stat.PlayerStats
import net.minecraft.util.math.ChunkPos
import org.lwjgl.input.Mouse
import org.polyfrost.polyplus.mixin.client.access.GameRendererAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldRendererAccessor
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.network.Connection
import net.minecraft.network.PacketFlow
import org.polyfrost.polyplus.mixin.client.access.ConnectionAccessor
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import java.net.InetAddress
import java.net.InetSocketAddress
import net.minecraft.entity.Entity
import net.minecraft.entity.living.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.LightType
import net.minecraft.world.WorldView
import net.minecraft.world.biome.Biome
import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import net.minecraft.resource.Identifier
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import net.minecraft.block.Blocks as FeatherBlocks

object LobbyUtil {
    private val pool = Executors.newCachedThreadPool { task -> Thread(task, "PolyPlus Lobby IO").apply { isDaemon = true } }

    fun getNanos(): Long = System.nanoTime()

    fun ioPool(): Executor = pool
}

class LobbyAccountSkin {
    @Volatile
    var texture: Identifier? = null
    @Volatile
    var slim: Boolean? = null
    @Volatile
    var cape: Identifier? = null
}

object LobbyAccountSkins {
    private val loaded = ConcurrentHashMap<UUID, LobbyAccountSkin>()

    fun of(uuid: UUID, name: String, local: Boolean): LobbyAccountSkin =
        loaded.computeIfAbsent(uuid) { LobbyAccountSkin().also { load(it, uuid, name, local) } }

    private fun load(skin: LobbyAccountSkin, uuid: UUID, name: String, local: Boolean) {
        val mc = Minecraft.getInstance()
        LobbyUtil.ioPool().execute {
            val profile = runCatching {
                if (local) mc.session.profile else mc.sessionService.fillProfileProperties(GameProfile(uuid, name), false)
            }.getOrNull() ?: return@execute
            runCatching {
                mc.skinManager.register(profile, { type, location, texture ->
                    when (type) {
                        MinecraftProfileTexture.Type.SKIN -> {
                            skin.slim = texture.getMetadata("model") == "slim"
                            skin.texture = location
                        }
                        MinecraftProfileTexture.Type.CAPE -> skin.cape = location
                        else -> {}
                    }
                }, false)
            }
        }
    }
}

object LobbyMth {
    fun clamp(value: Float, low: Float, high: Float): Float = value.coerceIn(low, high)

    fun clamp(value: Double, low: Double, high: Double): Double = value.coerceIn(low, high)

    fun clamp(value: Int, low: Int, high: Int): Int = value.coerceIn(low, high)

    fun lerp(amount: Float, from: Float, to: Float): Float = from + (to - from) * amount

    fun wrapDegrees(value: Float): Float = MathHelper.wrapDegrees(value)

    fun wrapDegrees(value: Double): Double = MathHelper.wrapDegrees(value)

    fun floor(value: Double): Int = MathHelper.floor(value)

    fun floor(value: Float): Int = MathHelper.floor(value)

    fun ceil(value: Double): Int = MathHelper.ceil(value)
}

object LobbySectionPos {
    const val SECTION_BITS = 4

    fun blockToSectionCoord(block: Int): Int = block shr SECTION_BITS
}

object LobbyBlocks {
    val AIR: Block = FeatherBlocks.AIR
    val LAVA: Block = FeatherBlocks.LAVA
    val FLOWING_LAVA: Block = FeatherBlocks.FLOWING_LAVA
    val SNOW: Block = FeatherBlocks.SNOW_LAYER
    val CACTUS: Block = FeatherBlocks.CACTUS
    val MAGMA_BLOCK: Block = FeatherBlocks.FLOWING_LAVA
    val SPRUCE_FENCE: Block = FeatherBlocks.SPRUCE_FENCE
    val LANTERN: Block = FeatherBlocks.TORCH
    val TORCH: Block = FeatherBlocks.TORCH
    val GLASS: Block = FeatherBlocks.GLASS
    val GLASS_PANE: Block = FeatherBlocks.GLASS_PANE
    val BRICKS: Block = FeatherBlocks.BRICKS
    val STONE_BRICKS: Block = FeatherBlocks.STONE_BRICKS
    val COBBLESTONE: Block = FeatherBlocks.COBBLESTONE
}

class LobbyTag(val matches: (Block) -> Boolean)

object LobbyBlockTags {
    val LEAVES = LobbyTag { it is AbstractLeavesBlock }
    val LOGS = LobbyTag { it is AbstractLogBlock }
    val FENCES = LobbyTag { it is FenceBlock || it is FenceGateBlock }
    val WALLS = LobbyTag { it is WallBlock }
    val CAMPFIRES = LobbyTag { false }
    val PLANKS = LobbyTag { it is PlanksBlock }
    val STAIRS = LobbyTag { it is StairsBlock }
    val SLABS = LobbyTag { it is SlabBlock }
    val DOORS = LobbyTag { it is DoorBlock }
    val TRAPDOORS = LobbyTag { it is TrapdoorBlock }
    val WOOL = LobbyTag { it === FeatherBlocks.WOOL || it is ColoredBlock }
    val BEDS = LobbyTag { it is BedBlock }
}

enum class LobbyPose { STANDING, CROUCHING }

class LobbyFluid(val isEmpty: Boolean)

class LobbyShape(private val top: Double, private val narrow: Boolean = false) {
    val isEmpty: Boolean get() = top <= 0.0

    fun max(axis: Direction.Axis): Double = if (axis == Direction.Axis.Y) top else if (narrow) NARROW_MAX else 1.0

    fun min(axis: Direction.Axis): Double = if (axis != Direction.Axis.Y && narrow) NARROW_MIN else 0.0

    private companion object {
        const val NARROW_MIN = 0.4375
        const val NARROW_MAX = 0.5625
    }
}

class LobbyHit(val type: Type, val location: Vec3d, val blockPos: BlockPos) {
    enum class Type { MISS, BLOCK }
}

object LobbyHeightmap {
    enum class Types { WORLD_SURFACE, MOTION_BLOCKING }
}

val BlockState.isAir: Boolean get() = block.material === Material.AIR

val BlockState.lightEmission: Int get() = block.light

val BlockState.fluidState: LobbyFluid get() = LobbyFluid(!block.material.isLiquid)

fun BlockState.`is`(tag: LobbyTag): Boolean = tag.matches(block)

fun BlockState.`is`(other: Block): Boolean = block === other || (other === LobbyBlocks.LAVA && block === LobbyBlocks.FLOWING_LAVA)

fun BlockState.canBeReplaced(): Boolean = block.material.isReplaceable

fun Block.defaultBlockState(): BlockState = defaultState()

fun WorldView.getFluidState(pos: BlockPos): LobbyFluid = getBlockState(pos).fluidState

fun BlockState.getCollisionShape(level: WorldView, pos: BlockPos): LobbyShape = LobbyShape(collisionTop(this), block is PaneBlock)

private fun collisionTop(state: BlockState): Double {
    val block = state.block
    return when {
        !block.material.blocksMovement() && block !is SnowLayerBlock -> 0.0
        block is SnowLayerBlock -> (block.getMetadataFromState(state) and 7) / 8.0
        block is SlabBlock -> if (block.isDouble) 1.0 else 0.5
        block is CarpetBlock -> 0.0625
        block is FarmlandBlock -> 0.9375
        block is SoulSandBlock -> 0.875
        block is CactusBlock -> 0.9375
        block is BedBlock -> 0.5625
        block is CakeBlock -> 0.5
        block is FenceBlock || block is WallBlock || block is FenceGateBlock -> 1.5
        else -> 1.0
    }
}

object LobbyClip {
    private const val MAX_CELLS = 400

    fun clip(level: WorldView, from: Vec3d, to: Vec3d): LobbyHit {
        var x = floor(from.x).toInt()
        var y = floor(from.y).toInt()
        var z = floor(from.z).toInt()
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val deltaZ = to.z - from.z
        val stepX = if (deltaX > 0) 1 else -1
        val stepY = if (deltaY > 0) 1 else -1
        val stepZ = if (deltaZ > 0) 1 else -1
        val spanX = if (deltaX == 0.0) Double.MAX_VALUE else 1.0 / abs(deltaX)
        val spanY = if (deltaY == 0.0) Double.MAX_VALUE else 1.0 / abs(deltaY)
        val spanZ = if (deltaZ == 0.0) Double.MAX_VALUE else 1.0 / abs(deltaZ)
        var nextX = first(from.x, deltaX, x, spanX)
        var nextY = first(from.y, deltaY, y, spanY)
        var nextZ = first(from.z, deltaZ, z, spanZ)
        repeat(MAX_CELLS) {
            hit(level, x, y, z, from, deltaX, deltaY, deltaZ)?.let { return it }
            if (nextX > 1.0 && nextY > 1.0 && nextZ > 1.0) return miss(to)
            when {
                nextX <= nextY && nextX <= nextZ -> { x += stepX; nextX += spanX }
                nextY <= nextZ -> { y += stepY; nextY += spanY }
                else -> { z += stepZ; nextZ += spanZ }
            }
        }
        return miss(to)
    }

    private fun first(start: Double, delta: Double, cell: Int, span: Double): Double = when {
        delta > 0 -> (cell + 1 - start) * span
        delta < 0 -> (start - cell) * span
        else -> Double.MAX_VALUE
    }

    private fun miss(to: Vec3d) = LobbyHit(LobbyHit.Type.MISS, to, BlockPos(to))

    private fun hit(level: WorldView, x: Int, y: Int, z: Int, from: Vec3d, deltaX: Double, deltaY: Double, deltaZ: Double): LobbyHit? {
        val pos = BlockPos(x, y, z)
        val top = collisionTop(level.getBlockState(pos))
        if (top <= 0.0) return null
        var near = 0.0
        var far = 1.0
        val bounds = arrayOf(
            Triple(from.x, deltaX, x.toDouble() to x + 1.0),
            Triple(from.y, deltaY, y.toDouble() to y + min(top, 1.0)),
            Triple(from.z, deltaZ, z.toDouble() to z + 1.0),
        )
        for ((start, delta, range) in bounds) {
            if (delta == 0.0) {
                if (start < range.first || start > range.second) return null
                continue
            }
            val a = (range.first - start) / delta
            val b = (range.second - start) / delta
            near = max(near, min(a, b))
            far = min(far, max(a, b))
            if (near > far) return null
        }
        return LobbyHit(LobbyHit.Type.BLOCK, from.add(deltaX * near, deltaY * near, deltaZ * near), pos)
    }
}

fun Vec3d.scale(amount: Double): Vec3d = Vec3d(x * amount, y * amount, z * amount)

fun Vec3d.lerp(to: Vec3d, amount: Double): Vec3d = Vec3d(x + (to.x - x) * amount, y + (to.y - y) * amount, z + (to.z - z) * amount)

fun BlockPos.above(amount: Int = 1): BlockPos = up(amount)

fun BlockPos.below(amount: Int = 1): BlockPos = down(amount)

fun BlockPos.offset(dx: Int, dy: Int, dz: Int): BlockPos = add(dx, dy, dz)

fun BlockPos.relative(direction: Direction): BlockPos = offset(direction)

fun BlockPos.asLong(): Long = toLong()

fun BlockPos.toShortString(): String = "$x, $y, $z"

fun BlockPos.distManhattan(other: BlockPos): Int = abs(x - other.x) + abs(y - other.y) + abs(z - other.z)

var Entity.yRot: Float
    @JvmName("lobbyYRot") get() = yaw
    @JvmName("lobbyPutYRot") set(value) {
        yaw = value
    }

var Entity.xRot: Float
    @JvmName("lobbyXRot") get() = pitch
    @JvmName("lobbyPutXRot") set(value) {
        pitch = value
    }

var Entity.yRotO: Float
    get() = lastYaw
    set(value) {
        lastYaw = value
    }

var Entity.xRotO: Float
    get() = lastPitch
    set(value) {
        lastPitch = value
    }

var LivingEntity.yBodyRot: Float
    get() = bodyYaw
    set(value) {
        bodyYaw = value
    }

var LivingEntity.yBodyRotO: Float
    get() = lastBodyYaw
    set(value) {
        lastBodyYaw = value
    }

var LivingEntity.yHeadRot: Float
    get() = headYaw
    set(value) {
        headYaw = value
    }

var LivingEntity.yHeadRotO: Float
    get() = lastHeadYaw
    set(value) {
        lastHeadYaw = value
    }

val Entity.bbHeight: Float get() = height

var Entity.id: Int
    get() = networkId
    set(value) {
        setNetworkId(value)
    }

val Entity.uuid: UUID get() = getUuid()

var Entity.pose: LobbyPose
    get() = if (isSneaking) LobbyPose.CROUCHING else LobbyPose.STANDING
    set(value) {
        isSneaking = value == LobbyPose.CROUCHING
    }

fun Entity.position(): Vec3d = Vec3d(x, y, z)

fun Entity.setPos(x: Double, y: Double, z: Double) = setPosition(x, y, z)

fun Entity.setPos(pos: Vec3d) = setPosition(pos.x, pos.y, pos.z)

fun Entity.setYRot(value: Float) {
    yaw = value
}

fun Entity.setXRot(value: Float) {
    pitch = value
}

fun Entity.setOldPosAndRot() {
    prevX = x
    prevY = y
    prevZ = z
    lastX = x
    lastY = y
    lastZ = z
    lastYaw = yaw
    lastPitch = pitch
}

fun Entity.blockPosition(): BlockPos = BlockPos(this)

fun Entity.onGround(): Boolean = onGround

val Entity.isVehicle: Boolean get() = rider != null

val Entity.isPassenger: Boolean get() = vehicle != null

fun Biome.value(): Biome = this

val Biome.baseTemperature: Float get() = temperature

fun Biome.coldEnoughToSnow(pos: BlockPos): Boolean = getTemperature(pos) < 0.15f

val ClientWorld.minY: Int get() = 0

val ClientWorld.height: Int get() = 256

val ClientWorld.minSectionY: Int get() = 0

val ClientWorld.maxSectionY: Int get() = 15

fun ClientWorld.hasChunk(chunkX: Int, chunkZ: Int): Boolean = chunkSource.hasChunk(chunkX, chunkZ)

fun ClientWorld.getBrightness(type: LightType, pos: BlockPos): Int = getLight(type, pos)

fun ClientWorld.setBlock(pos: BlockPos, state: BlockState, flags: Int) {
    setBlockState(pos, state, flags)
}

fun ClientWorld.getHeight(type: LobbyHeightmap.Types, x: Int, z: Int): Int {
    val chunk = getChunk(BlockPos(x, 0, z))
    val cursor = BlockPos.Mutable()
    var y = chunk.highestSectionOffset + 15
    while (y >= 0) {
        cursor.set(x, y, z)
        val state = chunk.getBlockState(cursor)
        val solid = if (type == LobbyHeightmap.Types.WORLD_SURFACE) !state.isAir else state.block.material.blocksMovement() || state.block.material.isLiquid
        if (solid) return y + 1
        y--
    }
    return 0
}

object LobbyCompat {
    private const val LEGACY_DATA_VERSION = 47
    private const val SNOW_LIFE = 0.015

    private var cameraPoint = Vec3d(0.0, 0.0, 0.0)
    private var viewLook = Vec3d(0.0, 0.0, 1.0)
    private var viewRight = Vec3d(1.0, 0.0, 0.0)
    private var viewFov = 70f

    fun blockAt(point: Vec3d): BlockPos = BlockPos(point)

    fun blockAt(x: Double, y: Double, z: Double): BlockPos = BlockPos(x, y, z)

    fun between(from: BlockPos, to: BlockPos): Iterable<BlockPos> = BlockPos.iterateRegion(from, to)

    fun levelSink(mc: Minecraft): WorldRenderer = mc.worldRenderer

    fun mainTarget(renderer: GameRenderer): RenderTarget = Minecraft.getInstance().renderTarget

    fun setGameTime(level: ClientWorld, time: Long) {
        level.time = time
    }

    fun setDayTime(level: ClientWorld, time: Long) = level.setTimeOfDay(time)

    fun clockTime(level: ClientWorld): Long = level.timeOfDay

    fun chunkOf(pos: BlockPos): ChunkPos = ChunkPos(pos.x shr LobbySectionPos.SECTION_BITS, pos.z shr LobbySectionPos.SECTION_BITS)

    fun chunkKey(x: Int, z: Int): Long = ChunkPos.toLong(x, z)

    fun snapEntity(entity: Entity, pos: Vec3d, yaw: Float) = entity.setPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f)

    fun dataVersion(): Int = LEGACY_DATA_VERSION

    fun protocolVersion(): Int = LEGACY_DATA_VERSION

    fun pose(rig: Entity, look: Vec3d, right: Vec3d, fov: Float) {
        cameraPoint = Vec3d(rig.x, rig.y + rig.eyeHeight, rig.z)
        viewLook = look
        viewRight = right
        viewFov = fov
        val up = right.cross(look)
        LobbyLegacyRender.pose(floats(look), floats(right), floats(up))
    }

    private fun floats(vector: Vec3d) = floatArrayOf(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat())

    fun cameraPosition(renderer: GameRenderer): Vec3d = cameraPoint

    fun project(renderer: GameRenderer, point: Vec3d, fov: Float): Vec3d {
        val mc = Minecraft.getInstance()
        val aspect = mc.width.toDouble() / mc.height.coerceAtLeast(1)
        val tanHalf = tan(Math.toRadians(fov / 2.0))
        val up = viewRight.cross(viewLook)
        val offset = point.subtract(cameraPoint)
        val depth = offset.dot(viewLook).coerceAtLeast(0.1)
        return Vec3d(offset.dot(viewRight) / (depth * tanHalf * aspect), offset.dot(up) / (depth * tanHalf), 0.0)
    }

    fun joiningWorld(mc: Minecraft): Boolean = mc.world == null && (mc as MinecraftAccessor).`polyplus$getConnection`()?.isConnected == true

    fun mouseX(mc: Minecraft): Double = Mouse.getX().toDouble()

    fun mouseY(mc: Minecraft): Double = (mc.height - Mouse.getY()).toDouble()

    fun sectionsDone(mc: Minecraft): Boolean = (mc.worldRenderer as WorldRendererAccessor).`polyplus$dirtyChunks`().isEmpty()

    fun renderedSections(mc: Minecraft): String = mc.worldRenderer.chunkDebugInfo

    fun createRig(mc: Minecraft, level: ClientWorld, listener: ClientPlayNetworkHandler): LocalClientPlayerEntity =
        LocalClientPlayerEntity(mc, level, listener, PlayerStats())

    fun blockDust(engine: ParticleManager, state: BlockState, x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double) {
        engine.addParticle(ParticleType.BLOCK_DUST.id, x, y, z, vx, vy, vz, Block.serialize(state))
    }

    fun snowflake(engine: ParticleManager, x: Double, y: Double, z: Double) {
        engine.addParticle(ParticleType.SNOW_SHOVEL.id, x, y, z, SNOW_LIFE, -0.035, 0.01)
    }

    fun offlineConnection(): Connection {
        val connection = Connection(PacketFlow.CLIENTBOUND)
        (connection as ConnectionAccessor).`polyplus$setAddress`(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        return connection
    }

    fun mainExecutor(mc: Minecraft): Executor = Executor { task -> mc.executeTask(task) }

    fun gameTime(level: ClientWorld): Long = level.time

    fun removeEntity(level: ClientWorld, entity: Entity) = level.removeEntity(entity)
}

var Minecraft.level: ClientWorld?
    get() = world
    set(value) {
        world = value
    }

var Minecraft.gameMode: ClientPlayerInteractionManager?
    get() = interactionManager
    set(value) {
        interactionManager = value
    }

val Minecraft.particleEngine: ParticleManager get() = particleManager

val Minecraft.mouseHandler: LobbyMouseHandler get() = LobbyMouseHandler

object LobbyMouseHandler {
    fun xpos(): Double = Mouse.getX().toDouble()

    fun ypos(): Double = (Minecraft.getInstance().height - Mouse.getY()).toDouble()
}

val Minecraft.levelRenderer: WorldRenderer get() = worldRenderer

val Minecraft.gameDirectory: File get() = gameDir

fun Minecraft.execute(task: () -> Unit) {
    executeTask(Runnable(task))
}

val GameOptions.effectiveRenderDistance: Int get() = viewDistance

val Minecraft.singleplayerServer: IntegratedServer? get() = server

val Minecraft.currentServer: ServerListEntry? get() = currentServerEntry

fun Minecraft.setCameraEntity(entity: Entity?) = setCamera(entity)

val Minecraft.cameraEntity: Entity? get() = camera

fun GameRenderer.setRenderBlockOutline(show: Boolean) {
    (this as GameRendererAccessor).`polyplus$setRenderBlockOutline`(show)
}
*///?}
