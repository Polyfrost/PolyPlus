package org.polyfrost.polyplus.client.render

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
//?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
*///?}

data class PlayerRenderContext(
    val ageInTicks: Float,
    val walkAnimationSpeed: Float,
    val isCrouching: Boolean,
    val isOnGround: Boolean,
    val isInWater: Boolean,
    val swimAmount: Float,
    val isInvisible: Boolean,
) {
    companion object {
        //? if >= 1.21.4 {
        fun from(state: HumanoidRenderState): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = state.ageInTicks,
            walkAnimationSpeed = state.walkAnimationSpeed,
            isCrouching = state.isCrouching,
            isOnGround = state.walkAnimationSpeed > 0.01f || state.isCrouching,
            isInWater = state.isInWater,
            swimAmount = state.swimAmount,
            isInvisible = state.isInvisible,
        )
        //?} elif = 1.21.1 {
        /*fun from(
            player: AbstractClientPlayer,
            partialTicks: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
        ): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
            isCrouching = player.isCrouching,
            isOnGround = player.onGround(),
            isInWater = player.isInWater,
            swimAmount = player.getSwimAmount(partialTicks),
            isInvisible = player.isInvisible,
        )
        *///?} else {
        /*@JvmStatic
        fun from(
            player: ClientPlayerEntity,
            @Suppress("UNUSED_PARAMETER") partialTicks: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
        ): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
            isCrouching = player.isSneaking,
            isOnGround = player.onGround,
            isInWater = player.isInWater,
            swimAmount = 0f,
            isInvisible = player.isInvisible,
        )
        *///?}
    }
}
