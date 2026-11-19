package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.entity.animal.equine.Variant
//?} else {
/*import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.RenderType
*///?}
//? if >= 1.21.4 && < 1.21.11 {
/*import net.minecraft.world.entity.animal.horse.Variant
*///?}
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
//? if >= 1.21.4 {
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.HorseRenderState
import net.minecraft.world.entity.HumanoidArm
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
//?}
//? if >= 26.2 {
import net.minecraft.world.entity.EntityTypes
//?} elif >= 1.21.5 {
/*import net.minecraft.world.entity.EntityType as EntityTypes
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.model.Model
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.util.ARGB
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState as AvatarRenderState
*///?}
//? if >= 26.3 {
import net.minecraft.client.renderer.texture.UvMapping
//?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
*///?}
//? if < 1.21.10 {
/*import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.LivingEntity
import org.polyfrost.polyplus.mixin.client.access.CameraAccessor
*///?}
//? if >= 1.21.4 && < 1.21.10 {
/*import net.minecraft.client.renderer.entity.EntityRenderer
*///?}

//? if >= 1.21.10 {
internal typealias LiveDraw = EntityRenderState
//?} elif >= 1.21.4 {
/*internal class LiveDraw(val state: EntityRenderState, val entity: LivingEntity, val partial: Float)
*///?} else {
/*internal class LiveDraw(val entity: LivingEntity, val partial: Float)
*///?}

//? if >= 1.21.4 {
internal typealias LivePlayerModel = PlayerModel
//?} else {
/*internal typealias LivePlayerModel = PlayerModel<*>
*///?}

internal class LivePlayerStage(private val tweak: LiveTweak, private val ours: Boolean) : RenderBlocksContent {
    var horseOpacity = LiveOurs.HORSE_OPACITY
    val actor = LivePlayerActor(tweak, ours)

    private val scene = LiveScene(if (tweak == LiveTweak.SKIN_LAYERS) LiveSpot.STANDING else LiveSpot.RIDING_PATH)
    private val startNanos = System.nanoTime()

    override fun render(renderer: RenderBlocksRenderer, target: RenderTarget, width: Int, height: Int, aspect: Float, nowNanos: Long): Boolean {
        val place = scene.place()
        actor.place = place
        val mc = Minecraft.getInstance()
        val partial = LivePlayerScene.partial()
        val player = actor.player
        val base = LivePlayerSkins.base()
        if (player == null) return scene.render(target, aspect, idleCamera(place)) { }
        player.shownSkin = when (tweak) {
            LiveTweak.CAPES -> LivePlayerSkins.withCape(base)
            LiveTweak.SKIN_LAYERS -> LivePlayerSkins.layered(base)
            else -> base
        }
        val pos = actor.position(player, partial)
        val time = ((nowNanos - startNanos) / NANOS_PER_SECOND).toFloat()
        return when (tweak) {
            LiveTweak.CAPES -> renderCapes(mc, target, aspect, player, pos, partial)
            LiveTweak.SKIN_LAYERS -> renderSkinLayers(mc, target, aspect, place, player, pos, partial, time)
            LiveTweak.SPRINT -> renderSprint(mc, target, aspect, place, player, pos, partial)
            LiveTweak.MOUNT -> renderMount(mc, target, aspect, player, pos, partial)
            else -> false
        }
    }

    override fun release(): List<AutoCloseable> = listOf(scene)

    private fun idleCamera(place: LiveSpotPlace): LiveCamera = LiveCamera.orbit(place, 0f, IDLE_DISTANCE, IDLE_HEIGHT)

