package org.polyfrost.polyplus.client.lobby

import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * The 3D lobby's look-around: dragging on an empty part of the lobby turns the camera around the scene, scrolling zooms
 * in and out, a double click goes back to the scene's own view. The camera eases toward where you put it.
 */
object LobbyLook {
    // a drag across the whole lobby turns this far
    private const val YAW_PER_WIDTH = 200.0
    private const val PITCH_PER_HEIGHT = 80.0
    private const val MIN_PITCH = -35.0
    private const val MAX_PITCH = 60.0
    private const val ZOOM_STEP = 1.12
    private const val MIN_ZOOM = 0.45
    private const val MAX_ZOOM = 4.0
    private const val RATE = 10.0
    private const val SETTLED = 0.01
    private const val LOWEST_VIEW = -20.0
    private const val HIGHEST_VIEW = 80.0
    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    private var targetYaw = 0.0
    private var targetPitch = 0.0
    private var targetZoom = 1.0
    private var held = false

    /** Degrees added to the scene's yaw and pitch, and how much farther (or closer) the camera sits. */
    var yaw = 0.0
        private set
    var pitch = 0.0
        private set
    var zoom = 1.0
        private set

    fun enabled(): Boolean = PolyPlusLobbyConfig.lobbyLookAround

    /** True while the camera is anywhere but the scene's own view. */
    fun active(): Boolean = abs(yaw) > SETTLED || abs(pitch) > SETTLED || abs(zoom - 1.0) > SETTLED

    /** True while the camera is still easing toward where it was put. */
    fun moving(): Boolean = abs(targetYaw - yaw) > SETTLED || abs(targetPitch - pitch) > SETTLED || abs(targetZoom - zoom) > SETTLED / 10

    /** A drag of this share of the lobby's width and height. */
    fun turn(widthShare: Double, heightShare: Double) {
        targetYaw += widthShare * YAW_PER_WIDTH
        targetPitch = (targetPitch + heightShare * PITCH_PER_HEIGHT).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    /** Scroll steps: up (positive) zooms in. */
    fun zoomBy(steps: Double) {
        targetZoom = (targetZoom * ZOOM_STEP.pow(-steps)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Goes back to the scene's home view: the angle saved for it, or the scene's own view. */
    fun reset(instant: Boolean = false) {
        val home = LobbySceneStore.view(LobbyScene.currentId) ?: LobbySceneView()
        // the short way round to the saved yaw
        targetYaw = yaw + wrapped(home.yaw - yaw)
        targetPitch = home.pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        targetZoom = home.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (!instant) return
        yaw = targetYaw
        pitch = targetPitch
        zoom = targetZoom
    }

    /** Where the camera is being put right now, to save as the scene's home view. */
    fun current(): LobbySceneView = LobbySceneView(wrapped(targetYaw), targetPitch, targetZoom)

    private fun wrapped(degrees: Double): Double = (degrees + HALF_TURN).mod(FULL_TURN) - HALF_TURN

    /** Eases toward the target; [elapsed] in seconds since the last frame. */
    fun settle(elapsed: Double) {
        if (!enabled() && !held) {
            held = true
            reset(instant = true)
        } else if (enabled()) {
            held = false
        }
        val share = 1.0 - exp(-RATE * elapsed)
        yaw += (targetYaw - yaw) * share
        pitch += (targetPitch - pitch) * share
        zoom += (targetZoom - zoom) * share
    }

    /** The scene's pitch plus ours, kept between looking a little up and looking well down. */
    fun pitched(base: Double): Double = (base + pitch).coerceIn(LOWEST_VIEW, HIGHEST_VIEW)
}
