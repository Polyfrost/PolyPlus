package org.polyfrost.polyplus.client.emotes.conditions

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
*///?}

object EmoteConditions {
    //? if > 1.8.9 {
    @JvmStatic
    fun blocking(player: AbstractClientPlayer): Boolean = player.isBlocking

    @JvmStatic
    fun swinging(player: AbstractClientPlayer): Boolean =
        //? if >= 26.3 {
        player.isSwinging || player.getSwingAnimation(1.0f) > 0f
        //?} else {
        /*player.swinging || player.getAttackAnim(1.0f) > 0f
        *///?}
    //?} else {
    /*@JvmStatic
    fun blocking(player: AbstractClientPlayer): Boolean = player.isSwordBlocking

    @JvmStatic
    fun swinging(player: AbstractClientPlayer): Boolean =
        player.armSwinging || player.getAttackAnimationProgress(1.0f) > 0f
    *///?}

    @JvmStatic
    fun interrupts(player: AbstractClientPlayer): Boolean = blocking(player) || swinging(player)
}
