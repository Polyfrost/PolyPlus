package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.WallTorchBlock
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
//? if >= 26.3 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState.HandRenderSelection
import net.minecraft.client.renderer.state.level.PlayerRenderState
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemDisplayContext
//?}

internal class LiveWorldTunnel(label: String, var brightness: Float, var heldLightMode: Int) : RenderBlocksDiorama(label) {
    override val scene = RenderBlocksScene(tunnelBlocks(), tunnelLight(null))

    private val hand = LiveWorldTorchHand()
    private var hudPass: LiveWorldHud? = null
    private val eyePos = Vector3f()
    private val litPos = Vector3f()
    private var walk = 0f
    private var litTick = Long.MIN_VALUE
    private var litKey: List<Int> = emptyList()

    override fun animate(dt: Float, time: Float) {
        walk = time * WALK_RATE
        val swing = walk * PI_F
        eyePos.set(
            EYE.x + sin(swing) * BOB * HALF,
            EYE.y - abs(cos(swing) * BOB),
            EYE.z + sin(time * DRIFT_SPEED) * DRIFT,
        )
    }

    override fun meshKey(time: Float): Any {
        if (heldLightMode == OnboardingFeatures.DYNAMIC_LIGHTS_OFF) {
            litTick = Long.MIN_VALUE
            litKey = emptyList()
            scene.light = tunnelLight(null)
            return litKey
        }
        val interval = UPDATE_SECONDS.getOrElse(heldLightMode) { 0f }
        val tick = if (interval <= 0f) Long.MAX_VALUE else (time / interval).toLong()
        if (tick == litTick && tick != Long.MAX_VALUE) return litKey
        litTick = tick
        litPos.set(eyePos.x, eyePos.y - HELD_DROP, eyePos.z)
        val light = tunnelLight(litPos)
        scene.light = light
        litKey = scene.blocks.keys.flatMap { pos -> Direction.entries.map { light.packed(pos.relative(it)) } }
        return litKey
    }

    override fun view(time: Float, aspect: Float) = RenderBlocksView(
        eyePos,
        0f,
        sin(time * SWAY_SPEED) * SWAY_YAW,
        PITCH + abs(cos(walk * PI_F - BOB_PHASE) * BOB) * BOB_PITCH,
        FOV,
    )

    override fun lighting(time: Float) = RenderBlocksLighting(brightness = brightness)

    override fun overlay(target: RenderTarget, aspect: Float, time: Float) {
        val held = if (heldLightMode == OnboardingFeatures.DYNAMIC_LIGHTS_OFF) {
            scene.light.packed(BlockPos.containing(eyePos.x.toDouble(), eyePos.y.toDouble(), eyePos.z.toDouble()))
        } else {
            LightCoordsUtil.pack(TORCH_LUMINANCE, 0)
        }
        val pass = hudPass ?: LiveWorldHud("polyplus_live_torch_hud").also { hudPass = it }
        pass.render(target, aspect) { hand.submit(it, walk, held) }
    }

    override fun releaseExtra(): List<AutoCloseable> {
        val owned = listOfNotNull(hudPass)
        hudPass = null
        litTick = Long.MIN_VALUE
        litKey = emptyList()
        return owned
    }

    private companion object {
        val EYE = Vector3f(0.5f, 2.62f, 1.4f)
        val UPDATE_SECONDS = listOf(0f, 0.5f, 0.25f, 0f)
        const val PI_F = Math.PI.toFloat()
        const val WALK_RATE = 0.9f
        const val BOB = 0.06f
        const val BOB_PHASE = 0.2f
        const val BOB_PITCH = 5f
        const val HALF = 0.5f
        const val DRIFT = 0.35f
        const val DRIFT_SPEED = 0.4f
        const val SWAY_SPEED = 0.3f
        const val SWAY_YAW = 4f
        const val PITCH = 8f
        const val FOV = 70f
        const val HELD_DROP = 0.4f
    }
}

private class LiveWorldTorchHand {
    //? if >= 26.3 {
    private val player = PlayerRenderState()
    private val avatar = AvatarRenderState()
    //?}
    private val torch = ItemStack(Items.TORCH)

