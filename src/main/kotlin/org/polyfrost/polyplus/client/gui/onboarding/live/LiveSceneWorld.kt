package org.polyfrost.polyplus.client.gui.onboarding.live

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import org.polyfrost.polyplus.api.lobby.LobbyListener
import org.polyfrost.polyplus.client.lobby.LobbyEvents
import org.polyfrost.polyplus.client.lobby.LobbyScene
import java.lang.ref.WeakReference
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.lobby.level*/

internal object LiveSceneWorld : LobbyListener {
    private var seen = WeakReference<ClientLevel>(null)
    private var listening = false

    override fun onSceneBuilt(level: ClientLevel) {
        seen = WeakReference(level)
    }

    override fun onSwapIn(level: ClientLevel) {
        seen = WeakReference(level)
    }

    override fun onSceneEnded() {
        seen = WeakReference(null)
    }

    fun level(): ClientLevel? {
        if (!listening) {
            LobbyEvents.add(this)
            listening = true
        }
        val current = Minecraft.getInstance().level?.takeIf { LobbyScene.isSceneLevel(it) }
        if (current != null) seen = WeakReference(current)
        return seen.get()?.takeIf { LobbyScene.isSceneLevel(it) }
    }

    //? if > 1.8.9 {
    fun center(level: ClientLevel): BlockPos? {
        val players = level.players()
        if (players.isNotEmpty()) {
            val x = players.sumOf { it.x } / players.size
            val y = players.sumOf { it.y } / players.size
            val z = players.sumOf { it.z } / players.size
            return BlockPos.containing(x, y, z)
        }
        val entities = level.entitiesForRendering().toList()
        if (entities.isEmpty()) return null
        return BlockPos.containing(
            entities.sumOf { it.x } / entities.size,
            entities.sumOf { it.y } / entities.size,
            entities.sumOf { it.z } / entities.size,
        )
    }
    //?} else {
    /*fun center(level: ClientLevel): BlockPos? {
        val players = level.players
        if (players.isNotEmpty()) {
            return BlockPos(players.sumOf { it.x } / players.size, players.sumOf { it.y } / players.size, players.sumOf { it.z } / players.size)
        }
        val entities = level.entities.toList()
        if (entities.isEmpty()) return null
        return BlockPos(entities.sumOf { it.x } / entities.size, entities.sumOf { it.y } / entities.size, entities.sumOf { it.z } / entities.size)
    }
    *///?}
}
