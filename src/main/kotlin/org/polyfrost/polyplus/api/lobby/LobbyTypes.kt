package org.polyfrost.polyplus.api.lobby

import net.minecraft.client.multiplayer.ClientLevel
import org.jetbrains.annotations.ApiStatus
import java.util.OptionalDouble
import java.util.UUID

@ApiStatus.Experimental
enum class LobbyPhase { IDLE, LOADING, READY, FAILED }

@ApiStatus.Experimental
enum class LobbyMode { STANDALONE, EMBEDDED }

@ApiStatus.Experimental
enum class AvatarRole { SELF, PARTY, BACKGROUND }

@ApiStatus.Experimental
@JvmRecord
data class Avatar(val id: UUID, val name: String, val role: AvatarRole)

@ApiStatus.Experimental
@JvmRecord
data class LobbyState(
    val apiVersion: Int,
    val sceneId: String,
    val reveal: String,
    val elapsedSeconds: Double,
    val clock: Long,
    val self: Avatar,
    val others: List<Avatar>,
)

@ApiStatus.Experimental
interface LobbyListener {
    fun onSceneBuilt(level: ClientLevel) = Unit

    fun onSwapIn(level: ClientLevel) = Unit

    fun onSwapOut(level: ClientLevel) = Unit

    fun onSceneEnded() = Unit
}

@ApiStatus.Experimental
interface LobbyInputs {
    fun elapsedSeconds(): Double

    fun clock(): Long

    fun mouseX(): Float

    fun mouseY(): Float

    fun aspect(): Float

    fun others(): List<Avatar>

    fun reveal(): String
}

@ApiStatus.Experimental
interface LobbyHandle {
    fun level(): ClientLevel?

    fun phase(): LobbyPhase

    fun onBuilt(callback: Runnable)

    fun onEnded(callback: Runnable)

    fun setVisible(visible: Boolean)

    fun fov(baseFov: Float): OptionalDouble

    fun bind(): AutoCloseable

    fun stop()
}
