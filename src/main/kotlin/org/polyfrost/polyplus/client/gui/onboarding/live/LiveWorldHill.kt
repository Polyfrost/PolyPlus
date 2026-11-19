package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.core.BlockPos
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.sin

internal enum class LiveWorldShot { HILLSIDE, LOOK_AT, FIRST_PERSON }

internal fun interface LiveWorldHudContent {
    fun submit(collector: LiveHudCollector, time: Float)
}

internal class LiveWorldHill(label: String, private val shot: LiveWorldShot) : RenderBlocksDiorama(label) {
    var betterGrassMode: Int? = null
    var hud: LiveWorldHudContent? = null

    override val scene = RenderBlocksScene(hillBlocks(), RenderBlocksLight { LightCoordsUtil.pack(0, FULL) })
    private var hudPass: LiveWorldHud? = null

    override fun meshKey(time: Float): Any? = betterGrassMode

    fun isEmpty(pos: BlockPos): Boolean = scene.getBlockState(pos).isAir

    override fun buildMesh(mesh: RenderBlocksMesh) {
        val mode = betterGrassMode
        if (mode == null) {
            mesh.rebuild(scene)
            return
        }
        mesh.rebuild(scene, grassMode = mode)
    }

    override fun view(time: Float, aspect: Float): RenderBlocksView = when (shot) {
        LiveWorldShot.HILLSIDE -> RenderBlocksView.fit(
            HILLSIDE_CENTER, HILLSIDE_RADIUS, HILLSIDE_YAW + sin(time * SWAY_SPEED) * SWAY_DEG, HILLSIDE_PITCH, ORBIT_FOV, aspect,
        )
        LiveWorldShot.LOOK_AT -> RenderBlocksView.fit(
            LOOK_CENTER, LOOK_RADIUS, LOOK_YAW + sin(time * SWAY_SPEED) * SWAY_DEG, LOOK_PITCH, ORBIT_FOV, aspect,
        )
        LiveWorldShot.FIRST_PERSON -> RenderBlocksView(
            EYE, 0f, EYE_YAW + sin(time * IDLE_SPEED) * IDLE_YAW, EYE_PITCH + sin(time * IDLE_SPEED * 2f) * IDLE_PITCH, EYE_FOV,
        )
    }

    override fun backdrop(target: RenderTarget, aspect: Float, time: Float): Boolean {
        hudPass().backdrop(target, aspect)
        return true
    }

    override fun overlay(target: RenderTarget, aspect: Float, time: Float) {
        val content = hud ?: return
        hudPass().render(target, aspect) { content.submit(it, time) }
    }

    private fun hudPass(): LiveWorldHud = hudPass ?: LiveWorldHud("polyplus_live_hud").also { hudPass = it }

    override fun releaseExtra(): List<AutoCloseable> {
        val owned = listOfNotNull(hudPass)
        hudPass = null
        return owned
    }

    companion object {
        val TARGET: BlockPos = BlockPos(3, 2, 3)

        private val HILLSIDE_CENTER = Vector3f(3f, 2.6f, 3.4f)
        private const val HILLSIDE_RADIUS = 4.6f
        private const val HILLSIDE_YAW = -24f
        private const val HILLSIDE_PITCH = 30f
        private val LOOK_CENTER = Vector3f(TARGET.x + 0.5f, TARGET.y + 0.6f, TARGET.z + 0.5f)
        private const val LOOK_RADIUS = 2.1f
        private const val LOOK_YAW = -32f
        private const val LOOK_PITCH = 34f
        private const val ORBIT_FOV = 30f
        private const val SWAY_SPEED = 0.35f
        private const val SWAY_DEG = 6f

        private val EYE = Vector3f(2.5f, 3.62f, 9.3f)
        private const val EYE_YAW = 0f
        private const val EYE_PITCH = 12f
        private const val EYE_FOV = 70f
        private const val IDLE_SPEED = 0.6f
        private const val IDLE_YAW = 1.5f
        private const val IDLE_PITCH = 0.6f
    }
}

private fun hillBlocks(): Map<BlockPos, BlockState> {
    val blocks = HashMap<BlockPos, BlockState>()
    for (x in MIN_X..MAX_X) for (z in MIN_Z..MAX_Z) {
        val top = height(x, z)
        blocks[BlockPos(x, -1, z)] = Blocks.STONE.defaultBlockState()
        for (y in 0 until top) blocks[BlockPos(x, y, z)] = Blocks.DIRT.defaultBlockState()
        blocks[BlockPos(x, top, z)] = Blocks.GRASS_BLOCK.defaultBlockState()
    }
    PLANTS.forEach { (x, z, plant) -> blocks[BlockPos(x, height(x, z) + 1, z)] = plant.defaultBlockState() }
    val treeBase = height(TREE_X, TREE_Z) + 1
    for (y in 0 until TRUNK) blocks[BlockPos(TREE_X, treeBase + y, TREE_Z)] = Blocks.OAK_LOG.defaultBlockState()
    for (dx in -2..2) for (dz in -2..2) for (dy in TRUNK - 2..TRUNK) {
        val corner = abs(dx) == 2 && abs(dz) == 2
        if (corner || (dy == TRUNK && (abs(dx) == 2 || abs(dz) == 2))) continue
        val pos = BlockPos(TREE_X + dx, treeBase + dy, TREE_Z + dz)
        if (blocks[pos] == null) blocks[pos] = Blocks.OAK_LEAVES.defaultBlockState()
    }
    blocks[BlockPos(TREE_X, treeBase + TRUNK, TREE_Z)] = Blocks.OAK_LEAVES.defaultBlockState()
    return blocks
}

private fun height(x: Int, z: Int): Int {
    val ridge = when {
        z <= 0 -> RIDGE
        z >= RIDGE + 1 -> 1
        else -> RIDGE + 1 - z
    }
    val shoulder = if (x <= 0 && z in 1..2) 1 else 0
    val dip = if (x in 2..3 && z == 2) -1 else 0
    val far = if (z < -2 && (x + z) % 3 == 0) 1 else 0
    return (ridge + shoulder + dip + far).coerceAtLeast(1)
}

private data class Plant(val x: Int, val z: Int, val block: Block)

private val PLANTS = listOf(
    Plant(5, 4, Blocks.SHORT_GRASS),
    Plant(1, 5, Blocks.SHORT_GRASS),
    Plant(4, 6, Blocks.DANDELION),
    Plant(0, 7, Blocks.SHORT_GRASS),
    Plant(5, 1, Blocks.POPPY),
    Plant(-2, 4, Blocks.SHORT_GRASS),
    Plant(7, 3, Blocks.FERN),
    Plant(6, 8, Blocks.SHORT_GRASS),
)

private const val FULL = 15
private const val RIDGE = 4
private const val MIN_X = -5
private const val MAX_X = 10
private const val MIN_Z = -6
private const val MAX_Z = 9
private const val TREE_X = -1
private const val TREE_Z = -1
private const val TRUNK = 4
//?}