    fun submit(collector: LiveHudCollector, walk: Float, light: Int) {
        val pose = PoseStack()
        val swing = walk * Math.PI.toFloat()
        pose.translate(sin(swing) * HAND_BOB * HALF, -abs(cos(swing) * HAND_BOB), 0f)
        pose.turn(Axis.ZP, sin(swing) * HAND_BOB * ROLL)
        pose.turn(Axis.XP, abs(cos(swing - BOB_PHASE) * HAND_BOB) * TILT)
        //? if < 26.3 {
        /*val holder = ShieldBearer.holding(ItemStack.EMPTY) ?: return
        LiveWorldHands.submit(collector, pose, HumanoidArm.RIGHT, torch, holder, light)
        *///?} else {
        val mc = Minecraft.getInstance()
        avatar.mainArm = HumanoidArm.RIGHT
        avatar.isUsingItem = false
        avatar.isInvisible = false
        avatar.isAutoSpinAttack = false
        avatar.currentSwing = null
        avatar.swingAnimation = 0f
        avatar.xRot = 0f
        avatar.lightCoords = light
        player.hasPlayer = true
        player.avatarRenderState = avatar
        val hands = player.firstPersonHandsAndItems
        hands.attackHand = InteractionHand.MAIN_HAND
        hands.isScoping = false
        hands.useItemRemainingTicks = 0
        hands.mainHandSwapScale = 1f
        hands.offHandSwapScale = 1f
        hands.mainHandHeight = 1f
        hands.oldMainHandHeight = 1f
        hands.offHandHeight = 1f
        hands.oldOffHandHeight = 1f
        hands.mainHandItem = torch
        hands.offHandItem = ItemStack.EMPTY
        hands.hasMainHandMapData = false
        hands.hasOffHandMapData = false
        hands.handRenderSelection = HandRenderSelection.RENDER_MAIN_HAND_ONLY
        mc.itemModelResolver.updateForTopItem(hands.mainHandRenderState, torch, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 0)
        mc.gameRenderer.firstPersonHandsAndItemsRenderer.submitHandsWithItems(0f, pose, collector, player, hands)
        //?}
    }

    private companion object {
        const val HAND_BOB = 0.06f
        const val HALF = 0.5f
        const val ROLL = 3f
        const val TILT = 5f
        const val BOB_PHASE = 0.2f
    }
}

private fun tunnelLight(source: Vector3f?) = RenderBlocksLight { pos ->
    val fixed = (WALL_TORCH_LIGHT - pos.distManhattan(WALL_TORCH)).coerceAtLeast(0)
    val held = if (source == null) {
        0
    } else {
        val dx = pos.x + CENTER - source.x
        val dy = pos.y + CENTER - source.y
        val dz = pos.z + CENTER - source.z
        ceil((1f - sqrt(dx * dx + dy * dy + dz * dz) / DYNAMIC_LIGHT_RADIUS) * TORCH_LUMINANCE).toInt()
    }
    LightCoordsUtil.pack(maxOf(fixed, held, FLOOR_LIGHT).coerceAtMost(FULL), 0)
}

private fun tunnelBlocks(): Map<BlockPos, BlockState> {
    val blocks = HashMap<BlockPos, BlockState>()
    for (z in END_Z..START_Z) {
        for (x in -WALL_X..WALL_X) {
            blocks[BlockPos(x, 0, z)] = floor(x, z)
            blocks[BlockPos(x, CEILING_Y, z)] = rock(x, CEILING_Y, z)
        }
        for (y in 1 until CEILING_Y) {
            blocks[BlockPos(-WALL_X, y, z)] = rock(-WALL_X, y, z)
            blocks[BlockPos(WALL_X, y, z)] = rock(WALL_X, y, z)
        }
    }
    for (x in -WALL_X..WALL_X) for (y in 1 until CEILING_Y) blocks[BlockPos(x, y, END_Z)] = rock(x, y, END_Z)
    blocks[BlockPos(-2, 1, -4)] = Blocks.COBBLESTONE.defaultBlockState()
    blocks[BlockPos(-2, 2, -4)] = Blocks.MOSSY_COBBLESTONE.defaultBlockState()
    blocks[BlockPos(2, 1, -7)] = Blocks.GRAVEL.defaultBlockState()
    blocks[BlockPos(3, 1, -2)] = Blocks.ANDESITE.defaultBlockState()
    blocks[WALL_TORCH] = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)
    return blocks
}

private fun floor(x: Int, z: Int): BlockState = when {
    (x + z * 3) % 7 == 0 -> Blocks.GRAVEL
    (x * 5 + z) % 6 == 0 -> Blocks.ANDESITE
    else -> Blocks.STONE
}.defaultBlockState()

private fun rock(x: Int, y: Int, z: Int): BlockState = when {
    (x * 7 + y * 13 + z * 5) % 23 == 0 -> Blocks.COAL_ORE
    (x * 3 + y * 11 + z * 7) % 41 == 0 -> Blocks.IRON_ORE
    (x + y * 2 + z) % 5 == 0 -> Blocks.TUFF
    (x * 2 + y + z * 3) % 4 == 0 -> Blocks.ANDESITE
    else -> Blocks.STONE
}.defaultBlockState()

private val WALL_TORCH = BlockPos(0, 2, END_Z + 1)
private const val WALL_X = 3
private const val CEILING_Y = 5
private const val START_Z = 3
private const val END_Z = -13
private const val WALL_TORCH_LIGHT = 14
private const val TORCH_LUMINANCE = 14
private const val DYNAMIC_LIGHT_RADIUS = 7.75f
private const val FLOOR_LIGHT = 0
private const val FULL = 15
private const val CENTER = 0.5f
//?}
