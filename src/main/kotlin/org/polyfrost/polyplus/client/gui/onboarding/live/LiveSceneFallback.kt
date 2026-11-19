package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sin

internal object LiveSceneFallback {
    private val built: RenderBlocksScene by lazy { RenderBlocksScene(blocks(), RenderBlocksLight { LightCoordsUtil.pack(0, FULL) }) }

    fun scene(): RenderBlocksScene = built

    fun place(spot: LiveSpot): LiveSpotPlace = when (spot) {
        LiveSpot.STANDING -> LiveSpotPlace(Vec3(STAND_X + HALF, GROUND + 1.0, STAND_Z + HALF), STAND_YAW, 0.0)
        LiveSpot.RIDING_PATH -> LiveSpotPlace(Vec3(HALF, GROUND + 1.0, PATH_START + HALF), 0f, (PATH_END - PATH_START).toDouble())
    }

    private fun blocks(): Map<BlockPos, BlockState> {
        val blocks = HashMap<BlockPos, BlockState>()
        for (x in -RADIUS..RADIUS) for (z in PATH_START - RADIUS..PATH_END + RADIUS) {
            val onPath = abs(x) <= PATH_HALF_WIDTH && z in PATH_START - PATH_HALF_WIDTH..PATH_END
            val top = if (onPath) GROUND else GROUND + rise(x, z)
            for (y in GROUND - DEPTH until top) blocks[BlockPos(x, y, z)] = Blocks.DIRT.defaultBlockState()
            blocks[BlockPos(x, top, z)] = if (onPath) Blocks.DIRT_PATH.defaultBlockState() else Blocks.GRASS_BLOCK.defaultBlockState()
            if (!onPath && abs(x) > PATH_HALF_WIDTH + 1) decorate(blocks, x, top, z)
        }
        TREES.forEach { (x, z) -> tree(blocks, x, GROUND + rise(x, z) + 1, z) }
        house(blocks, HOUSE_X, PATH_END + 2)
        house(blocks, -HOUSE_X - HOUSE_SIZE + 1, PATH_END - HOUSE_SIZE)
        return blocks
    }

    private fun rise(x: Int, z: Int): Int {
        val wave = sin(x * WAVE_X) + sin(z * WAVE_Z + x * WAVE_MIX)
        val far = (abs(x) - PATH_CLEAR).coerceAtLeast(0) / HILL_STEP
        return (wave * WAVE_HEIGHT).toInt().coerceAtLeast(0) + far.coerceAtMost(HILL_MAX)
    }

    private fun decorate(blocks: MutableMap<BlockPos, BlockState>, x: Int, top: Int, z: Int) {
        val hash = (x * HASH_X + z * HASH_Z) and HASH_MASK
        val plant: Block = when {
            hash % FLOWER_RATE == 0 -> if (hash % 2 == 0) Blocks.DANDELION else Blocks.POPPY
            hash % GRASS_RATE == 0 -> Blocks.SHORT_GRASS
            else -> return
        }
        blocks[BlockPos(x, top + 1, z)] = plant.defaultBlockState()
    }

    private fun tree(blocks: MutableMap<BlockPos, BlockState>, x: Int, base: Int, z: Int) {
        for (dx in -2..2) for (dz in -2..2) for (dy in TRUNK - 2..TRUNK) {
            val corner = abs(dx) == 2 && abs(dz) == 2
            if (corner || (dy == TRUNK && (abs(dx) == 2 || abs(dz) == 2))) continue
            blocks[BlockPos(x + dx, base + dy, z + dz)] = Blocks.OAK_LEAVES.defaultBlockState()
        }
        blocks[BlockPos(x, base + TRUNK + 1, z)] = Blocks.OAK_LEAVES.defaultBlockState()
        for (y in 0 until TRUNK) blocks[BlockPos(x, base + y, z)] = Blocks.OAK_LOG.defaultBlockState()
    }

    private fun house(blocks: MutableMap<BlockPos, BlockState>, x0: Int, z0: Int) {
        val floor = GROUND + 1
        for (dx in 0 until HOUSE_SIZE) for (dz in 0 until HOUSE_SIZE) {
            val edgeX = dx == 0 || dx == HOUSE_SIZE - 1
            val edgeZ = dz == 0 || dz == HOUSE_SIZE - 1
            for (dy in 0 until HOUSE_WALL) {
                val pos = BlockPos(x0 + dx, floor + dy, z0 + dz)
                blocks[pos] = when {
                    edgeX && edgeZ -> Blocks.OAK_LOG.defaultBlockState()
                    edgeX || edgeZ -> if (dy == 1 && (dx == HOUSE_SIZE / 2 || dz == HOUSE_SIZE / 2)) Blocks.GLASS_PANE.defaultBlockState() else Blocks.OAK_PLANKS.defaultBlockState()
                    else -> Blocks.AIR.defaultBlockState()
                }
            }
            blocks[BlockPos(x0 + dx, floor - 1, z0 + dz)] = Blocks.COBBLESTONE.defaultBlockState()
        }
        val north = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
        val south = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
        val ridge = HOUSE_SIZE / 2
        for (layer in 0..ridge) {
            val y = floor + HOUSE_WALL + layer
            for (dx in -1..HOUSE_SIZE) {
                if (layer == ridge) {
                    blocks[BlockPos(x0 + dx, y, z0 + ridge)] = Blocks.SPRUCE_PLANKS.defaultBlockState()
                } else {
                    blocks[BlockPos(x0 + dx, y, z0 - 1 + layer)] = north
                    blocks[BlockPos(x0 + dx, y, z0 + HOUSE_SIZE - layer)] = south
                }
            }
        }
        blocks.entries.removeIf { it.value.isAir }
    }

    private val TREES = listOf(-9 to 4, 11 to -2, -14 to 18, 13 to 24, -7 to -12, 16 to 9, -18 to 33, 9 to 40)
    private const val FULL = 15
    private const val GROUND = 64
    private const val DEPTH = 2
    private const val RADIUS = 40
    private const val PATH_START = -6
    private const val PATH_END = 44
    private const val PATH_HALF_WIDTH = 1
    private const val PATH_CLEAR = 6
    private const val HILL_STEP = 7
    private const val HILL_MAX = 3
    private const val WAVE_X = 0.21
    private const val WAVE_Z = 0.17
    private const val WAVE_MIX = 0.05
    private const val WAVE_HEIGHT = 0.8
    private const val STAND_X = 6
    private const val STAND_Z = 6
    private const val STAND_YAW = 200f
    private const val HALF = 0.5
    private const val TRUNK = 5
    private const val HOUSE_X = 4
    private const val HOUSE_SIZE = 7
    private const val HOUSE_WALL = 4
    private const val HASH_X = 73856093
    private const val HASH_Z = 19349663
    private const val HASH_MASK = 0x7FFFFFFF
    private const val FLOWER_RATE = 23
    private const val GRASS_RATE = 5
}
//?}
