package org.polyfrost.polyplus.client.emotes.conditions

import net.minecraft.client.player.AbstractClientPlayer

object EmoteConditions {
    private const val WALK_THRESHOLD = 0.02f
    private const val FALL_THRESHOLD = -0.08

    @JvmStatic
    fun allows(player: AbstractClientPlayer, rules: EmoteRules): Boolean {
        if (player.isSleeping
            || player.isBlocking
            || player.swinging
            || player.getAttackAnim(1.0f) > 0f
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
}