    private fun renderCapes(mc: Minecraft, target: RenderTarget, aspect: Float, player: LivePlayerAvatar, pos: Vec3, partial: Float): Boolean {
        val yaw = Mth.rotLerp(partial, player.yRotO, player.yRot)
        val camera = follow(pos, yaw + CAPE_ORBIT, CAPE_DISTANCE, CAPE_HEIGHT, CAPE_TARGET, LiveCamera.DEFAULT_FOV)
        actor.eye = camera.position
        val draw = actor.extract(player, partial) ?: return scene.render(target, aspect, camera) { }
        //? if >= 1.21.4 {
        val state = LivePlayerRenderer.stateOf(draw) as? AvatarRenderState ?: return scene.render(target, aspect, camera) { }
        state.nameTag = null
        state.showCape = true
        //?}
        val model = LivePlayerRenderer.playerModel(mc, player)
        return LivePlayerMods.withSkinLayers(model, null) {
            LivePlayerMods.withWaveyCapes(ours) {
                scene.render(target, aspect, camera) { frame -> submit(mc, frame, draw, pos, MAX_CHANNEL) }
            }
        }
    }

    private fun renderSkinLayers(
        mc: Minecraft,
        target: RenderTarget,
        aspect: Float,
        place: LiveSpotPlace,
        player: LivePlayerAvatar,
        pos: Vec3,
        partial: Float,
        time: Float,
    ): Boolean {
        val angle = SKIN_ORBIT + SKIN_SWAY * sin(time * TAU / SKIN_PERIOD)
        val camera = LiveCamera.orbit(place, angle, SKIN_DISTANCE, SKIN_HEIGHT, SKIN_TARGET, SKIN_FOV)
        actor.eye = camera.position
        val draw = actor.extract(player, partial) ?: return scene.render(target, aspect, camera) { }
        //? if >= 1.21.4 {
        val state = LivePlayerRenderer.stateOf(draw) as? AvatarRenderState ?: return scene.render(target, aspect, camera) { }
        state.nameTag = null
        state.skin = player.skin
        LivePlayerMods.attachEntity(state, null)
        //?}
        val model = LivePlayerRenderer.playerModel(mc, player)
        return LivePlayerMods.withSkinLayers(model, if (ours) player.skin else null) {
            scene.render(target, aspect, camera) { frame -> submit(mc, frame, draw, pos, MAX_CHANNEL) }
        }
    }

    private fun renderSprint(
        mc: Minecraft,
        target: RenderTarget,
        aspect: Float,
        place: LiveSpotPlace,
        player: LivePlayerAvatar,
        pos: Vec3,
        partial: Float,
    ): Boolean {
        val camera = follow(pos, place.yaw + SIDE_ANGLE, SPRINT_DISTANCE, SPRINT_HEIGHT, SPRINT_TARGET, LiveCamera.DEFAULT_FOV * actor.fovScale(partial))
        actor.eye = camera.position
        val draw = actor.extract(player, partial) ?: return scene.render(target, aspect, camera) { }
        //? if >= 1.21.4 {
        val state = LivePlayerRenderer.stateOf(draw) as? AvatarRenderState ?: return scene.render(target, aspect, camera) { }
        state.nameTag = null
        //?}
        val model = LivePlayerRenderer.playerModel(mc, player)
        return LivePlayerMods.withSkinLayers(model, null) {
            scene.render(target, aspect, camera) { frame -> submit(mc, frame, draw, pos, MAX_CHANNEL) }
        }
    }

    private fun renderMount(mc: Minecraft, target: RenderTarget, aspect: Float, player: LivePlayerAvatar, pos: Vec3, partial: Float): Boolean {
        val eye = pos.add(0.0, player.eyeHeight.toDouble(), 0.0)
        val camera = LiveCamera(eye, Mth.rotLerp(partial, player.yRotO, player.yRot), Mth.lerp(partial, player.xRotO, player.xRot))
        val horse = actor.horse ?: return scene.render(target, aspect, camera) { }
        val horsePos = actor.position(horse, partial)
        val opacity = if (ours) horseOpacity.coerceIn(0f, FULL_OPACITY) else FULL_OPACITY
        val alpha = (opacity / FULL_OPACITY * MAX_CHANNEL).toInt().coerceIn(0, MAX_CHANNEL)
        actor.eye = camera.position
        val draw = actor.extract(horse, partial)
        return scene.render(target, aspect, camera) draw@{ frame ->
            if (draw == null || alpha == 0) return@draw
            //? if >= 1.21.4 {
            val state = LivePlayerRenderer.stateOf(draw)
            state.nameTag = null
            LivePlayerMods.mountOpacity(state, opacity)
            if (state is HorseRenderState) {
                state.variant = Variant.CHESTNUT
                state.isInvisible = opacity < FULL_OPACITY
                state.isInvisibleToPlayer = false
            }
            //?} else {
            /*horse.isInvisible = opacity < FULL_OPACITY
            *///?}
            submit(mc, frame, draw, horsePos, alpha)
        }
    }

