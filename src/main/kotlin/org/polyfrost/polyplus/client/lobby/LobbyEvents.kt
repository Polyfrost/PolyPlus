package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.polyplus.api.lobby.LobbyListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object LobbyEvents {
    private val listeners = CopyOnWriteArrayList<LobbyListener>()
    private val reported: MutableSet<Any> = ConcurrentHashMap.newKeySet()

    fun add(listener: LobbyListener) {
        listeners.addIfAbsent(listener)
    }

    fun remove(listener: LobbyListener) {
        listeners.remove(listener)
        reported.remove(listener)
    }

    fun built(level: ClientLevel) = each { it.onSceneBuilt(level) }

    fun swapIn(level: ClientLevel) = each { it.onSwapIn(level) }

    fun swapOut(level: ClientLevel) = each { it.onSwapOut(level) }

    fun ended() = each { it.onSceneEnded() }

    fun run(callback: Runnable) = guard(callback) { callback.run() }

    private inline fun each(action: (LobbyListener) -> Unit) {
        if (listeners.isEmpty()) return
        for (listener in listeners) guard(listener) { action(listener) }
    }

    private inline fun guard(owner: Any, action: () -> Unit) {
        runCatching(action).onFailure { if (reported.add(owner)) LOBBY_LOGGER.warn("Lobby listener {} failed", owner, it) }
    }
}
