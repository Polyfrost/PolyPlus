package org.polyfrost.polyplus.client.nameplate

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
//? if >= 1.21.1 {
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.scores.Team
//?} else {
/*import net.minecraft.entity.living.LivingEntity
import net.minecraft.scoreboard.team.AbstractTeam
*///?}

object NameplateSelfVisibility {
    //? if >= 1.21.1 {
    private const val DISCRETE_MAX_DISTANCE_SQ = 32.0 * 32.0
    //?}
    private const val POLYNAMETAG_ID = "polynametag"

    private val polyNametagLoaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded(POLYNAMETAG_ID) }

    fun deferredToPolyNametag(): Boolean = polyNametagLoaded

    @JvmStatic
    fun appliesTo(entity: LivingEntity): Boolean {
        if (polyNametagLoaded) return false
        val player = Minecraft.getInstance().player ?: return false
        return entity === player && NameplateRegistry.boundFor(player.uuid) != null
    }

    //? if >= 1.21.1 {
    @JvmStatic
    fun shouldShow(entity: LivingEntity, distanceToCameraSq: Double): Boolean {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        //? if >= 26.2 {
        if (minecraft.gui.hud.isHidden) return false
        //?} else {
        /*if (minecraft.options.hideGui) return false
        *///?}
        if (entity.isDiscrete && distanceToCameraSq >= DISCRETE_MAX_DISTANCE_SQ) return false

        val visible = !entity.isInvisibleTo(player)
        val team = entity.team ?: return visible && !entity.isVehicle
        return when (team.nameTagVisibility) {
            Team.Visibility.ALWAYS -> visible
            Team.Visibility.NEVER, Team.Visibility.HIDE_FOR_OWN_TEAM -> false
            Team.Visibility.HIDE_FOR_OTHER_TEAMS -> team.canSeeFriendlyInvisibles() || visible
        }
    }
    //?} else {
    /*@JvmStatic
    fun shouldShow(entity: LivingEntity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (!Minecraft.isDisplayGui()) return false

        val visible = !entity.isInvisibleTo(player)
        val team = entity.scoreboardTeam ?: return visible && entity.rider == null
        return when (team.nameTagVisibility) {
            AbstractTeam.Visibility.NEVER, AbstractTeam.Visibility.HIDE_FOR_OWN_TEAM -> false
            AbstractTeam.Visibility.HIDE_FOR_OTHER_TEAMS -> team.showFriendlyInvisibles() || visible
            else -> visible
        }
    }
    *///?}
}
