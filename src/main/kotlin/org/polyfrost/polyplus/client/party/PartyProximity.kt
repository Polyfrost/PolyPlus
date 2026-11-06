package org.polyfrost.polyplus.client.party

import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.level
import org.polyfrost.polyplus.client.lobby.uuid
import org.polyfrost.polyplus.client.lobby.yRot
*///?}
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyMember
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object PartyProximity {
    private const val FULL_VOLUME_BLOCKS = 4.0

    class Gain(val gain: Float, val pan: Float)

    private val NORMAL = Gain(1f, 0f)
    private val SILENT = Gain(0f, 0f)

    private class Spot(val x: Double, val y: Double, val z: Double)

    fun gains(party: Party, myKey: String?): Map<String, Gain> {
        val members = PartyRepository.others(party)
        if (party.settings.voiceMode != PartyVoiceMode.Proximity) return members.associate { it.player to NORMAL }
        val world = worldPlayers()
        val me = world?.let { localSpot() }
        val yaw = localYaw()
        val range = PartyVoiceSettings.proximityRange().toDouble()
        return members.associate { member ->
            val spot = if (world != null && me != null) find(world, member) else null
            val gain = when {
                spot != null && me != null -> distanceGain(me, spot, range, yaw)
                myKey != null && member.state?.location?.key == myKey -> SILENT
                else -> NORMAL
            }
            member.player to gain
        }
    }

    fun distanceTo(member: PartyMember): Double? {
        val world = worldPlayers() ?: return null
        val me = localSpot() ?: return null
        val spot = find(world, member) ?: return null
        val dx = spot.x - me.x
        val dy = spot.y - me.y
        val dz = spot.z - me.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun distanceGain(me: Spot, other: Spot, range: Double, yaw: Float): Gain {
        val dx = other.x - me.x
        val dy = other.y - me.y
        val dz = other.z - me.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val gain = when {
            distance <= FULL_VOLUME_BLOCKS -> 1.0
            distance >= range -> 0.0
            else -> 1.0 - (distance - FULL_VOLUME_BLOCKS) / (range - FULL_VOLUME_BLOCKS)
        }
        val facing = Math.toRadians(yaw.toDouble())
        val forwardX = -sin(facing)
        val forwardZ = cos(facing)
        val angle = atan2(dx * forwardZ - dz * forwardX, dx * forwardX + dz * forwardZ)
        return Gain(gain.toFloat(), (-sin(angle)).toFloat())
    }

    private fun find(world: Map<UUID, Pair<String, Spot>>, member: PartyMember): Spot? {
        val uuid = runCatching { UUID.fromString(member.player) }.getOrNull()
        uuid?.let { id -> world[id]?.let { return it.second } }
        val name = PlayerNamesRepository.names.value[member.player] ?: return null
        return world.values.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    private fun worldPlayers(): Map<UUID, Pair<String, Spot>>? {
        if (LobbyScene.terrainActive()) return null
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val self = mc.player ?: return null
        //? if > 1.8.9 {
        return level.players().filter { it !== self }.associate { it.uuid to (it.name.string to Spot(it.x, it.y, it.z)) }
        //?} else {
        /*return level.players.filter { it !== self }.associate { it.uuid to (it.gameProfile.name to Spot(it.x, it.y, it.z)) }
        *///?}
    }

    private fun localSpot(): Spot? {
        val player = Minecraft.getInstance().player ?: return null
        return Spot(player.x, player.y, player.z)
    }

    private fun localYaw(): Float = Minecraft.getInstance().player?.yRot ?: 0f
}
