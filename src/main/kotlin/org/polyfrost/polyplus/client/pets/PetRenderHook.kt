package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
//? if > 1.8.9
import org.polyfrost.polyplus.client.cosmetics.render.AdditiveEffectTypes

import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEffects
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticOrigin
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonView
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumpRules
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumps
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.render.PoseStack
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.CosmeticClock
import java.util.UUID
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

//? if > 1.8.9 {
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.Pose as EntityPose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.HitResult
//?} else {
/*import net.minecraft.client.render.Culler
import net.minecraft.client.render.model.ModelPart
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.world.HitResult
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonEffects
import org.polyfrost.polyplus.client.render.VertexConsumer
*///?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import org.polyfrost.polyplus.client.cosmetics.render.PooledGeometry
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.VertexConsumer
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if >= 26.2 {
import net.minecraft.util.LightCoordsUtil
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.LevelRenderer
*///?}

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.RenderType
*///?}

object PetRenderHook {
    private const val CULL_REACH = 1.0
    private const val CULL_HEAD_ROOM = 0.5
    private const val PIXELS_PER_BLOCK = 16f
    private const val PET_REACH = 3.0
    private const val PETTED = 0.3f
    private const val BRAIN_BLOCKS = 32.0

    private val root = FloatArray(PetScene.ROOT_FLOATS)
    private val anchor = FloatArray(PetScene.ANCHOR_FLOATS)
    private val variables = FloatArray(PetVariables.NAMES.size)
    private val present = ArrayList<PetManager.Pet>()
    private val drawn = ArrayList<PetManager.Pet>()
    private var useWasDown = false
    private val textures = ArrayList<Identifier>()
    private val glows = ArrayList<Identifier?>()
    private val scenes = ArrayList<PetScene>()
    private var frame = 0

    private class ArmPet {
        var pet: PetManager.Pet? = null
        var scene: PetScene? = null
        var row = -1
        var right = true
        var scale = 1f
        var texture: Identifier? = null
        var glow: Identifier? = null
        val root = FloatArray(PetScene.ROOT_FLOATS)
        val placement = Matrix4f()
    }

    private val armPet = ArmPet()
    private val unrooted = Matrix4f()

    fun reset() {
        for (i in present.indices) present[i].scene = null
        for (i in drawn.indices) drawn[i].scene = null
        present.clear()
        drawn.clear()
        textures.clear()
        glows.clear()
        scenes.clear()
        armPet.pet = null
        armPet.scene = null
        armPet.texture = null
        armPet.glow = null
        //? if >= 1.21.10 {
        releaseDraws()
        //?}
    }

    private fun prepare(level: ClientLevel, cameraX: Double, cameraY: Double, cameraZ: Double, partialTick: Float) {
        reset()
        frame++
        PetOwnerParts.nextFrame()
        val pets = PetManager.pets()
        val now = PetClock.seconds(partialTick)
        val local = Minecraft.getInstance().player?.uuid ?: ClientPlatform.localPlayerUuid()
        val clock = CosmeticClock.seconds()
        markOwnersSeen(level, local, clock)
        if (pets.isEmpty()) {
            PetInteractions.update(present, local, level, now, clock)
            return
        }
        CosmeticWearers.begin()
        val originX = CosmeticOrigin.x
        val originY = CosmeticOrigin.y
        val originZ = CosmeticOrigin.z
        val shiftX = (originX - cameraX).toFloat()
        val shiftY = (originY - cameraY).toFloat()
        val shiftZ = (originZ - cameraZ).toFloat()
        present.clear()
        val assetVersion = PetAssets.version
        for (index in pets.indices) {
            val pet = pets[index]
            pet.row = -1
            pet.scene = null
            pet.present = false
            if (pet.asset == null || pet.assetVersion != assetVersion) {
                pet.asset = PetAssets.get(pet.cosmeticId)
                pet.assetVersion = assetVersion
            }
            if (pet.asset == null) continue
            val player = findOwner(level, pet.owner) ?: continue
            readOwner(pet.ownerState, player, partialTick, now)
            pet.present = true
            present += pet
        }
        PetInteractions.update(present, local, level, now, clock)
        PetSimulation.gather(present)
        val useDown = useKeyDown()
        val used = useDown && !useWasDown
        useWasDown = useDown

        for (index in present.indices) {
            val pet = present[index]
            val asset = pet.asset ?: continue
            val player = findOwner(level, pet.owner) ?: continue
            val owner = pet.ownerState
            val full = pet.owner == local || near(pet.body, cameraX, cameraY, cameraZ, BRAIN_BLOCKS)
            PetSimulation.advance(pet, asset, owner, level, now, full, present)
            val events = owner.takeEvents()
            val directive = pet.body.directive
            val shared = PetInteractions.sharedRole(pet)
            val roleAge = if (shared != null) PetInteractions.roleAge(directive, clock) else 0f
            val placing = pet.placement.weigh(shared, roleAge, clock)
            if (used && pet.owner == local && petted(pet, asset, now)) pet.body.cheer(PETTED)
            pet.body.visible = false
            if (!PetVisibility.shown(player, local, clock)) continue
            if (asset.texture == null) continue

            val shoulder = asset.archetype == PetArchetype.Shoulder
            val alpha = pet.body.alpha(now)
            val x = if (shoulder) owner.renderX else pet.body.renderX(alpha)
            val y = if (shoulder) owner.renderY else pet.body.renderY(alpha)
            val z = if (shoulder) owner.renderZ else pet.body.renderZ(alpha)

            val onArm = shoulder && pet.owner == local && FirstPersonView.ownEyes() && !(placing > 0f)
            val reach = CULL_REACH * max(1f, asset.scale) + if (shoulder) SHOULDER_TOP else 0.0
            val top = if (shoulder) PetSimulation.height(asset) + SHOULDER_TOP else PetSimulation.height(asset)
            if (!onArm && !PetVisibility.inView(x - reach, y - CULL_HEAD_ROOM, z - reach, x + reach, y + top + reach, z + reach)) continue

            val scene = asset.scene() ?: continue
            scene.begin(frame, shiftX, shiftY, shiftZ)
            val dx = (x - cameraX).toFloat()
            val dy = (y - cameraY).toFloat()
            val dz = (z - cameraZ).toFloat()
            val scale = asset.scale * pet.body.appear(now)
            val worn = PetPose.shoulderAnchor(asset.anchor, owner)
            val shoulderAnchor = if (onArm && worn != PetPose.RIGHT_ARM && worn != PetPose.LEFT_ARM) {
                PetPose.shoulderAnchor(null, owner)
            } else {
                worn
            }
            if (shoulder) {
                if (WearerJumpRules.anchorSwitched(pet.shoulderAnchor, shoulderAnchor)) WearerJumps.broke(pet)
                pet.shoulderAnchor = shoulderAnchor
                PetPose.shoulder(root, owner, shoulderAnchor, dx, dy, dz, scale, PetOwnerParts.read(ownerEntityId(player)))
            } else {
                PetPose.root(
                    root,
                    dx,
                    dy,
                    dz,
                    Math.toRadians(pet.body.renderYaw(alpha).toDouble()).toFloat(),
                    Math.toRadians(pet.body.renderPitch(alpha).toDouble()).toFloat(),
                    scale / PIXELS_PER_BLOCK,
                )
            }
            if (!(shoulder && pet.owner == local) && !PetVisibility.clearOfCamera(
                    root[3].toDouble(),
                    root[7].toDouble(),
                    root[11].toDouble(),
                    PetSimulation.halfWidth(asset),
                    PetSimulation.height(asset),
                )
            ) {
                continue
            }
            root[3] -= shiftX
            root[7] -= shiftY
            root[11] -= shiftZ
            variables[PetVariables.MODE] = pet.body.mode
            variables[PetVariables.SPEED] = pet.body.speed
            variables[PetVariables.DISTANCE] = pet.body.distance
            variables[PetVariables.ON_GROUND] = if (pet.body.onGround) 1f else 0f
            variables[PetVariables.IN_WATER] = if (pet.body.inWater) 1f else 0f
            variables[PetVariables.MOOD] = pet.body.mood
            variables[PetVariables.INTERACTION] = directive.interaction?.value ?: 0f
            variables[PetVariables.ROLE] = directive.role.toFloat()
            variables[PetVariables.INTERACTION_TIME] = PetInteractions.interactionTime(pet, clock)
            variables[PetVariables.ACTION] = pet.body.action
            variables[PetVariables.LOOK_YAW] = pet.body.lookYaw(alpha)
            variables[PetVariables.LOOK_PITCH] = pet.body.lookPitch(alpha)
            if (shared != null) {
                PetPose.roleAnchor(
                    anchor,
                    directive.anchorX,
                    directive.anchorY,
                    directive.anchorZ,
                    directive.anchorYaw,
                    originX,
                    originY,
                    originZ,
                )
            }
            val colors = CosmeticColors.get(pet.owner, pet.cosmeticId, PreviewTryOn.lobbyTryOn(player) != null)
            pet.colors = colors
            scene.paintEffects(pet.wearer, asset.colorway, colors)
            val equip = PetEquips.events(pet.owner, pet.cosmeticId, asset.colorway, frame, clock) or lobbyEquip(player, pet.cosmeticId)
            val row = scene.add(
                pet.wearer,
                root,
                variables,
                owner.signals,
                events or equip,
                PetInteractions.celebrating(pet),
                owner.inCombat(now),
                detail(pet, ownerEntityId(player), local, x, y, z, cameraX, cameraY, cameraZ),
                pet.owner == local,
                shared,
                roleAge,
                anchor,
            )
            if (row < 0) continue
            pet.placement.commit()
            if (scenes.none { it === scene }) scenes += scene
            pet.scene = scene
            pet.row = row
            pet.body.visible = true
            pet.light = light(level, x, y + PetSimulation.height(asset) * 0.5, z)
            if (onArm) {
                armPet.pet = pet
                armPet.scene = scene
                armPet.row = row
                armPet.right = shoulderAnchor == PetPose.RIGHT_ARM
                armPet.scale = scale
                System.arraycopy(root, 0, armPet.root, 0, PetScene.ROOT_FLOATS)
                continue
            }
            drawn += pet
        }
        PetSimulation.stepPaths(now)
        for (i in scenes.indices) scenes[i].compute(clock)
        val paintedAt = System.nanoTime()
        var kept = 0
        for (i in drawn.indices) {
            val pet = drawn[i]
            val texture = pet.asset?.let { paint(pet, it, paintedAt) } ?: continue
            drawn[kept++] = pet
            textures += texture
            glows += pet.asset?.glowTexture
        }
        while (drawn.size > kept) drawn.removeAt(drawn.size - 1)
        val armed = armPet.pet
        if (armed != null) {
            armPet.texture = armed.asset?.let { paint(armed, it, paintedAt) }
            armPet.glow = armed.asset?.glowTexture
        }
    }

    private fun placeOnArm(right: Boolean, x: Float, y: Float, z: Float, xRot: Float, yRot: Float, zRot: Float): PetScene? {
        val scene = armPet.scene ?: return null
        if (armPet.texture == null || armPet.right != right || !FirstPersonView.ownEyes()) return null
        val r = armPet.root
        unrooted.set(
            r[0], r[4], r[8], 0f,
            r[1], r[5], r[9], 0f,
            r[2], r[6], r[10], 0f,
            r[3], r[7], r[11], 1f,
        ).invertAffine()
        val k = armPet.scale / PIXELS_PER_BLOCK
        armPet.placement
            .translation(x / PIXELS_PER_BLOCK, y / PIXELS_PER_BLOCK, z / PIXELS_PER_BLOCK)
            .rotateZYX(zRot, yRot, xRot)
            .translate(if (right) -ARM_OUTER else ARM_OUTER, -ARM_TOP, 0f)
            .scale(k, -k, -k)
            .mul(unrooted)
        return scene
    }

    private fun lobbyEquip(player: Any, cosmeticId: Int): Int {
        val tryOn = PreviewTryOn.lobbyTryOn(player) ?: return 0
        return if (tryOn.lobbyEquip(CosmeticWearers.frame, cosmeticId)) Renderer.EVENT_EQUIP else 0
    }

    private fun paint(pet: PetManager.Pet, asset: PetAsset, now: Long): Identifier? {
        val reaction = pet.scene?.colorway(pet.row) ?: -1
        val colorway = if (reaction >= 0) reaction else asset.colorway
        val colors = pet.colors
        val slot = pet.painted
        if (!slot.matches(asset, asset.paintGeneration, colorway, colors, now)) {
            slot.hold(asset, asset.paintGeneration, colorway, colors, asset.paint(colorway, colors, now))
        }
        val paint = slot.paint ?: return null
        paint.drawnFrame = PetFrames.frame
        return paint.texture
    }

    private fun petted(pet: PetManager.Pet, asset: PetAsset, now: Double): Boolean {
        if (asset.archetype == PetArchetype.Shoulder) return false
        val owner = pet.ownerState
        val eyeX = owner.renderX
        val eyeY = owner.renderY + owner.eyeHeight
        val eyeZ = owner.renderZ
        val yaw = Math.toRadians(owner.renderHeadYaw.toDouble())
        val pitch = Math.toRadians(owner.renderPitch.toDouble())
        val alpha = pet.body.alpha(now)
        val half = PetSimulation.halfWidth(asset)
        val x = pet.body.renderX(alpha)
        val y = pet.body.renderY(alpha)
        val z = pet.body.renderZ(alpha)
        val hit = rayBox(
            eyeX,
            eyeY,
            eyeZ,
            -sin(yaw) * cos(pitch),
            -sin(pitch),
            cos(yaw) * cos(pitch),
            x - half,
            y,
            z - half,
            x + half,
            y + PetSimulation.height(asset),
            z + half,
        )
        if (hit < 0.0 || hit > PET_REACH) return false
        val blocked = crosshairDistance(eyeX, eyeY, eyeZ)
        return blocked == null || blocked >= hit
    }

    private fun rayBox(
        ox: Double,
        oy: Double,
        oz: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
    ): Double {
        var near = 0.0
        var far = Double.MAX_VALUE
        for (axis in 0 until 3) {
            val origin = when (axis) { 0 -> ox; 1 -> oy; else -> oz }
            val direction = when (axis) { 0 -> dx; 1 -> dy; else -> dz }
            val low = when (axis) { 0 -> minX; 1 -> minY; else -> minZ }
            val high = when (axis) { 0 -> maxX; 1 -> maxY; else -> maxZ }
            if (direction == 0.0) {
                if (origin < low || origin > high) return -1.0
                continue
            }
            val a = (low - origin) / direction
            val b = (high - origin) / direction
            near = max(near, min(a, b))
            far = min(far, max(a, b))
            if (near > far) return -1.0
        }
        return near
    }

    private fun near(body: PetBody, cameraX: Double, cameraY: Double, cameraZ: Double, blocks: Double): Boolean {
        val dx = body.x - cameraX
        val dy = body.y - cameraY
        val dz = body.z - cameraZ
        return dx * dx + dy * dy + dz * dz <= blocks * blocks
    }

    private fun detail(
        pet: PetManager.Pet,
        ownerEntityId: Int,
        local: UUID,
        x: Double,
        y: Double,
        z: Double,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ): Int {
        if (pet.owner == local) return Renderer.DETAIL_FULL
        val owner = pet.ownerState
        val level = CosmeticWearers.emoteDetail(
            CosmeticWearers.wearerId(pet.owner),
            ownerEntityId,
            distanceSq(owner.renderX, owner.renderY, owner.renderZ, cameraX, cameraY, cameraZ),
        )
        val far = distanceSq(x, y, z, cameraX, cameraY, cameraZ) > MINIMAL_DETAIL_SQ
        return if (far) Renderer.DETAIL_MINIMAL else level
    }

    private fun distanceSq(x: Double, y: Double, z: Double, cameraX: Double, cameraY: Double, cameraZ: Double): Double {
        val dx = x - cameraX
        val dy = y - cameraY
        val dz = z - cameraZ
        return dx * dx + dy * dy + dz * dz
    }

    //? if > 1.8.9 {
    private fun findOwner(level: ClientLevel, uuid: UUID): Player? = level.getPlayerByUUID(uuid)

    private fun markOwnersSeen(level: ClientLevel, local: UUID, now: Double) {
        PetVisibility.pruneSights(now)
        val players = level.players()
        for (i in players.indices) {
            val player = players[i]
            if (PetVisibility.shown(player, local, now)) PetEquips.seen(player.uuid, frame, now) else PetEquips.hidden(player.uuid)
        }
    }

    private fun ownerEntityId(player: Player): Int = player.id

    private fun readOwner(owner: PetOwner, player: Player, partialTick: Float, now: Double) {
        owner.x = player.x
        owner.y = player.y
        owner.z = player.z
        owner.bodyYaw = player.yBodyRot
        owner.renderX = player.xo + (player.x - player.xo) * partialTick
        owner.renderY = player.yo + (player.y - player.yo) * partialTick
        owner.renderZ = player.zo + (player.z - player.zo) * partialTick
        owner.renderBodyYaw = lerpDegrees(partialTick, player.yBodyRotO, player.yBodyRot)
        owner.renderHeadYaw = lerpDegrees(partialTick, player.yHeadRotO, player.yHeadRot)
        owner.renderPitch = player.xRotO + (player.xRot - player.xRotO) * partialTick
        owner.limbSwing = player.walkAnimation.position(partialTick)
        owner.limbAmount = min(player.walkAnimation.speed(partialTick), 1f)
        owner.eyeHeight = player.eyeHeight.toDouble()
        owner.leftHanded = player.mainArm == HumanoidArm.LEFT
        readBody(owner, player, partialTick)
        owner.signals = PreviewTryOn.lobbySignals(player) ?: PlayerSignals.sample(player, partialTick)
        owner.observe(now)
    }

    private fun readBody(owner: PetOwner, player: Player, partialTick: Float) {
        owner.upright()
        if (player.hasPose(EntityPose.SLEEPING)) {
            val bed = player.bedOrientation
            if (bed == null) {
                owner.sleepYaw = owner.renderBodyYaw
                return
            }
            owner.sleepYaw = PetPose.sleepYaw(bed.stepX, bed.stepZ)
            val reach = player.getEyeHeight(EntityPose.STANDING) - SLEEP_EYE_DROP
            owner.sleepShiftX = -bed.stepX * reach
            owner.sleepShiftZ = -bed.stepZ * reach
            return
        }
        if (player.isFallFlying) {
            val flight = player.fallFlyingTicks + partialTick
            val amount = (flight * flight / GLIDE_RAMP).coerceIn(0f, 1f)
            if (!player.isAutoSpinAttack) owner.leanPitch = amount * (-90f - owner.renderPitch)
            val view = player.getViewVector(partialTick)
            val motion = player.deltaMovement
            owner.leanYaw = PetPose.glideYaw(view.x, view.z, motion.x, motion.z)
            return
        }
        val swim = player.getSwimAmount(partialTick)
        if (swim > 0f) {
            owner.leanPitch = swim * if (player.isInWater) -90f - owner.renderPitch else -90f
            owner.swimShift = player.isVisuallySwimming
        }
    }

    private fun useKeyDown(): Boolean = Minecraft.getInstance().options.keyUse.isDown

    private fun crosshairDistance(x: Double, y: Double, z: Double): Double? {
        val hit = Minecraft.getInstance().hitResult ?: return null
        if (hit.type == HitResult.Type.MISS) return null
        val location = hit.location
        val dx = location.x - x
        val dy = location.y - y
        val dz = location.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private val lightPos = BlockPos.MutableBlockPos()

    private fun light(level: ClientLevel, x: Double, y: Double, z: Double): Int {
        val pos = lightPos.set(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        //? if >= 26.2 {
        return LightCoordsUtil.getLightCoords(level, pos)
        //?} elif >= 26.1 {
        /*return LevelRenderer.getLightCoords(level, pos)
        *///?} else {
        /*return LevelRenderer.getLightColor(level, pos)
        *///?}
    }

    internal fun renderType(texture: Identifier): RenderType {
        //? if >= 26.1 {
        return if (PetTextures.isTranslucent(texture)) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutout(texture)
        //?} elif >= 1.21.11 {
        /*return if (PetTextures.isTranslucent(texture)) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutoutNoCull(texture)
        *///?} else {
        /*return if (PetTextures.isTranslucent(texture)) RenderType.entityTranslucent(texture) else RenderType.entityCutoutNoCull(texture)
        *///?}
    }

    internal fun glowType(texture: Identifier): RenderType = AdditiveEffectTypes.get(texture)
    //?} else {
    /*private fun findOwner(level: ClientLevel, uuid: UUID): PlayerEntity? = level.getPlayer(uuid)

    private fun markOwnersSeen(level: ClientLevel, local: UUID, now: Double) {
        PetVisibility.pruneSights(now)
        val players = level.players
        for (i in players.indices) {
            val player = players[i]
            if (PetVisibility.shown(player, local, now)) PetEquips.seen(player.uuid, frame, now) else PetEquips.hidden(player.uuid)
        }
    }

    private fun ownerEntityId(player: PlayerEntity): Int = player.networkId

    private fun readOwner(owner: PetOwner, player: PlayerEntity, partialTick: Float, now: Double) {
        owner.x = player.x
        owner.y = player.y
        owner.z = player.z
        owner.bodyYaw = player.bodyYaw
        owner.renderX = player.prevX + (player.x - player.prevX) * partialTick
        owner.renderY = player.prevY + (player.y - player.prevY) * partialTick
        owner.renderZ = player.prevZ + (player.z - player.prevZ) * partialTick
        owner.renderBodyYaw = lerpDegrees(partialTick, player.lastBodyYaw, player.bodyYaw)
        owner.renderHeadYaw = lerpDegrees(partialTick, player.lastHeadYaw, player.headYaw)
        owner.renderPitch = player.lastPitch + (player.pitch - player.lastPitch) * partialTick
        owner.limbSwing = player.walkAnimationProgress - player.walkAnimationSpeed * (1f - partialTick)
        owner.limbAmount = min(player.lastWalkAnimationSpeed + (player.walkAnimationSpeed - player.lastWalkAnimationSpeed) * partialTick, 1f)
        owner.eyeHeight = player.eyeHeight.toDouble()
        owner.upright()
        if (player.isAlive && player.isSleeping) {
            owner.sleepYaw = player.sleepingCameraAngle
            owner.sleepShiftX = player.sleepingCameraOffsetX
            owner.sleepShiftY = player.sleepingCameraOffsetY
            owner.sleepShiftZ = player.sleepingCameraOffsetZ
        }
        owner.signals = PreviewTryOn.lobbySignals(player) ?: PlayerSignals.sample(player, partialTick)
        owner.observe(now)
    }

    private fun useKeyDown(): Boolean = Minecraft.getInstance().options.useKey.isPressed

    private fun crosshairDistance(x: Double, y: Double, z: Double): Double? {
        val hit = Minecraft.getInstance().crosshairTarget ?: return null
        if (hit.type == HitResult.Type.MISS) return null
        val location = hit.facePos ?: return null
        val dx = location.x - x
        val dy = location.y - y
        val dz = location.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun light(level: ClientLevel, x: Double, y: Double, z: Double): Int = level.getLightColor(BlockPos(x, y, z), 0)
    *///?}

    private fun lerpDegrees(t: Float, from: Float, to: Float): Float = from + PetSimulation.wrapDegrees(to - from) * t

    //? if > 1.8.9 {
    private fun placeOnArm(arm: ModelPart, right: Boolean): PetScene? =
        placeOnArm(right, arm.x, arm.y, arm.z, arm.xRot, arm.yRot, arm.zRot)
    //?} else {
    /*private fun placeOnArm(arm: ModelPart, right: Boolean): PetScene? =
        placeOnArm(
            right,
            arm.translateX * PIXELS_PER_BLOCK + arm.x,
            arm.translateY * PIXELS_PER_BLOCK + arm.y,
            arm.translateZ * PIXELS_PER_BLOCK + arm.z,
            arm.rotationX,
            arm.rotationY,
            arm.rotationZ,
        )
    *///?}

    //? if >= 1.21.10 {
    private val armDraw = ArmDraw()
    private val armGlowDraw = ArmDraw()

    private class ArmDraw : PooledGeometry {
        var scene: PetScene? = null
        var row = -1
        var light = 0
        val placement = Matrix4f()

        override fun render(pose: Pose, buffer: VertexConsumer) {
            scene?.emitPlaced(row, placement, pose, buffer, light, OverlayTexture.NO_OVERLAY)
        }
    }

    @JvmStatic
    fun submitFirstPerson(poseStack: PoseStack, collector: SubmitNodeCollector, light: Int, arm: ModelPart, right: Boolean) {
        val scene = placeOnArm(arm, right) ?: return
        val texture = armPet.texture ?: return
        armDraw.scene = scene
        armDraw.row = armPet.row
        armDraw.light = light
        armDraw.placement.set(armPet.placement)
        collector.submitCustomGeometry(poseStack, renderType(texture), armDraw)
        val glow = armPet.glow ?: return
        armGlowDraw.scene = scene
        armGlowDraw.row = armPet.row
        armGlowDraw.light = FULL_BRIGHT
        armGlowDraw.placement.set(armPet.placement)
        collector.submitCustomGeometry(poseStack, glowType(glow), armGlowDraw)
    }

    private val draws = ArrayList<PetDraw>()
    private val glowDraws = ArrayList<PetDraw>()

    private fun releaseDraws() {
        for (draw in draws) draw.scene = null
        for (draw in glowDraws) draw.scene = null
        armDraw.scene = null
        armGlowDraw.scene = null
    }

    private class PetDraw : PooledGeometry {
        var scene: PetScene? = null
        var row = -1
        var light = 0

        override fun render(pose: Pose, buffer: VertexConsumer) {
            scene?.emit(row, pose, buffer, light, OverlayTexture.NO_OVERLAY)
        }
    }

    @JvmStatic
    fun extract(level: ClientLevel?, camera: Camera, frustum: Frustum, deltaTracker: DeltaTracker) {
        PetVisibility.frustum = frustum
        if (level == null) {
            reset()
            return
        }
        val position = camera.position()
        prepare(level, position.x, position.y, position.z, deltaTracker.getGameTimeDeltaPartialTick(false))
    }

    @JvmStatic
    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector) {
        for (i in drawn.indices) {
            val pet = drawn[i]
            if (i == draws.size) draws += PetDraw()
            val draw = draws[i]
            draw.scene = pet.scene
            draw.row = pet.row
            draw.light = pet.light
            collector.submitCustomGeometry(poseStack, renderType(textures[i]), draw)
            val glow = glows[i] ?: continue
            while (glowDraws.size <= i) glowDraws += PetDraw()
            val lit = glowDraws[i]
            lit.scene = pet.scene
            lit.row = pet.row
            lit.light = FULL_BRIGHT
            collector.submitCustomGeometry(poseStack, glowType(glow), lit)
        }
    }
    //?}

    //? if > 1.8.9 && < 1.21.10 {
    /*@JvmStatic
    fun renderFirstPerson(poseStack: PoseStack, buffers: MultiBufferSource, light: Int, arm: ModelPart, right: Boolean) {
        val scene = placeOnArm(arm, right) ?: return
        val texture = armPet.texture ?: return
        val pose = poseStack.last()
        scene.emitPlaced(armPet.row, armPet.placement, pose, buffers.getBuffer(renderType(texture)), light, OverlayTexture.NO_OVERLAY)
        val glow = armPet.glow ?: return
        scene.emitPlaced(armPet.row, armPet.placement, pose, buffers.getBuffer(glowType(glow)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
    }
    *///?}

    //? if >= 1.21.4 && < 1.21.10 {
    /*@JvmStatic
    fun capture(frustum: Frustum) {
        PetVisibility.frustum = frustum
    }

    @JvmStatic
    fun render(level: ClientLevel?, camera: Camera, deltaTracker: DeltaTracker, poseStack: PoseStack, buffers: MultiBufferSource) {
        if (level == null) return
        val position = camera.position
        prepare(level, position.x, position.y, position.z, deltaTracker.getGameTimeDeltaPartialTick(false))
        draw(poseStack, buffers)
    }

    private fun draw(poseStack: PoseStack, buffers: MultiBufferSource) {
        val pose = poseStack.last()
        for (i in drawn.indices) {
            val pet = drawn[i]
            pet.scene?.emit(pet.row, pose, buffers.getBuffer(renderType(textures[i])), pet.light, OverlayTexture.NO_OVERLAY)
            val glow = glows[i] ?: continue
            pet.scene?.emit(pet.row, pose, buffers.getBuffer(glowType(glow)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        }
        for (sceneIndex in scenes.indices) if (scenes[sceneIndex].hasEffects) CosmeticEffects.drawNow(scenes[sceneIndex].effectSource, poseStack, buffers)
    }
    *///?}

    //? if = 1.21.1 {
    /*private val identity = PoseStack()

    @JvmStatic
    fun render(level: ClientLevel?, camera: Camera, frustum: Frustum, deltaTracker: DeltaTracker) {
        PetVisibility.frustum = frustum
        if (level == null) return
        val position = camera.position
        prepare(level, position.x, position.y, position.z, deltaTracker.getGameTimeDeltaPartialTick(false))
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
        val pose = identity.last()
        for (i in drawn.indices) {
            val pet = drawn[i]
            pet.scene?.emit(pet.row, pose, buffers.getBuffer(renderType(textures[i])), pet.light, OverlayTexture.NO_OVERLAY)
            val glow = glows[i] ?: continue
            pet.scene?.emit(pet.row, pose, buffers.getBuffer(glowType(glow)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        }
        for (sceneIndex in scenes.indices) if (scenes[sceneIndex].hasEffects) CosmeticEffects.drawNow(scenes[sceneIndex].effectSource, identity, buffers)
    }
    *///?}

    //? if = 1.8.9 {
    /*private val identity = PoseStack()

    @JvmStatic
    fun renderFirstPerson(player: PlayerEntity, arm: ModelPart, right: Boolean) {
        val scene = placeOnArm(arm, right) ?: return
        val texture = armPet.texture ?: return
        val partialTick = FirstPersonEffects.partialTick(Minecraft.getInstance())
        val light = if (player.isOnFire) FULL_BRIGHT else player.getLightLevel(partialTick)
        val pose = identity.last()
        VertexConsumer.draw(texture, PetTextures.isTranslucent(texture)) { buffer -> scene.emitPlaced(armPet.row, armPet.placement, pose, buffer, light, 0) }
        val glow = armPet.glow ?: return
        VertexConsumer.draw(glow, true, additive = true) { buffer -> scene.emitPlaced(armPet.row, armPet.placement, pose, buffer, FULL_BRIGHT, 0) }
    }

    @JvmStatic
    fun render(level: ClientLevel?, culler: Culler, tickDelta: Float) {
        PetVisibility.frustum = culler
        if (level == null) return
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        prepare(level, dispatcher.cameraX, dispatcher.cameraY, dispatcher.cameraZ, tickDelta)
        if (drawn.isNotEmpty()) drawPets(mc)
        for (sceneIndex in scenes.indices) if (scenes[sceneIndex].hasEffects) CosmeticEffects.drawNow(scenes[sceneIndex].effectSource)
    }

    private fun drawPets(mc: Minecraft) {
        mc.gameRenderer.enableLightMap()
        Lighting.turnOn()
        GlStateManager.enableRescaleNormal()
        GlStateManager.disableCull()
        GlStateManager.enableAlphaTest()
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        GlStateManager.disableBlend()
        val pose = identity.last()
        for (i in drawn.indices) {
            val pet = drawn[i]
            val scene = pet.scene ?: continue
            VertexConsumer.draw(textures[i], PetTextures.isTranslucent(textures[i])) { buffer -> scene.emit(pet.row, pose, buffer, pet.light, 0) }
            val glow = glows[i] ?: continue
            VertexConsumer.draw(glow, true, additive = true) { buffer -> scene.emit(pet.row, pose, buffer, FULL_BRIGHT, 0) }
        }
        if (blend) GlStateManager.enableBlend()
        GlStateManager.enableCull()
        GlStateManager.disableRescaleNormal()
        Lighting.turnOff()
        mc.gameRenderer.disableLightMap()
    }
    *///?}

    private const val SHOULDER_TOP = 1.8
    //? if > 1.8.9 {
    private const val SLEEP_EYE_DROP = 0.1f
    private const val GLIDE_RAMP = 100f
    //?}
    private const val ARM_OUTER = 1f / 16f
    private const val ARM_TOP = 2f / 16f
    private const val FULL_BRIGHT = 0xF000F0
    private const val MINIMAL_DETAIL_SQ = CosmeticWearers.MINIMAL_DETAIL_BLOCKS * CosmeticWearers.MINIMAL_DETAIL_BLOCKS
}
