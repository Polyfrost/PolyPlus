package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.polyplus.api.lobby.LobbyHandle
import org.polyfrost.polyplus.api.lobby.LobbyInputs
import org.polyfrost.polyplus.api.lobby.LobbyMode
import org.polyfrost.polyplus.api.lobby.LobbyPhase
import java.util.OptionalDouble

class LobbySession(val mode: LobbyMode, val inputs: LobbyInputs?) : LobbyHandle {
    @Volatile
    var visible = mode == LobbyMode.STANDALONE
        private set

    @Volatile
    var ended = false
        private set

    private var finalPhase = LobbyPhase.IDLE
    private var built = false
    private val builtCallbacks = ArrayList<Runnable>()
    private val endedCallbacks = ArrayList<Runnable>()

    val embedded: Boolean get() = mode == LobbyMode.EMBEDDED

    override fun level(): ClientLevel? = if (ended) null else LobbyScene.levelOf(this)

    override fun phase(): LobbyPhase = if (ended) finalPhase else LobbyScene.apiPhase()

    override fun onBuilt(callback: Runnable) {
        when {
            ended -> Unit
            built -> LobbyEvents.run(callback)
            else -> builtCallbacks += callback
        }
    }

    override fun onEnded(callback: Runnable) {
        if (ended) LobbyEvents.run(callback) else endedCallbacks += callback
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
    }

    override fun fov(baseFov: Float): OptionalDouble = LobbyScene.fovOf(this)

    override fun bind(): AutoCloseable = LobbyScene.bind(this)

    override fun stop() = LobbyScene.stopSession(this)

    fun markBuilt() {
        if (ended || built) return
        built = true
        val pending = builtCallbacks.toList()
        builtCallbacks.clear()
        pending.forEach(LobbyEvents::run)
    }

    fun finish(phase: LobbyPhase) {
        if (ended) return
        ended = true
        finalPhase = phase
        builtCallbacks.clear()
        val pending = endedCallbacks.toList()
        endedCallbacks.clear()
        pending.forEach(LobbyEvents::run)
    }
}