    private fun follow(pos: Vec3, angle: Float, distance: Double, height: Double, targetHeight: Double, fov: Float): LiveCamera {
        val rad = Math.toRadians(angle.toDouble())
        val eye = pos.add(-sin(rad) * distance, height, cos(rad) * distance)
        return LiveCamera.lookingAt(eye, pos.add(0.0, targetHeight, 0.0), fov)
    }

    private fun submit(mc: Minecraft, frame: LiveSceneFrame, draw: LiveDraw, pos: Vec3, alpha: Int) {
        //? if >= 1.21.10 {
        val scene = if (actor.physicsLit) draw.lightCoords else frame.light(pos.add(0.0, LIGHT_PROBE, 0.0))
        draw.lightCoords = LightCoordsUtil.pack(
            maxOf(LightCoordsUtil.block(scene), MIN_LIGHT),
            maxOf(LightCoordsUtil.sky(scene), MIN_LIGHT),
        )
        val rel = frame.relative(pos)
        val collector = if (alpha < MAX_CHANNEL) LivePlayerRenderer.fade(frame.collector, draw, alpha) else frame.collector
        mc.entityRenderDispatcher.submit(draw, frame.cameraState, rel.x, rel.y, rel.z, frame.pose, collector)
        //?} else {
        /*val scene = if (actor.physicsLit) {
            mc.entityRenderDispatcher.getPackedLightCoords(draw.entity, draw.partial)
        } else {
            frame.light(pos.add(0.0, LIGHT_PROBE, 0.0))
        }
        val light = LightCoordsUtil.pack(
            maxOf(LightCoordsUtil.block(scene), MIN_LIGHT),
            maxOf(LightCoordsUtil.sky(scene), MIN_LIGHT),
        )
        val rel = frame.relative(pos)
        val buffers = if (alpha < MAX_CHANNEL) LivePlayerRenderer.fade(frame.buffers, alpha) else frame.buffers
        runCatching { LivePlayerRenderer.draw(mc, draw, rel, frame.pose, buffers, light, actor.physicsLit) }
            .onFailure { LivePlayerAvatars.warn("draw", it) }
        *///?}
    }

    private companion object {
        const val TAU = (PI * 2.0).toFloat()
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val FULL_OPACITY = 100f
        const val MAX_CHANNEL = 255
        const val LIGHT_PROBE = 0.5
        const val MIN_LIGHT = 14
        const val IDLE_DISTANCE = 3.5
        const val IDLE_HEIGHT = 1.4
        const val CAPE_ORBIT = 150f
        const val CAPE_DISTANCE = 3.4
        const val CAPE_HEIGHT = 1.7
        const val CAPE_TARGET = 1.1
        const val SKIN_ORBIT = -60f
        const val SKIN_SWAY = 18f
        const val SKIN_PERIOD = 12f
        const val SKIN_DISTANCE = 2.0
        const val SKIN_HEIGHT = 1.6
        const val SKIN_TARGET = 1.4
        const val SKIN_FOV = 50f
        const val SIDE_ANGLE = 90f
        const val SPRINT_DISTANCE = 4.2
        const val SPRINT_HEIGHT = 1.3
        const val SPRINT_TARGET = 0.95
    }
}

