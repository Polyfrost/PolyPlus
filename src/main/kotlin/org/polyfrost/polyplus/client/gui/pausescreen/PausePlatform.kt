package org.polyfrost.polyplus.client.gui.pausescreen

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager

//? if > 1.8.9 {
import net.minecraft.client.CameraType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
//?} else {
/*import net.minecraft.block.material.Material
import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.util.math.Vec3d
*///?}

internal class PauseVec(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double) {
    fun add(other: PauseVec) = PauseVec(x + other.x, y + other.y, z + other.z)
    fun sub(other: PauseVec) = PauseVec(x - other.x, y - other.y, z - other.z)
    fun scale(by: Double) = PauseVec(x * by, y * by, z * by)
    fun dot(other: PauseVec) = x * other.x + y * other.y + z * other.z
    fun length() = Math.sqrt(dot(this))
    fun lerp(to: PauseVec, t: Double) = add(to.sub(this).scale(t))
}

internal object PausePlatform {
    private const val FIRST_PERSON = 0
    private const val THIRD_PERSON = 1
    private const val JITTER = 0.1
    private const val CORNERS = 8

    fun player(): LivingEntity? {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        return if (mc.level != null) mc.player else null
        //?} else {
        /*return if (mc.world != null) mc.player else null
        *///?}
    }

    fun feet(entity: LivingEntity, partial: Float): PauseVec {
        //? if > 1.8.9 {
        return PauseVec(
            entity.xo + (entity.x - entity.xo) * partial,
            entity.yo + (entity.y - entity.yo) * partial,
            entity.zo + (entity.z - entity.zo) * partial,
        )
        //?} else {
        /*return PauseVec(
            entity.prevX + (entity.x - entity.prevX) * partial,
            entity.prevY + (entity.y - entity.prevY) * partial,
            entity.prevZ + (entity.z - entity.prevZ) * partial,
        )
        *///?}
    }

    fun eyeHeight(entity: LivingEntity): Double = entity.eyeHeight.toDouble()

    fun height(entity: LivingEntity): Double =
        //? if > 1.8.9 {
        entity.bbHeight.toDouble()
        //?} else {
        /*entity.height.toDouble()
        *///?}

    fun yaw(entity: LivingEntity, partial: Float): Float =
        //? if > 1.8.9 {
        entity.getViewYRot(partial)
        //?} else {
        /*entity.lastYaw + (entity.yaw - entity.lastYaw) * partial
        *///?}

    fun pitch(entity: LivingEntity, partial: Float): Float =
        //? if > 1.8.9 {
        entity.getViewXRot(partial)
        //?} else {
        /*entity.lastPitch + (entity.pitch - entity.lastPitch) * partial
        *///?}

    fun bodyYaw(entity: LivingEntity): Float =
        //? if > 1.8.9 {
        entity.yBodyRot
        //?} else {
        /*entity.bodyYaw
        *///?}

    fun headYaw(entity: LivingEntity): Float =
        //? if > 1.8.9 {
        entity.yHeadRot
        //?} else {
        /*entity.headYaw
        *///?}

    private fun clip(entity: LivingEntity, from: PauseVec, to: PauseVec): Double {
        val reach = to.sub(from).length()
        //? if > 1.8.9 {
        val start = Vec3(from.x, from.y, from.z)
        val hit = entity.level().clip(ClipContext(start, Vec3(to.x, to.y, to.z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity))
        return if (hit.type == HitResult.Type.MISS) reach else start.distanceTo(hit.location)
        //?} else {
        /*val start = Vec3d(from.x, from.y, from.z)
        val hit = Minecraft.getInstance().world?.rayTrace(start, Vec3d(to.x, to.y, to.z)) ?: return reach
        return start.distanceTo(hit.facePos)
        *///?}
    }

    fun clearance(entity: LivingEntity, from: PauseVec, to: PauseVec): Double {
        var shortest = to.sub(from).length()
        for (corner in 0 until CORNERS) {
            val offset = PauseVec(
                ((corner and 1) * 2 - 1) * JITTER,
                ((corner shr 1 and 1) * 2 - 1) * JITTER,
                ((corner shr 2 and 1) * 2 - 1) * JITTER,
            )
            shortest = minOf(shortest, clip(entity, from.add(offset), to.add(offset)))
        }
        return shortest
    }

    fun canStage(entity: LivingEntity): Boolean {
        //? if > 1.8.9 {
        val spectating = (entity as? Player)?.isSpectator == true
        return !spectating && !entity.isPassenger && !entity.isSleeping && !entity.isDeadOrDying &&
            !entity.isFallFlying && !entity.isSwimming && !entity.isUnderWater && !entity.isInLava
        //?} else {
        /*val player = entity as? PlayerEntity
        return player?.isSpectator != true && player?.isSleeping != true && !entity.isRiding && entity.health > 0f &&
            !entity.isSubmergedIn(Material.WATER) && !entity.isInLava
        *///?}
    }

    var cameraMode: Int
        get() {
            //? if > 1.8.9 {
            return Minecraft.getInstance().options.cameraType.ordinal
            //?} else {
            /*return Minecraft.getInstance().options.perspective
            *///?}
        }
        set(value) {
            //? if > 1.8.9 {
            Minecraft.getInstance().options.setCameraType(CameraType.entries[value])
            //?} else {
            /*Minecraft.getInstance().options.perspective = value
            *///?}
        }

    fun firstPerson(mode: Int): Boolean = mode == FIRST_PERSON

    fun thirdPerson(mode: Int): Boolean = mode == THIRD_PERSON

    fun showBody() {
        cameraMode = THIRD_PERSON
    }

    var hudHidden: Boolean
        get() {
            //? if >= 26.2 {
            return Minecraft.getInstance().gui.hud.isHidden
            //?} else {
            /*return Minecraft.getInstance().options.hideGui
            *///?}
        }
        set(value) {
            //? if >= 26.2 {
            val hud = Minecraft.getInstance().gui.hud
            if (hud.isHidden != value) hud.toggle()
            //?} else {
            /*Minecraft.getInstance().options.hideGui = value
            *///?}
        }

    fun motionScale(): Float {
        val options = Minecraft.getInstance().options
        //? if > 1.8.9 {
        val bob = if (options.bobView().get()) 1f else 0f
        return bob * options.screenEffectScale().get().toFloat()
        //?} else {
        /*return if (options.viewBobbing) 1f else 0f
        *///?}
    }

    fun userFov(): Float {
        val options = Minecraft.getInstance().options
        //? if > 1.8.9 {
        return options.fov().get().toFloat()
        //?} else {
        /*return options.fov
        *///?}
    }

    fun aspect(): Float {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        return mc.window.width.toFloat() / mc.window.height.coerceAtLeast(1)
        //?} else {
        /*return mc.width.toFloat() / mc.height.coerceAtLeast(1)
        *///?}
    }

    fun gamePaused(): Boolean = Minecraft.getInstance().isPaused

    fun pausesWorld(): Boolean = SocialHosting.singleplayer && !SocialHosting.lanOpen && P2PSessionManager.currentSessionId == null

    fun skyDarkness(entity: LivingEntity): Int {
        //? if > 1.8.9 {
        return entity.level().skyDarken
        //?} else {
        /*return entity.world.calculateAmbientDarkness(1f)
        *///?}
    }
}
