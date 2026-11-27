package org.polyfrost.polyplus.client.render

import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
//?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
*///?}

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
*///?}

data class PlayerRenderContext(
    val ageInTicks: Float,
    val walkAnimationSpeed: Float,
    val isCrouching: Boolean,
    val isOnGround: Boolean,
    val isInWater: Boolean,
    val swimAmount: Float,
    val isInvisible: Boolean,
    val signals: PlayerSignalRecord = PlayerSignals.NEUTRAL,
) {
    companion object {
        //? if >= 1.21.4 {
        fun from(state: HumanoidRenderState): PlayerRenderContext = from(
            PlayerSignals.of(state),
            ageInTicks = state.ageInTicks,
            walkAnimationSpeed = state.walkAnimationSpeed,
            swimAmount = state.swimAmount,
            isInvisible = state.isInvisible,
        )
        //?} elif = 1.21.1 {
        /*fun from(
            player: AbstractClientPlayer,
            partialTicks: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
        ): PlayerRenderContext = from(
            if (PlayerPreviewRenderer.isPreviewEntity(player)) PreviewTryOn.previewSignals() else PreviewTryOn.lobbySignals(player) ?: PlayerSignals.sample(player),
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
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
        ): PlayerRenderContext = from(
            PreviewTryOn.lobbySignals(player) ?: PlayerSignals.sample(player),
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
            swimAmount = 0f,
            isInvisible = player.isInvisible,
        )
        *///?}

        private fun from(
            signals: PlayerSignalRecord,
            ageInTicks: Float,
            walkAnimationSpeed: Float,
            swimAmount: Float,
            isInvisible: Boolean,
        ): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = ageInTicks,
            walkAnimationSpeed = walkAnimationSpeed,
            isCrouching = signals.flag(PlayerSignals.IS_SNEAKING),
            isOnGround = signals.flag(PlayerSignals.IS_ON_GROUND),
            isInWater = signals.flag(PlayerSignals.IS_IN_WATER),
            swimAmount = swimAmount,
            isInvisible = isInvisible,
            signals = signals,
        )
    }
}
