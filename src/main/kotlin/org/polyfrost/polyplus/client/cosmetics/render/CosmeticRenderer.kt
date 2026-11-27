package org.polyfrost.polyplus.client.cosmetics.render

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.LivingEntityRenderer
//?}
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack
import java.util.UUID

//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack as VanillaPoseStack
import com.mojang.blaze3d.vertex.VertexConsumer as VanillaVertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?}

//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState
*///?}

//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.entity.living.player.PlayerEntity
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
import org.polyfrost.polyplus.client.render.VertexConsumer
import org.polyfrost.polyplus.mixin.client.signals.SignalsTimerAccessor
*///?}

object CosmeticRenderer {
    private const val CHESTPLATE_BACK_OFFSET = 1.5f
    private val root = FloatArray(12)

    private class Draw
    //? if >= 1.21.10
    : PooledGeometry
    {
        lateinit var texture: Identifier
        lateinit var scene: CosmeticScene
        var frame = 0
        var row = 0
        var color = 0
        var translucent = false
        var model = false
        //? if >= 1.21.10 {
        var lightCoords = 0
        var overlay = 0

        override fun render(pose: VanillaPoseStack.Pose, buffer: VanillaVertexConsumer) {
            scene.emit(frame, row, pose, buffer, lightCoords, overlay, color)
        }
        //?}
    }

    private val BODY_SLOTS = BodySlot.values()
    private val drawList = ArrayList<Draw>(8)
    private val drawPool = ArrayList<Draw>(8)
    private var drawsUsed = 0

    private fun obtainDraw(): Draw {
        if (drawsUsed == drawPool.size) drawPool += Draw()
        return drawPool[drawsUsed++]
    }

    //? if >= 1.21.10 {
    @JvmStatic
    fun endFrame() {
        drawsUsed = 0
        DeferredPoses.reset()
    }
    //?}

