package org.polyfrost.polyplus.client.lobby

import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * The 3D lobby's look-around: dragging on an empty part of the lobby turns the camera around the scene, scrolling zooms
 * in and out (from right up close to a high overview of the whole area), a right drag pans the camera, a double click
 * goes back to the scene's own view. The camera eases toward where you put it.
 */
object LobbyLook {
    // a drag across the whole lobby turns this far
    private const val YAW_PER_WIDTH = 200.0
    private const val PITCH_PER_HEIGHT = 80.0
    private const val MIN_PITCH = -70.0
    private const val MAX_PITCH = 90.0
    private const val ZOOM_STEP = 1.15
    private const val MIN_ZOOM = 0.08
    private const val MAX_ZOOM = 40.0
    // a right drag across the whole lobby moves the camera about this many times its distance
    private const val PAN_PER_WIDTH = 1.4
    private const val MIN_PAN_DISTANCE = 2.0
    private const val MAX_PAN = 400.0
    private const val RATE = 10.0
    private const val SETTLED = 0.01
    private const val LOWEST_VIEW = -60.0
    private const val HIGHEST_VIEW = 89.0
    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    private var targetYaw = 0.0
    private var targetPitch = 0.0
    private var targetZoom = 1.0
    private var targetPanX = 0.0
    private var targetPanY = 0.0
    private var targetPanZ = 0.0
    private var held = false

    // the camera's last yaw, pitch and distance, so a pan moves along the screen
    private var viewYaw = 0.0
    private var viewPitch = 0.0
    private var viewDistance = 8.0

    /** Degrees added to the scene's yaw and pitch, and how much farther (or closer) the camera sits. */
    var yaw = 0.0
        private set
    var pitch = 0.0
        private set
    var zoom = 1.0
        private set

    /** Blocks the camera's focus point is moved by (a right drag). */
    var panX = 0.0
        private set
    var panY = 0.0
        private set
    var panZ = 0.0
        private set

    fun enabled(): Boolean = PolyPlusLobbyConfig.lobbyLookAround

    /** True while the camera is anywhere but the scene's own view. */
    fun active(): Boolean = abs(yaw) > SETTLED || abs(pitch) > SETTLED || abs(zoom - 1.0) > SETTLED || panned()

    /** True once the camera was moved off its focus point (a right drag). */
    fun panned(): Boolean = abs(panX) > SETTLED || abs(panY) > SETTLED || abs(panZ) > SETTLED

    /** True while the camera is still easing toward where it was put. */
    fun moving(): Boolean = abs(targetYaw - yaw) > SETTLED || abs(targetPitch - pitch) > SETTLED || abs(targetZoom - zoom) > SETTLED / 10 ||
        abs(targetPanX - panX) > SETTLED || abs(targetPanY - panY) > SETTLED || abs(targetPanZ - panZ) > SETTLED

    /** A drag of this share of the lobby's width and height. */
    fun turn(widthShare: Double, heightShare: Double) {
        targetYaw += widthShare * YAW_PER_WIDTH
        targetPitch = (targetPitch + heightShare * PITCH_PER_HEIGHT).coerceIn(MIN_PITCH, MAX_PITCH)
    }

    /** A right drag of this share of the lobby's width and height: the scene follows the mouse. */
    fun pan(widthShare: Double, heightShare: Double) {
        val reach = viewDistance.coerceAtLeast(MIN_PAN_DISTANCE) * PAN_PER_WIDTH
        val yawRad = Math.toRadians(viewYaw)
        val pitchRad = Math.toRadians(viewPitch)
        // the camera's right and up directions
        val rightX = -cos(yawRad)
        val rightZ = -sin(yawRad)
        val upX = -sin(yawRad) * sin(pitchRad)
        val upY = cos(pitchRad)
        val upZ = cos(yawRad) * sin(pitchRad)
        val sideways = -widthShare * reach
        val upwards = heightShare * reach
        targetPanX = (targetPanX + rightX * sideways + upX * upwards).coerceIn(-MAX_PAN, MAX_PAN)
        targetPanY = (targetPanY + upY * upwards).coerceIn(-MAX_PAN, MAX_PAN)
        targetPanZ = (targetPanZ + rightZ * sideways + upZ * upwards).coerceIn(-MAX_PAN, MAX_PAN)
    }

    /** The lobby camera's final angle and distance this frame. */
    fun viewed(yaw: Double, pitch: Double, distance: Double) {
        viewYaw = yaw
        viewPitch = pitch
        if (distance.isFinite()) viewDistance = distance
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
        targetPanX = home.panX
        targetPanY = home.panY
        targetPanZ = home.panZ
        if (!instant) return
        yaw = targetYaw
        pitch = targetPitch
        zoom = targetZoom
        panX = targetPanX
        panY = targetPanY
        panZ = targetPanZ
    }

    /** Where the camera is being put right now, to save as the scene's home view. */
    fun current(): LobbySceneView = LobbySceneView(wrapped(targetYaw), targetPitch, targetZoom, targetPanX, targetPanY, targetPanZ)

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
        // zoom eases in its own scale so far zooms don't rush
        zoom = exp(ln(zoom) + (ln(targetZoom) - ln(zoom)) * share)
        panX += (targetPanX - panX) * share
        panY += (targetPanY - panY) * share
        panZ += (targetPanZ - panZ) * share
    }

    /** The scene's pitch plus ours, kept between looking a little up and looking well down. */
    fun pitched(base: Double): Double = (base + pitch).coerceIn(LOWEST_VIEW, HIGHEST_VIEW)
}