internal object LivePlayerRenderer {
    fun playerModel(mc: Minecraft, player: LivePlayerAvatar): LivePlayerModel? =
        //? if >= 1.21.4 {
        (mc.entityRenderDispatcher.getRenderer(player) as? LivingEntityRenderer<*, *, *>)?.model as? LivePlayerModel
        //?} else {
        /*(mc.entityRenderDispatcher.getRenderer(player) as? LivingEntityRenderer<*, *>)?.model as? LivePlayerModel
        *///?}

    //? if >= 1.21.10 {
    fun stateOf(draw: LiveDraw): EntityRenderState = draw
    //?} elif >= 1.21.4 {
    /*fun stateOf(draw: LiveDraw): EntityRenderState = draw.state
    *///?}

    //? if >= 1.21.4 {
    fun avatarState(): AvatarRenderState = AvatarRenderState().apply {
        (this as? AvatarEmoteRenderAccess)?.`polyplus$bindEmoteController`(EmoteController())
        LivePlayerMods.mountOpacity(this, FULL_OPACITY)
        //? if >= 1.21.5 {
        entityType = EntityTypes.PLAYER
        //?}
        mainArm = HumanoidArm.RIGHT
        leftArmPose = HumanoidModel.ArmPose.EMPTY
        rightArmPose = HumanoidModel.ArmPose.EMPTY
        id = STATE_ID
        boundingBoxWidth = PLAYER_WIDTH
        boundingBoxHeight = PLAYER_HEIGHT
        eyeHeight = PLAYER_EYES
    }

    fun horseState(): HorseRenderState = HorseRenderState().apply {
        LivePlayerMods.mountOpacity(this, FULL_OPACITY)
        //? if >= 1.21.5 {
        entityType = EntityTypes.HORSE
        //?}
        variant = Variant.CHESTNUT
        isRidden = true
        animateTail = true
        boundingBoxWidth = HORSE_WIDTH
        boundingBoxHeight = HORSE_HEIGHT
    }
    //?}

    //? if >= 1.21.10 {
    fun fade(collector: SubmitNodeCollector, target: Any, alpha: Int): SubmitNodeCollector = FadeCollector(collector, target, alpha)

