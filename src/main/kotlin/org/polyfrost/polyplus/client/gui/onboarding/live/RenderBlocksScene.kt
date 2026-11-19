package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
//? if < 1.21.4 {
/*import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.chunk.LightChunk
import net.minecraft.world.level.chunk.LightChunkGetter
*///?}
import net.minecraft.world.level.material.FluidState
//? if >= 26.1 {
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.level.CardinalLighting
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockAndTintGetter
*///?}

internal fun interface RenderBlocksLight {
    fun packed(pos: BlockPos): Int

    companion object {
        fun constant(block: Int, sky: Int) = RenderBlocksLight { LightCoordsUtil.pack(block, sky) }
    }
}

internal class RenderBlocksScene(
    val blocks: Map<BlockPos, BlockState>,
    var light: RenderBlocksLight = RenderBlocksLight.constant(0, FULL_LIGHT),
) : BlockAndTintGetter {
    private val air = Blocks.AIR.defaultBlockState()

    //? if >= 26.1 {
    override fun cardinalLighting(): CardinalLighting = CardinalLighting.DEFAULT
    //?} else {
    /*override fun getShade(direction: Direction, shade: Boolean): Float = when {
        !shade -> 1f
        direction == Direction.DOWN -> SHADE_DOWN
        direction == Direction.UP -> 1f
        direction.axis == Direction.Axis.Z -> SHADE_NORTH_SOUTH
        else -> SHADE_EAST_WEST
    }
    *///?}

    override fun getBlockTint(pos: BlockPos, color: ColorResolver): Int = when {
        color === BiomeColors.GRASS_COLOR_RESOLVER -> PLAINS_GRASS
        color === BiomeColors.FOLIAGE_COLOR_RESOLVER -> PLAINS_FOLIAGE
        //? if >= 1.21.5
        color === BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER -> PLAINS_DRY_FOLIAGE
        color === BiomeColors.WATER_COLOR_RESOLVER -> PLAINS_WATER
        else -> -1
    }

    //? if >= 1.21.4 {
    override fun getLightEngine(): LevelLightEngine = LevelLightEngine.EMPTY
    //?} else {
    /*override fun getLightEngine(): LevelLightEngine = emptyLight

    private val emptyLight: LevelLightEngine by lazy {
        val scene = this
        LevelLightEngine(
            object : LightChunkGetter {
                override fun getChunkForLighting(x: Int, z: Int): LightChunk? = null

                override fun getLevel(): BlockGetter = scene
            },
            false,
            false,
        )
    }
    *///?}

    override fun getBrightness(layer: LightLayer, pos: BlockPos): Int {
        val packed = light.packed(pos)
        return if (layer == LightLayer.SKY) LightCoordsUtil.sky(packed) else LightCoordsUtil.block(packed)
    }

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    override fun getBlockState(pos: BlockPos): BlockState = blocks[pos] ?: air

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getHeight(): Int = HEIGHT

    //? if >= 1.21.4 {
    override fun getMinY(): Int = MIN_Y
    //?} else {
    /*override fun getMinBuildHeight(): Int = MIN_Y
    *///?}

    private companion object {
        const val FULL_LIGHT = 15
        const val HEIGHT = 384
        const val MIN_Y = -64
        const val PLAINS_GRASS = 0xFF91BD59.toInt()
        const val PLAINS_FOLIAGE = 0xFF77AB2F.toInt()
        const val PLAINS_DRY_FOLIAGE = 0xFFA17D4E.toInt()
        const val PLAINS_WATER = 0xFF3F76E4.toInt()
        //? if < 26.1 {
        /*const val SHADE_DOWN = 0.5f
        const val SHADE_NORTH_SOUTH = 0.8f
        const val SHADE_EAST_WEST = 0.6f
        *///?}
    }
}
//?}
