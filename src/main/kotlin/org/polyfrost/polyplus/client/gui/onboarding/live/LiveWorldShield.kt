package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
//? if >= 26.2 {
import net.minecraft.world.entity.EntityTypes
//?} else {
/*import net.minecraft.world.entity.EntityType as EntityTypes
*///?}
import net.minecraft.world.entity.HumanoidArm
//? if < 1.21.5
/*import net.minecraft.world.entity.EquipmentSlot*/
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.slf4j.LoggerFactory
import sun.misc.Unsafe
//? if < 26.3 {
/*import net.minecraft.client.renderer.texture.OverlayTexture
*///?}
//? if < 1.21.4 {
/*import net.minecraft.client.model.ShieldModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.resources.model.ModelBakery
*///?}
//? if < 26.3 && >= 1.21.5 {
/*import net.minecraft.client.renderer.item.ItemStackRenderState
*///?}
//? if >= 26.3 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState.HandRenderSelection
import net.minecraft.client.renderer.state.level.PlayerRenderState
import net.minecraft.world.InteractionHand
//?}

@Composable
internal fun LiveWorldShieldPreview(shieldHeight: Float, modifier: Modifier) {
    val shield = remember { LiveWorldShield() }
    val height = shieldHeight.coerceIn(OnboardingFeatures.SHIELD_HEIGHT_MIN, OnboardingFeatures.SHIELD_HEIGHT_MAX)
    LiveWorldFirstPerson("polyplus_shield_preview", modifier) { collector, _ -> shield.submit(collector, height) }
}

internal class LiveWorldShield {
    //? if >= 26.3 {
    private val player = PlayerRenderState()
    private val avatar = AvatarRenderState()
    //?}
    private val shield = ItemStack(Items.SHIELD)
    private val sword = ItemStack(Items.DIAMOND_SWORD)

    fun submit(collector: LiveHudCollector, shieldHeight: Float) {
        //? if < 26.3 {
        /*val holder = ShieldBearer.holding(shield) ?: return
        OverlayTweaksShield.neutral {
            LiveWorldHands.submit(collector, PoseStack(), HumanoidArm.RIGHT, sword, holder, FULL_BRIGHT)
            val lowered = PoseStack()
            lowered.translate(0f, shieldHeight, 0f)
            LiveWorldHands.submit(collector, lowered, HumanoidArm.LEFT, shield, holder, FULL_BRIGHT)
        }
        *///?} else {
        val mc = Minecraft.getInstance()
        prepare()
        val hands = player.firstPersonHandsAndItems
        val resolver = mc.itemModelResolver
        resolver.updateForTopItem(hands.mainHandRenderState, sword, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 0)
        resolver.updateForTopItem(hands.offHandRenderState, shield, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, null, ShieldBearer.holding(shield), 0)
        val renderer = mc.gameRenderer.firstPersonHandsAndItemsRenderer
        OverlayTweaksShield.neutral {
            hands.handRenderSelection = HandRenderSelection.RENDER_MAIN_HAND_ONLY
            renderer.submitHandsWithItems(0f, PoseStack(), collector, player, hands)
            hands.handRenderSelection = HandRenderSelection.RENDER_OFF_HAND_ONLY
            val lowered = PoseStack()
            lowered.translate(0f, shieldHeight, 0f)
            renderer.submitHandsWithItems(0f, lowered, collector, player, hands)
        }
        //?}
    }

    //? if >= 26.3 {
    private fun prepare() {
        avatar.mainArm = HumanoidArm.RIGHT
        avatar.isUsingItem = true
        avatar.useItemHand = InteractionHand.OFF_HAND
        avatar.isInvisible = false
        avatar.isAutoSpinAttack = false
        avatar.currentSwing = null
        avatar.swingAnimation = 0f
        avatar.xRot = 0f
        avatar.lightCoords = FULL_BRIGHT
        player.hasPlayer = true
        player.avatarRenderState = avatar
        val hands = player.firstPersonHandsAndItems
        hands.attackHand = InteractionHand.MAIN_HAND
        hands.isScoping = false
        hands.useItemRemainingTicks = USE_TICKS
        hands.mainHandUseDuration = USE_TICKS
        hands.offHandUseDuration = USE_TICKS
        hands.mainHandSwapScale = 1f
        hands.offHandSwapScale = 1f
        hands.mainHandHeight = 1f
        hands.oldMainHandHeight = 1f
        hands.offHandHeight = 1f
        hands.oldOffHandHeight = 1f
        hands.mainHandItem = sword
        hands.offHandItem = shield
        hands.hasMainHandMapData = false
        hands.hasOffHandMapData = false
    }
    //?}

    private companion object {
        const val FULL_BRIGHT = 0xF000F0
        const val USE_TICKS = 72000
    }
}

