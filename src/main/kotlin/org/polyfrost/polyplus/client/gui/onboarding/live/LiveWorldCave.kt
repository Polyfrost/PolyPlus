package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import net.minecraft.core.BlockPos
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import kotlin.math.sin

internal class LiveWorldCave(label: String, var brightness: Float) : RenderBlocksDiorama(label) {
    override val scene = RenderBlocksScene(caveBlocks(), caveLight())

    override fun view(time: Float, aspect: Float) = RenderBlocksView.fit(
        SCENE_CENTER,
        SCENE_RADIUS,
        BASE_YAW + sin(time * SWAY_SPEED) * SWAY_DEG,
        BASE_PITCH,
        VIEW_FOV,
        aspect,
    )

    override fun lighting(time: Float) = RenderBlocksLighting(brightness = brightness)

    private companion object {
        val SCENE_CENTER = Vector3f(3f, 2.4f, 3f)
        const val SCENE_RADIUS = 5.6f
        const val VIEW_FOV = 28f
        const val BASE_YAW = -45f
        const val BASE_PITCH = 28f
        const val SWAY_SPEED = 0.35f
        const val SWAY_DEG = 7f
    }
}

private fun caveLight() = RenderBlocksLight { pos ->
    val sky = if (pos.y >= SURFACE_Y) FULL else (pos.x + pos.z - CAVE_MOUTH).coerceIn(0, MOUTH_SKY)
    LightCoordsUtil.pack(CAVE_FLOOR_LIGHT, sky)
}

private fun caveBlocks(): Map<BlockPos, BlockState> {
    val blocks = HashMap<BlockPos, BlockState>()
    for (x in 0 until SIZE) for (z in 0 until SIZE) {
        blocks[BlockPos(x, -1, z)] = Blocks.DEEPSLATE.defaultBlockState()
        blocks[BlockPos(x, 0, z)] = floorBlock(x, z)
    }
    for (i in 0 until SIZE) for (y in 1 until SURFACE_Y) {
        blocks[BlockPos(0, y, i)] = wallBlock(i, y, 0)
        blocks[BlockPos(i, y, 0)] = wallBlock(i, y, 1)
    }
    for (i in 0 until SIZE) {
        blocks[BlockPos(0, SURFACE_Y, i)] = Blocks.GRASS_BLOCK.defaultBlockState()
        blocks[BlockPos(i, SURFACE_Y, 0)] = Blocks.GRASS_BLOCK.defaultBlockState()
    }
    GRASS_TUFTS.forEach { (x, z) -> blocks[BlockPos(x, SURFACE_Y + 1, z)] = Blocks.SHORT_GRASS.defaultBlockState() }
    blocks[BlockPos(0, SURFACE_Y + 1, 2)] = Blocks.FERN.defaultBlockState()
    blocks[BlockPos(1, 1, 1)] = Blocks.COBBLESTONE.defaultBlockState()
    blocks[BlockPos(2, 1, 1)] = Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    blocks[BlockPos(1, 1, 2)] = Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    blocks[BlockPos(1, 2, 1)] = Blocks.COBBLESTONE.defaultBlockState()
    return blocks
}

private fun floorBlock(x: Int, z: Int): BlockState = when {
    (x == 3 && z == 4) || (x == 4 && z == 4) || (x == 4 && z == 3) -> Blocks.GRAVEL
    x == 2 && z == 3 -> Blocks.ANDESITE
    x == 5 && z == 1 -> Blocks.COBBLESTONE
    else -> Blocks.STONE
}.defaultBlockState()

private fun wallBlock(i: Int, y: Int, side: Int): BlockState = when {
    y == SURFACE_Y - 1 -> if ((i + side) % 3 == 0) Blocks.COARSE_DIRT else Blocks.DIRT
    side == 0 && y == 2 && i == 3 -> Blocks.COAL_ORE
    side == 0 && y == 1 && i == 4 -> Blocks.COAL_ORE
    side == 1 && y == 2 && i == 4 -> Blocks.IRON_ORE
    side == 1 && y == 3 && i == 2 -> Blocks.COPPER_ORE
    (i * 7 + y * 3 + side) % 5 == 0 -> Blocks.TUFF
    (i + y * 2 + side) % 4 == 0 -> Blocks.ANDESITE
    else -> Blocks.STONE
}.defaultBlockState()

private const val SIZE = 6
private const val SURFACE_Y = 5
private const val FULL = 15
private const val CAVE_MOUTH = 6
private const val MOUTH_SKY = 4
private const val CAVE_FLOOR_LIGHT = 1
private val GRASS_TUFTS = listOf(0 to 4, 3 to 0, 5 to 0, 0 to 0)
//?}