    private class FadeCollector(
        private val inner: SubmitNodeCollector,
        private val target: Any,
        private val alpha: Int,
    ) : SubmitNodeCollector by inner {
        //? if >= 26.3 {
        override fun <S : Any> submitModel(
            model: Model<in S>,
            state: S,
            poseStack: PoseStack,
            renderType: RenderType,
            lightCoords: Int,
            overlayCoords: Int,
            tintedColor: Int,
            uvMapping: UvMapping?,
            outlineColor: Int,
        ) {
            val color = if (state === target) ARGB.color(alpha, tintedColor) else tintedColor
            inner.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, uvMapping, outlineColor)
        }
        //?} else {
        /*override fun <S : Any> submitModel(
            model: Model<in S>,
            state: S,
            poseStack: PoseStack,
            renderType: RenderType,
            lightCoords: Int,
            overlayCoords: Int,
            tintedColor: Int,
            sprite: TextureAtlasSprite?,
            outlineColor: Int,
            crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?,
        ) {
            val color = if (state === target) ARGB.color(alpha, tintedColor) else tintedColor
            inner.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumblingOverlay)
        }
        *///?}
    }
    //?} else {
    /*private val standIn: Camera by lazy {
        (LivePlayerAvatars.unsafe().allocateInstance(Camera::class.java) as Camera).also {
            (it as CameraAccessor).`polyplus$setPosition`(Vec3(0.0, STAND_IN_HEIGHT, 0.0))
        }
    }

    fun <T> withStandInCamera(block: () -> T): T {
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val saved = dispatcher.camera
        dispatcher.camera = standIn
        try {
            return block()
        } finally {
            dispatcher.camera = saved
        }
    }

    fun fade(buffers: MultiBufferSource, alpha: Int): MultiBufferSource = FadeBuffers(buffers, alpha)

    private class FadeBuffers(private val inner: MultiBufferSource, private val alpha: Int) : MultiBufferSource {
        override fun getBuffer(renderType: RenderType): VertexConsumer = FadeConsumer(inner.getBuffer(renderType), alpha)
    }

    private class FadeConsumer(private val inner: VertexConsumer, private val alpha: Int) : VertexConsumer {
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { inner.addVertex(x, y, z) }

        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply { inner.setColor(red, green, blue, this.alpha) }

        override fun setUv(u: Float, v: Float): VertexConsumer = apply { inner.setUv(u, v) }

        override fun setUv1(u: Int, v: Int): VertexConsumer = apply { inner.setUv1(u, v) }

        override fun setUv2(u: Int, v: Int): VertexConsumer = apply { inner.setUv2(u, v) }

        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply { inner.setNormal(x, y, z) }

        override fun addVertex(
            x: Float,
            y: Float,
            z: Float,
            color: Int,
            u: Float,
            v: Float,
            overlay: Int,
            light: Int,
            normalX: Float,
            normalY: Float,
            normalZ: Float,
        ) {
            val faded = (alpha shl ALPHA_SHIFT) or (color and RGB_MASK)
            inner.addVertex(x, y, z, faded, u, v, overlay, light, normalX, normalY, normalZ)
        }
    }
    *///?}

    //? if >= 1.21.5 && < 1.21.10 {
    /*@Suppress("UNCHECKED_CAST")
    fun rendererOf(entity: LivingEntity): EntityRenderer<LivingEntity, EntityRenderState> =
        Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity) as EntityRenderer<LivingEntity, EntityRenderState>

    fun draw(mc: Minecraft, draw: LiveDraw, rel: Vec3, pose: PoseStack, buffers: MultiBufferSource, light: Int, physics: Boolean) {
        val dispatcher = mc.entityRenderDispatcher
        dispatcher.setRenderShadow(physics && mc.level === draw.entity.level())
        try {
            dispatcher.render(draw.state, rel.x, rel.y, rel.z, pose, buffers, light)
        } finally {
            dispatcher.setRenderShadow(true)
        }
    }
    *///?} elif = 1.21.4 {
    /*@Suppress("UNCHECKED_CAST")
    fun rendererOf(entity: LivingEntity): EntityRenderer<LivingEntity, EntityRenderState> =
        Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity) as EntityRenderer<LivingEntity, EntityRenderState>

    fun draw(mc: Minecraft, draw: LiveDraw, rel: Vec3, pose: PoseStack, buffers: MultiBufferSource, light: Int, physics: Boolean) {
        val renderer = rendererOf(draw.entity)
        val offset = renderer.getRenderOffset(draw.state)
        pose.pushPose()
        try {
            pose.translate(rel.x + offset.x, rel.y + offset.y, rel.z + offset.z)
            renderer.render(draw.state, pose, buffers, light)
        } finally {
            pose.popPose()
        }
    }
    *///?} elif = 1.21.1 {
    /*fun draw(mc: Minecraft, draw: LiveDraw, rel: Vec3, pose: PoseStack, buffers: MultiBufferSource, light: Int, physics: Boolean) {
        val entity = draw.entity
        val yaw = Mth.rotLerp(draw.partial, entity.yRotO, entity.yRot)
        withStandInCamera {
            mc.entityRenderDispatcher.render(entity, rel.x, rel.y, rel.z, yaw, draw.partial, pose, buffers, light)
        }
    }
    *///?}

    //? if >= 1.21.4 {
    private const val STATE_ID = Int.MIN_VALUE + 7
    private const val FULL_OPACITY = 100f
    private const val PLAYER_WIDTH = 0.6f
    private const val PLAYER_HEIGHT = 1.8f
    private const val PLAYER_EYES = 1.62f
    private const val HORSE_WIDTH = 1.3964844f
    private const val HORSE_HEIGHT = 1.6f
    //?}
    //? if < 1.21.10 {
    /*private const val STAND_IN_HEIGHT = 1.0E9
    private const val ALPHA_SHIFT = 24
    private const val RGB_MASK = 0xFFFFFF
    *///?}
}
//?}
