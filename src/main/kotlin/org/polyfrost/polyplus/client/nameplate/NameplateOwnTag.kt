package org.polyfrost.polyplus.client.nameplate

import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.gui.pausescreen.PausePlatform
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
//? if >= 1.21.10 {
import org.polyfrost.polyplus.client.nameplate.render.NameTags
//?} else {
/*import org.polyfrost.polyplus.client.nameplate.render.NameTagSupport
*///?}
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.scores.Team
//?} else {
/*import net.minecraft.client.gui.screen.game.inventory.PlayerInventoryScreen
import net.minecraft.client.render.Camera
import net.minecraft.entity.living.LivingEntity
import net.minecraft.scoreboard.team.AbstractTeam
*///?}

object NameplateOwnTag {
    private const val DISCRETE_DISTANCE_SQ = 32.0 * 32.0
    //? if = 1.21.1
    /*private const val NAME_DISTANCE_SQ = 64.0 * 64.0*/
    //? if = 1.8.9
    /*private const val LEGACY_NAME_DISTANCE_SQ = 64.0 * 64.0*/

    @JvmStatic
    fun shows(original: Boolean, entity: LivingEntity, distanceSq: Double): Boolean {
        if (original) return true
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (entity !== player) return false
        if (NameplatePolyNametag.installed) return false
        if (!thirdPerson(mc) || inventoryOpen() || PausePlatform.hudHidden) return false
        if (!bound(player.uuid)) return false
        //? if > 1.8.9 {
        if (entity.isDiscrete && distanceSq >= DISCRETE_DISTANCE_SQ) return false
        //? if = 1.21.1
        /*if (distanceSq >= NAME_DISTANCE_SQ) return false*/
        val visible = !entity.isInvisibleTo(player)
        val team = entity.team ?: return visible && !entity.isVehicle
        return when (team.nameTagVisibility) {
            Team.Visibility.ALWAYS -> visible
            Team.Visibility.HIDE_FOR_OTHER_TEAMS -> team.canSeeFriendlyInvisibles() || visible
            else -> false
        }
        //?} else {
        /*if (distanceSq >= if (entity.isSneaking) DISCRETE_DISTANCE_SQ else LEGACY_NAME_DISTANCE_SQ) return false
        val team = entity.scoreboardTeam ?: return !entity.isInvisibleTo(player) && entity.rider == null
        return when (team.nameTagVisibility) {
            AbstractTeam.Visibility.ALWAYS, AbstractTeam.Visibility.HIDE_FOR_OTHER_TEAMS -> true
            else -> false
        }
        *///?}
    }

    //? if = 1.8.9 {
    /*@JvmStatic
    fun cameraDistanceSq(entity: LivingEntity): Double {
        val camera = Minecraft.getInstance().camera ?: return 0.0
        val offset = Camera.offset() ?: return 0.0
        val dx = camera.x + offset.x - entity.x
        val dy = camera.y + offset.y - entity.y
        val dz = camera.z + offset.z - entity.z
        return dx * dx + dy * dy + dz * dz
    }
    *///?}

    private fun bound(wearer: UUID): Boolean {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, wearer)) return false
        val equip = NameplateWearers.resolve(wearer) ?: return false
        //? if >= 1.21.10 {
        return !NameTags.failed(equip.bundle)
        //?} else {
        /*return !NameTagSupport.failed(equip.bundle)
        *///?}
    }

    private fun thirdPerson(mc: Minecraft): Boolean {
        //? if > 1.8.9 {
        return !mc.options.cameraType.isFirstPerson
        //?} else {
        /*return mc.options.perspective != 0
        *///?}
    }

    private fun inventoryOpen(): Boolean {
        val screen = ClientPlatform.currentScreen()
        //? if > 1.8.9 {
        return screen is InventoryScreen || screen is CreativeModeInventoryScreen
        //?} else {
        /*return screen is PlayerInventoryScreen
        *///?}
    }
}
