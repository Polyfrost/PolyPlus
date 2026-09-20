package org.polyfrost.polyplus.client.emotes.conditions

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
import kotlin.math.sqrt
*///?}

object EmoteConditions {
    private const val WALK_THRESHOLD = 0.02f
    private const val FALL_THRESHOLD = -0.08

    //? if > 1.8.9 {
    @JvmStatic
    fun allows(player: AbstractClientPlayer, rules: EmoteRules): Boolean {
        if (player.isSleeping
            || player.isBlocking
            //? if >= 26.3 {
            || player.isSwinging
            || player.getSwingAnimation(1.0f) > 0f
            //?} else {
            /*|| player.swinging
            || player.getAttackAnim(1.0f) > 0f
            *///?}
        ) {
            return false
        }

        if (!rules.allowCrouching && player.isCrouching) {
            return false
        }

        if (!rules.allowSwimming && player.isVisuallySwimming) {
            return false
        }

        val isFlying =
            player.isFallFlying
            //? if >= 1.21.10
            || player.isFlyingVehicle
        if (!rules.allowElytraFlying && isFlying) {
            return false
        }

        if (!rules.allowFalling && !player.onGround() && player.deltaMovement.y < FALL_THRESHOLD) {
            return false
        }

        val horizontalSpeed = player.deltaMovement.horizontalDistance()
        if (horizontalSpeed > WALK_THRESHOLD) {
            if (!rules.allowWalking) {
                return false
            }
            if (!rules.allowSprinting && player.isSprinting) {
                return false
            }
        }

        return true
    }
    //?} else {
    /*@JvmStatic
    fun allows(player: AbstractClientPlayer, rules: EmoteRules): Boolean {
        if (player.isSleeping
            || player.isSwordBlocking
            || player.armSwinging
            || player.getAttackAnimationProgress(1.0f) > 0f
        ) {
            return false
        }

        if (!rules.allowCrouching && player.isSneaking) {
            return false
        }

        if (!rules.allowFalling && !player.onGround && player.y - player.lastY < FALL_THRESHOLD) {
            return false
        }

        val dx = player.x - player.lastX
        val dz = player.z - player.lastZ
        val horizontalSpeed = sqrt(dx * dx + dz * dz)
        if (horizontalSpeed > WALK_THRESHOLD) {
            if (!rules.allowWalking) {
                return false
            }
            if (!rules.allowSprinting && player.isSprinting) {
                return false
            }
        }

        return true
    }
    *///?}
}
