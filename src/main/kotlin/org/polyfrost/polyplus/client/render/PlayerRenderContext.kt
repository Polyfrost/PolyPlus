package org.polyfrost.polyplus.client.render

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
//?} else {
/*import net.minecraft.client.player.AbstractClientPlayer
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
        //?} else {
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
        *///?}
    }
}
