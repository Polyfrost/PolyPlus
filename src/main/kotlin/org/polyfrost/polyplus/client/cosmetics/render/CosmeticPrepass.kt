package org.polyfrost.polyplus.client.cosmetics.render

//? if >= 1.21.4 && < 1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteLayerPose
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityPoseInvoker
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityRendererInvoker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CosmeticPrepass {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")
    private val poseStack = PoseStack()

    @JvmStatic
    fun collect(camera: Camera, deltaTracker: DeltaTracker, entities: List<Entity>) {
        CosmeticView.exitEntity()
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val dispatcher = mc.entityRenderDispatcher
        val position = camera.position
        val ticks = level.tickRateManager()
        for (entity in entities) {
            if (entity !is AbstractClientPlayer || !collectsCosmetics(entity)) continue
            if ((entity as PlayerEmotesAccess).`polyplus$emoteController`().isActive) continue
            val renderer = dispatcher.getRenderer(entity) as? PlayerRenderer ?: continue
            if (!wearsLayer(renderer)) continue
            if (entity.tickCount == 0) {
                entity.xOld = entity.x
                entity.yOld = entity.y
                entity.zOld = entity.z
            }
            val partialTick = deltaTracker.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(entity))
            poseStack.pushPose()
            poseStack.pushPose()
            try {
                collect(entity, renderer, partialTick, position)
            } catch (ex: RuntimeException) {
                LOGGER.debug("Skipped collecting a wearer's cosmetics before the entity pass", ex)
            } finally {
                poseStack.popPose()
                poseStack.popPose()
            }
        }
    }

    private fun collect(player: AbstractClientPlayer, renderer: PlayerRenderer, partialTick: Float, camera: Vec3) {
        val state = renderer.createRenderState(player, partialTick)
        if (state.isSpectator) return
        val offset = renderer.getRenderOffset(state)
        poseStack.translate(
            Mth.lerp(partialTick.toDouble(), player.xOld, player.x) - camera.x + offset.x,
            Mth.lerp(partialTick.toDouble(), player.yOld, player.y) - camera.y + offset.y,
            Mth.lerp(partialTick.toDouble(), player.zOld, player.z) - camera.z + offset.z,
        )
        if (state.hasPose(Pose.SLEEPING)) {
            val direction = state.bedOrientation
            if (direction != null) {
                val height = state.eyeHeight - 0.1f
                poseStack.translate(-direction.stepX * height, 0f, -direction.stepZ * height)
            }
        }
        val scale = state.scale
        poseStack.scale(scale, scale, scale)
        val pose = renderer as LivingEntityPoseInvoker
        pose.`polyplus$setupRotations`(state, poseStack, state.bodyRot, scale)
        poseStack.scale(-1f, -1f, 1f)
        pose.`polyplus$scale`(state, poseStack)
        poseStack.translate(0f, -1.501f, 0f)
        val model = renderer.model
        EmoteLayerPose.hold(poseStack.last().pose())
        try {
            model.setupAnim(state)
        } finally {
            EmoteLayerPose.release()
        }
        collectCosmetics(poseStack, state, model)
    }

    private fun wearsLayer(renderer: PlayerRenderer): Boolean =
        (renderer as LivingEntityRendererInvoker).`polyplus$layers`().any { it is CosmeticRenderLayer }
}
*///?} elif = 1.21.1 {
/*import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteLayerPose
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityPoseInvoker
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityRendererInvoker
import org.polyfrost.polyplus.mixin.client.cosmetics.PlayerRendererPoseInvoker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CosmeticPrepass {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")
    private val poseStack = PoseStack()

    @JvmStatic
    fun collect(camera: Camera, deltaTracker: DeltaTracker, frustum: Frustum) {
        CosmeticView.exitEntity()
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val dispatcher = mc.entityRenderDispatcher
        val position = camera.position
        val ticks = level.tickRateManager()
        val viewer = camera.entity
        for (player in level.players()) {
            if (!collectsCosmetics(player)) continue
            if (!dispatcher.shouldRender(player, frustum, position.x, position.y, position.z) && !player.hasIndirectPassenger(mc.player)) continue
            if (player === viewer && !camera.isDetached && !(viewer is LivingEntity && viewer.isSleeping)) continue
            if (player is LocalPlayer && viewer !== player) continue
            if (player.isSpectator || (player as PlayerEmotesAccess).`polyplus$emoteController`().isActive) continue
            val renderer = dispatcher.getRenderer(player) as? PlayerRenderer ?: continue
            if (!wearsLayer(renderer)) continue
            if (player.tickCount == 0) {
                player.xOld = player.x
                player.yOld = player.y
                player.zOld = player.z
            }
            val partialTick = deltaTracker.getGameTimeDeltaPartialTick(!ticks.isEntityFrozen(player))
            poseStack.pushPose()
            poseStack.pushPose()
            try {
                collect(player, renderer, partialTick, position)
            } catch (ex: RuntimeException) {
                LOGGER.debug("Skipped collecting a wearer's cosmetics before the entity pass", ex)
            } finally {
                poseStack.popPose()
                poseStack.popPose()
            }
        }
    }

    private fun collect(player: AbstractClientPlayer, renderer: PlayerRenderer, partialTick: Float, camera: Vec3) {
        val offset = renderer.getRenderOffset(player, partialTick)
        poseStack.translate(
            Mth.lerp(partialTick.toDouble(), player.xOld, player.x) - camera.x + offset.x,
            Mth.lerp(partialTick.toDouble(), player.yOld, player.y) - camera.y + offset.y,
            Mth.lerp(partialTick.toDouble(), player.zOld, player.z) - camera.z + offset.z,
        )
        (renderer as PlayerRendererPoseInvoker).`polyplus$setModelProperties`(player)
        val pose = renderer as LivingEntityPoseInvoker
        val model = renderer.model
        model.attackTime = pose.`polyplus$getAttackAnim`(player, partialTick)
        model.riding = player.isPassenger
        model.young = player.isBaby
        var bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        val headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
        var yaw = headYaw - bodyYaw
        val vehicle = player.vehicle
        if (player.isPassenger && vehicle is LivingEntity) {
            bodyYaw = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot)
            yaw = headYaw - bodyYaw
            var turn = Mth.wrapDegrees(yaw)
            if (turn < -85f) turn = -85f
            if (turn >= 85f) turn = 85f
            bodyYaw = headYaw - turn
            if (turn * turn > 2500f) bodyYaw += turn * 0.2f
            yaw = headYaw - bodyYaw
        }
        var pitch = Mth.lerp(partialTick, player.xRotO, player.xRot)
        if (LivingEntityRenderer.isEntityUpsideDown(player)) {
            pitch *= -1f
            yaw *= -1f
        }
        yaw = Mth.wrapDegrees(yaw)
        if (player.hasPose(Pose.SLEEPING)) {
            val direction = player.bedOrientation
            if (direction != null) {
                val height = player.getEyeHeight(Pose.STANDING) - 0.1f
                poseStack.translate(-direction.stepX * height, 0f, -direction.stepZ * height)
            }
        }
        val scale = player.scale
        poseStack.scale(scale, scale, scale)
        val bob = pose.`polyplus$getBob`(player, partialTick)
        pose.`polyplus$setupRotations`(player, poseStack, bob, bodyYaw, partialTick, scale)
        poseStack.scale(-1f, -1f, 1f)
        pose.`polyplus$scale`(player, poseStack, partialTick)
        poseStack.translate(0f, -1.501f, 0f)
        var speed = 0f
        var walked = 0f
        if (!player.isPassenger && player.isAlive) {
            speed = PauseAvatar.legacyFade(player, player.walkAnimation.speed(partialTick))
            walked = player.walkAnimation.position(partialTick)
            if (player.isBaby) walked *= 3f
            if (speed > 1f) speed = 1f
        }
        model.prepareMobModel(player, walked, speed, partialTick)
        EmoteLayerPose.hold(poseStack.last().pose())
        try {
            model.setupAnim(player, walked, speed, bob, yaw, pitch)
        } finally {
            EmoteLayerPose.release()
        }
        (model as PlayerModelRootAccess).`polyplus$root`().translateAndRotate(poseStack)
        collectCosmetics(poseStack, player, model, partialTick, speed, bob)
    }

    private fun wearsLayer(renderer: PlayerRenderer): Boolean =
        (renderer as LivingEntityRendererInvoker).`polyplus$layers`().any { it is CosmeticRenderLayer }
}
*///?} elif = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.Culler
import net.minecraft.client.render.entity.PlayerRenderer
import net.minecraft.entity.Entity
import net.minecraft.entity.living.LivingEntity
import net.minecraft.util.math.BlockPos
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityPoseInvoker
import org.polyfrost.polyplus.mixin.client.cosmetics.LivingEntityRendererInvoker
import org.polyfrost.polyplus.mixin.client.cosmetics.PlayerRendererPoseInvoker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CosmeticPrepass {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

    @JvmStatic
    fun collect(camera: Entity, culler: Culler, tickDelta: Float) {
        val mc = Minecraft.getInstance()
        val world = mc.world ?: return
        val dispatcher = mc.entityRenderDispatcher
        val x = camera.lastX + (camera.x - camera.lastX) * tickDelta
        val y = camera.lastY + (camera.y - camera.lastY) * tickDelta
        val z = camera.lastZ + (camera.z - camera.lastZ) * tickDelta
        val viewer = mc.camera
        val sleeping = viewer is LivingEntity && viewer.isSleeping
        for (entity in world.players) {
            val player = entity as? ClientPlayerEntity ?: continue
            if (!collectsCosmetics(player)) continue
            if (!dispatcher.shouldRender(player, culler, x, y, z) && player.rider !== mc.player) continue
            if (player === viewer && mc.options.perspective == 0 && !sleeping) continue
            if (player.y >= 0.0 && player.y < 256.0 && !world.isChunkLoaded(BlockPos(player))) continue
            if (player.isLocal && dispatcher.camera !== player) continue
            if (player.isSpectator || (player as PlayerEmotesAccess).`polyplus$emoteController`().isActive) continue
            val renderer = dispatcher.getRenderer<ClientPlayerEntity>(player) as? PlayerRenderer ?: continue
            if (!wearsLayer(renderer)) continue
            try {
                collect(player, renderer, tickDelta)
            } catch (ex: RuntimeException) {
                LOGGER.debug("Skipped collecting a wearer's cosmetics before the entity pass", ex)
            }
        }
    }

    private fun collect(player: ClientPlayerEntity, renderer: PlayerRenderer, tickDelta: Float) {
        if (player.ticks == 0) {
            player.prevX = player.x
            player.prevY = player.y
            player.prevZ = player.z
        }
        (renderer as PlayerRendererPoseInvoker).`polyplus$setModelStatus`(player)
        val pose = renderer as LivingEntityPoseInvoker
        val model = renderer.getModel()
        model.attackAnimationProgress = pose.`polyplus$getAttackAnimationProgress`(player, tickDelta)
        model.riding = player.isRiding
        model.isBaby = player.isBaby
        val headYaw = pose.`polyplus$getRotatedAngle`(player.lastHeadYaw, player.headYaw, tickDelta)
        val vehicle = player.vehicle
        val body = if (player.isRiding && vehicle is LivingEntity) vehicle else player
        val yaw = headYaw - pose.`polyplus$getRotatedAngle`(body.lastBodyYaw, body.bodyYaw, tickDelta)
        val pitch = player.lastPitch + (player.pitch - player.lastPitch) * tickDelta
        val bob = pose.`polyplus$getBob`(player, tickDelta)
        var speed = player.lastWalkAnimationSpeed + (player.walkAnimationSpeed - player.lastWalkAnimationSpeed) * tickDelta
        var walked = player.walkAnimationProgress - player.walkAnimationSpeed * (1f - tickDelta)
        if (player.isBaby) walked *= 3f
        if (speed > 1f) speed = 1f
        model.prepare(player, walked, speed, tickDelta)
        model.setupAnimation(walked, speed, bob, yaw, pitch, 0.0625f, player)
        collectCosmetics(player, model, speed, tickDelta, bob)
    }

    private fun wearsLayer(renderer: PlayerRenderer): Boolean =
        (renderer as LivingEntityRendererInvoker).`polyplus$layers`().any { it is CosmeticRenderLayer }
}
*///?}