//? if < 26.3 {
/*internal object LiveWorldHands {
    fun submit(collector: LiveHudCollector, pose: PoseStack, arm: HumanoidArm, stack: ItemStack, holder: LivingEntity, light: Int) {
        val right = arm == HumanoidArm.RIGHT
        pose.pushPose()
        pose.translate(if (right) ARM_X else -ARM_X, ARM_Y, ARM_Z)
        val context = if (right) ItemDisplayContext.FIRST_PERSON_RIGHT_HAND else ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        val mc = Minecraft.getInstance()
        //? if >= 1.21.5 {
        val state = ItemStackRenderState()
        mc.itemModelResolver.updateForTopItem(state, stack, context, null, holder, 0)
        //? if >= 1.21.10 {
        state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0)
        //?} else {
        /^state.render(pose, collector, light, OverlayTexture.NO_OVERLAY)
        ^///?}
        //?} else {
        /^if (stack.`is`(Items.SHIELD) && mc.player == null) {
            shield(mc, pose, collector, stack, context, !right, holder, light)
        } else {
            mc.itemRenderer.renderStatic(holder, stack, context, !right, pose, collector, null, light, OverlayTexture.NO_OVERLAY, 0)
        }
        ^///?}
        pose.popPose()
    }

    //? if < 1.21.4 {
    /^private val shieldModel: ShieldModel by lazy { ShieldModel(Minecraft.getInstance().entityModels.bakeLayer(ModelLayers.SHIELD)) }

    private fun shield(mc: Minecraft, pose: PoseStack, buffers: LiveHudCollector, stack: ItemStack, context: ItemDisplayContext, leftHand: Boolean, holder: LivingEntity, light: Int) {
        val model = mc.itemRenderer.getModel(stack, null, holder, 0)
        pose.pushPose()
        model.transforms.getTransform(context).apply(leftHand, pose)
        pose.translate(-HALF, -HALF, -HALF)
        pose.scale(1f, -1f, -1f)
        val material = ModelBakery.NO_PATTERN_SHIELD
        val buffer = material.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffers, shieldModel.renderType(material.atlasLocation()), true, stack.hasFoil()))
        shieldModel.handle().render(pose, buffer, light, OverlayTexture.NO_OVERLAY)
        shieldModel.plate().render(pose, buffer, light, OverlayTexture.NO_OVERLAY)
        pose.popPose()
    }

    private const val HALF = 0.5f
    ^///?} elif < 1.21.5 {
    /^private fun shield(mc: Minecraft, pose: PoseStack, buffers: LiveHudCollector, stack: ItemStack, context: ItemDisplayContext, leftHand: Boolean, holder: LivingEntity, light: Int) =
        mc.itemRenderer.renderStatic(holder, stack, context, leftHand, pose, buffers, null, light, OverlayTexture.NO_OVERLAY, 0)
    ^///?}

    private const val ARM_X = 0.56f
    private const val ARM_Y = -0.52f
    private const val ARM_Z = -0.72f
}
*///?}

private object OverlayTweaksShield {
    private val LOG = LoggerFactory.getLogger("polyplus/live-shield")
    private var broken = false

    fun neutral(block: () -> Unit) {
        val config = target()
        val saved = config?.let { runCatching { OnboardingFeatures.readFloatingField(it, FIELD) }.getOrNull() }
        if (config == null || saved == null || saved == 0.0 || !write(config, 0.0)) {
            block()
            return
        }
        try {
            block()
        } finally {
            write(config, saved)
        }
    }

    private fun target(): Any? {
        if (broken || !OnboardingFeatures.shieldHeightAvailable) return null
        return runCatching { OnboardingFeatures.overlayTweaksAccess().first }
            .onFailure { broken = true }
            .getOrNull()
    }

    private fun write(config: Any, value: Double): Boolean = runCatching { OnboardingFeatures.writeFloatingField(config, FIELD, value) }
        .onFailure {
            broken = true
            LOG.error("[live-shield] could not override the Overlay Tweaks shield height", it)
        }
        .isSuccess

    private const val FIELD = "customShieldHeight"
}

internal class ShieldBearer : LivingEntity(EntityTypes.ARMOR_STAND, absent<Level>()) {
    override fun isUsingItem(): Boolean = true

    override fun getUseItem(): ItemStack = held

    override fun getMainArm(): HumanoidArm = HumanoidArm.RIGHT

    //? if < 1.21.5 {
    /*override fun getArmorSlots(): Iterable<ItemStack> = emptyList()

    override fun getItemBySlot(slot: EquipmentSlot): ItemStack = ItemStack.EMPTY

    override fun setItemSlot(slot: EquipmentSlot, stack: ItemStack) = Unit
    *///?}

    companion object {
        private var held: ItemStack = ItemStack.EMPTY
        private val instance: LivingEntity? by lazy {
            runCatching {
                val field = Unsafe::class.java.getDeclaredField("theUnsafe")
                field.isAccessible = true
                (field.get(null) as Unsafe).allocateInstance(ShieldBearer::class.java) as LivingEntity
            }.onFailure { LoggerFactory.getLogger("polyplus/live-shield").error("[live-shield] could not create the item holder", it) }.getOrNull()
        }

        fun holding(stack: ItemStack): LivingEntity? {
            held = stack
            return instance
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> absent(): T = null as T
//?}