    //? if >= 1.21.10 {
    fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
        preview: Boolean,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        val tracked = track(wearer, state.id, if (preview) 0.0 else state.distanceToCameraSq, preview)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        layerRoot(poseStack, preview)
        if (preview) PreviewEffects.place(tracked, poseStack.last().pose(), root)
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        val draws = draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)
        for (at in 0 until draws.size) {
            val draw = draws[at]
            if (!draw.model) continue
            val renderType = BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent)
            draw.lightCoords = lightCoords
            draw.overlay = overlay
            submitNodeCollector.submitCustomGeometry(poseStack, renderType, draw)
        }
        if (preview) {
            for (source in previewSources(tracked, draws)) {
                CosmeticEffects.submitPreview(source, tracked.previewModel, poseStack, submitNodeCollector)
            }
        }
    }
    //?} elif >= 1.21.4 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        state: PlayerRenderState,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
        preview: Boolean,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        val tracked = track(wearer, state.id, if (preview) 0.0 else state.distanceToCameraSq, preview)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        layerRoot(poseStack, preview)
        if (preview) PreviewEffects.place(tracked, poseStack.last().pose(), root)
        val overlay = LivingEntityRenderer.getOverlayCoords(state, 0f)
        val draws = draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)
        for (at in 0 until draws.size) {
            val draw = draws[at]
            if (!draw.model) continue
            val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent))
            draw.scene.emit(draw.frame, draw.row, poseStack.last(), buffer, lightCoords, overlay, draw.color)
        }
        if (preview) {
            for (source in previewSources(tracked, draws)) {
                CosmeticEffects.drawPreview(source, tracked.previewModel, poseStack, bufferSource)
            }
        }
    }

    fun collect(
        poseStack: PoseStack,
        state: PlayerRenderState,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
    ) {
        val renderContext = PlayerRenderContext.from(state)
        val tracked = track(wearer, state.id, state.distanceToCameraSq, false)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        layerRoot(poseStack, false)
        draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots, collect = true)
    }
    *///?} elif > 1.8.9 {
    /*fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        lightCoords: Int,
        player: AbstractClientPlayer,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
        preview: Boolean,
    ) {
        val distanceSq = if (preview) 0.0 else Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(player)
        val tracked = track(wearer, player.id, distanceSq, preview)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        layerRoot(poseStack, preview)
        if (preview) PreviewEffects.place(tracked, poseStack.last().pose(), root)
        val overlay = LivingEntityRenderer.getOverlayCoords(player, 0f)
        val draws = draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)
        for (at in 0 until draws.size) {
            val draw = draws[at]
            if (!draw.model) continue
            val buffer = bufferSource.getBuffer(BedrockAttachedModelRenderer.renderType(draw.texture, draw.translucent))
            draw.scene.emit(draw.frame, draw.row, poseStack.last(), buffer, lightCoords, overlay, draw.color)
        }
        if (preview) {
            for (source in previewSources(tracked, draws)) {
                CosmeticEffects.drawPreview(source, tracked.previewModel, poseStack, bufferSource)
            }
        }
    }

    fun collect(
        poseStack: PoseStack,
        player: AbstractClientPlayer,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
    ) {
        val tracked = track(wearer, player.id, Minecraft.getInstance().entityRenderDispatcher.distanceToSqr(player), false)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        layerRoot(poseStack, false)
        draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots, collect = true)
    }
    *///?} else {
    /*fun render(
        poseStack: PoseStack,
        lightCoords: Int,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long = Renderer.PREVIEW_WEARER,
        entityId: Int = 0,
        distanceSq: Double = 0.0,
        preview: Boolean = true,
    ) {
        val tracked = track(wearer, entityId, distanceSq, preview)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        entityRoot(entityId, preview, playerModel)
        if (preview) {
            modelView.clear()
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
            PreviewEffects.place(tracked, Matrix4f(modelView).mul(poseStack.last().pose()), root)
        }
        val draws = draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots)
        for (at in 0 until draws.size) {
            val draw = draws[at]
            if (!draw.model) continue
            legacyEmit.draw = draw
            legacyEmit.pose = poseStack
            legacyEmit.light = lightCoords
            VertexConsumer.draw(draw.texture, draw.translucent, emit = legacyEmit)
        }
        if (preview) {
            val model = Matrix4f(poseStack.last().pose()).mul(tracked.previewModel)
            for (source in previewSources(tracked, draws)) CosmeticEffects.drawPreview(source, model)
        }
    }

    fun collect(
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        equipment: CosmeticEquipment,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        wearer: Long,
        entityId: Int,
        distanceSq: Double,
    ) {
        val tracked = track(wearer, entityId, distanceSq, false)
        val events = renderContext.signals.events(PlayerSignals.READER_COSMETICS)
        if (renderContext.isInvisible) return
        entityRoot(entityId, false, playerModel)
        draws(tracked, events, equipment, renderContext, playerModel, particleColor, chestplateEquipped, hiddenSlots, collect = true)
    }

    private val modelView = BufferUtils.createFloatBuffer(16)

    private class LegacyEmit : (VertexConsumer) -> Unit {
        lateinit var draw: Draw
        lateinit var pose: PoseStack
        var light = 0

        override fun invoke(buffer: VertexConsumer) {
            draw.scene.emit(draw.frame, draw.row, pose.last(), buffer, light, 0, draw.color)
        }
    }

    private val legacyEmit = LegacyEmit()
    *///?}

    //? if > 1.8.9 {
    private fun layerRoot(poseStack: PoseStack, preview: Boolean) {
        if (preview) PreviewTryOn.previewRoot(root) else CosmeticOrigin.fromLayer(root, CosmeticView.layerPose(poseStack.last().pose()))
    }
    //?} else {
    /*private fun entityRoot(entityId: Int, preview: Boolean, model: PlayerModel) {
        val player = if (preview) null else Minecraft.getInstance().world?.getEntity(entityId) as? PlayerEntity
        if (player == null) {
            if (preview) PreviewTryOn.previewRoot(root) else CosmeticOrigin.still(root)
            return
        }
        val partialTick = (Minecraft.getInstance() as SignalsTimerAccessor).`polyplus$signalTimer`().partialTick
        val x = player.prevX + (player.x - player.prevX) * partialTick
        val y = player.prevY + (player.y - player.prevY) * partialTick
        val z = player.prevZ + (player.z - player.prevZ) * partialTick
        var turn = (player.bodyYaw - player.lastBodyYaw) % 360f
        if (turn >= 180f) turn -= 360f
        if (turn < -180f) turn += 360f
        CosmeticOrigin.fromWorld(root, x, y, z, player.lastBodyYaw + turn * partialTick, PLAYER_SCALE)
        val part = (model as PlayerModelRootAccess).`polyplus$root`()
        val scale = part as ModelPartScaleAccess
        CosmeticOrigin.applyLayerPart(
            root, part.x, part.y, part.z, part.rotationX, part.rotationY, part.rotationZ,
            scale.`polyplus$scaleX`(), scale.`polyplus$scaleY`(), scale.`polyplus$scaleZ`(),
        )
    }

    private const val PLAYER_SCALE = 0.9375f
    *///?}

    private fun previewSources(wearer: CosmeticWearers.Wearer, draws: List<Draw>): List<EffectSource> {
        val sources = ArrayList<EffectSource>(2)
        for (at in 0 until draws.size) {
            val source = draws[at].scene.previewSource(wearer.id) ?: continue
            if (sources.none { it === source }) sources += source
        }
        return sources
    }

    private fun track(wearer: Long, entityId: Int, distanceSq: Double, preview: Boolean): CosmeticWearers.Wearer {
        CosmeticWearers.begin()
        return CosmeticWearers.track(wearer, entityId, distanceSq, preview)
    }

    private fun draws(
        wearer: CosmeticWearers.Wearer,
        events: Int,
        equipment: CosmeticEquipment,
        renderContext: PlayerRenderContext,
        playerModel: PlayerModel,
        particleColor: Int?,
        chestplateEquipped: Boolean,
        hiddenSlots: Set<BodySlot>,
        collect: Boolean = false,
    ): List<Draw> {
        val owner = CosmeticWearers.colorOwner(wearer)
        val draws = drawList
        draws.clear()
        //? if < 1.21.10
        //drawsUsed = 0
        for (slot in BODY_SLOTS) {
            val cosmetic = equipment.get(slot)?.cosmetic ?: continue
            val bits = if (wearer.preview) events or PreviewTryOn.equipEvents(cosmetic.cosmeticId) else events
            if (cosmetic.slot in hiddenSlots) continue
            val tint = particleColor?.takeIf { cosmetic.slot == BodySlot.Aura && cosmetic.scene.asset == null }
            val backOffset = if (chestplateEquipped && (cosmetic.slot == BodySlot.Backpack || cosmetic.slot == BodySlot.Tail)) CHESTPLATE_BACK_OFFSET else 0f
            val drawn = draw(wearer, cosmetic, owner, playerModel, renderContext, backOffset, bits, tint, collect, equipped = true)
            if (drawn != null) draws.add(drawn)
        }
        if (BonusEffects.SLOT !in hiddenSlots) {
            val bonuses = if (wearer.preview) BonusEffects.loaded(PreviewTryOn.previewBonuses()) else if (owner != null) BonusEffects.forPlayer(owner) else emptyList()
            for (at in 0 until bonuses.size) {
                val bonus = bonuses[at]
                val bits = events or CosmeticEquips.bonusEvents(wearer, owner, bonus)
                val drawn = draw(wearer, bonus.cosmetic, owner, playerModel, renderContext, 0f, bits, null, collect)
                if (drawn != null) draws.add(drawn)
            }
        }
        return draws
    }

    private fun draw(
        wearer: CosmeticWearers.Wearer,
        cosmetic: AttachedCosmetic,
        owner: UUID?,
        playerModel: PlayerModel,
        renderContext: PlayerRenderContext,
        backOffset: Float,
        events: Int,
        tint: Int?,
        collect: Boolean,
        equipped: Boolean = false,
    ): Draw? {
        if (!RemoteTextures.isRegistered(cosmetic.texture)) return null
        val colors = if (owner != null) CosmeticColors.get(owner, cosmetic.cosmeticId, wearer.preview) else null
        if (owner != null && cosmetic.scene.hasEffects) cosmetic.scene.paint(wearer.id, cosmetic.colorway, colors)
        val bits = if (equipped) events or CosmeticEquips.events(wearer, cosmetic) else events
        val row = cosmetic.scene.add(wearer, playerModel, renderContext, backOffset, bits, root, collect)
        if (row < 0 || collect) return null
        val texture = texture(wearer, cosmetic, colors)
        val draw = obtainDraw()
        draw.texture = texture
        draw.scene = cosmetic.scene
        draw.frame = CosmeticWearers.frame
        draw.row = row
        draw.color = tint ?: -1
        draw.translucent = (tint != null && (tint ushr 24) != 0xFF) || RemoteTextures.isTranslucent(texture)
        draw.model = cosmetic.scene.showsModel(wearer, cosmetic.slot)
        return draw
    }

    internal fun texture(wearer: CosmeticWearers.Wearer, cosmetic: AttachedCosmetic, owner: UUID?): Identifier =
        texture(wearer, cosmetic, owner?.let { CosmeticColors.get(it, cosmetic.cosmeticId, wearer.preview) })

    private fun texture(wearer: CosmeticWearers.Wearer, cosmetic: AttachedCosmetic, colors: Map<String, Int>?): Identifier {
        val reaction = cosmetic.scene.reactionColorway(wearer.id)
        val colorway = if (reaction == PaletteTextures.NO_COLORWAY) cosmetic.colorway else reaction
        return PaletteTextures.texture(CosmeticWearers.look(wearer, cosmetic.scene), wearer.id, cosmetic, colorway, colors)
    }
}
