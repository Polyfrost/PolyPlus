package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.platform.v1.Platform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

//? if > 1.8.9 {
import org.joml.Quaternionf
import org.joml.Vector3f
import org.polyfrost.polyplus.client.lobby.LobbyCompat
//?} else {
/*import net.minecraft.entity.living.LivingEntity
import net.minecraft.util.math.Vec3d
import org.polyfrost.polyplus.mixin.client.ping.PingFovInvoker
import kotlin.math.tan
*///?}

internal class ScreenPoint(
    val x: Float,
    val y: Float,
    val onScreen: Boolean,
    val dirX: Float,
    val dirY: Float,
)

internal object PingProjection {
    private const val NEAR = 0.05f
    private const val BLEND = 0.2f

    //? if = 1.8.9 {
    /*private const val THIRD_PERSON_DISTANCE = 4.0
    private const val CLIP_JITTER = 0.1
    *///?}

    fun guiWidth(): Float = Platform.screen().guiWidth().toFloat()

    fun guiHeight(): Float = Platform.screen().guiHeight().toFloat()

    fun project(x: Double, y: Double, z: Double, partialTicks: Float): ScreenPoint? {
        val width = guiWidth()
        val height = guiHeight()
        if (width <= 0f || height <= 0f) return null
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val renderer = mc.gameRenderer
        val camera = LobbyCompat.camera(renderer)
        val origin = LobbyCompat.cameraPosition(renderer)
        val rotation = Quaternionf(camera.rotation())
        val inverse = Quaternionf(rotation).conjugate()
        val view = Vector3f((x - origin.x).toFloat(), (y - origin.y).toFloat(), (z - origin.z).toFloat()).rotate(inverse)
        val up = Vector3f(0f, 1f, 0f).rotate(inverse)
        val probe = Vector3f(1f, 1f, -1f).rotate(rotation)
        //? if >= 1.21.8 {
        val fov = 0f
        //?} else {
        /*val fov = mc.options.fov().get().toFloat()
        *///?}
        val scale = LobbyCompat.project(renderer, origin.add(probe.x.toDouble(), probe.y.toDouble(), probe.z.toDouble()), fov)
        val scaleX = scale.x.toFloat()
        val scaleY = scale.y.toFloat()
        if (!scaleX.isFinite() || !scaleY.isFinite() || scaleX <= 0f || scaleY <= 0f) return null
        return resolve(view.x, view.y, -view.z, up.y, -up.z, scaleX, scaleY, width, height)
        //?} else {
        /*val camera = mc.camera ?: return null
        val eye = camera.getEyePosition(partialTicks)
        var yaw = camera.lastYaw + (camera.yaw - camera.lastYaw) * partialTicks
        var pitch = camera.lastPitch + (camera.pitch - camera.lastPitch) * partialTicks
        val perspective = mc.options.perspective
        val sleeping = camera is LivingEntity && camera.isSleeping
        var cx = eye.x
        var cy = eye.y
        var cz = eye.z
        if (perspective > 0 && !sleeping) {
            val offsetPitch = if (perspective == 2) camera.pitch + 180f else camera.pitch
            val yawRad = Math.toRadians(camera.yaw.toDouble())
            val pitchRad = Math.toRadians(offsetPitch.toDouble())
            val ox = -sin(yawRad) * cos(pitchRad)
            val oz = cos(yawRad) * cos(pitchRad)
            val oy = -sin(pitchRad)
            val distance = thirdPersonDistance(eye, ox, oy, oz)
            cx -= ox * distance
            cy -= oy * distance
            cz -= oz * distance
            if (perspective == 2) {
                yaw += 180f
                pitch = -pitch
            }
        }
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val fx = -sin(yawRad) * cos(pitchRad)
        val fy = -sin(pitchRad)
        val fz = cos(yawRad) * cos(pitchRad)
        val rx = -cos(yawRad)
        val rz = -sin(yawRad)
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz
        val depth = dx * fx + dy * fy + dz * fz
        val right = dx * rx + dz * rz
        val upward = dx * ux + dy * uy + dz * uz
        val fovDeg = (mc.gameRenderer as PingFovInvoker).`polyplus$pingFov`(partialTicks, true)
        val half = tan(Math.toRadians(fovDeg / 2.0))
        val aspect = if (mc.height > 0) mc.width.toDouble() / mc.height else 1.0
        if (half <= 0.0 || !half.isFinite()) return null
        val scaleY = (1.0 / half).toFloat()
        val scaleX = (1.0 / (half * aspect)).toFloat()
        return resolve(right.toFloat(), upward.toFloat(), depth.toFloat(), uy.toFloat(), fy.toFloat(), scaleX, scaleY, width, height)
        *///?}
    }

    //? if = 1.8.9 {
    /*private fun thirdPersonDistance(eye: Vec3d, ox: Double, oy: Double, oz: Double): Double {
        val world = Minecraft.getInstance().world ?: return THIRD_PERSON_DISTANCE
        var distance = THIRD_PERSON_DISTANCE
        for (i in 0 until 8) {
            val jx = ((i and 1) * 2 - 1) * CLIP_JITTER
            val jy = ((i shr 1 and 1) * 2 - 1) * CLIP_JITTER
            val jz = ((i shr 2 and 1) * 2 - 1) * CLIP_JITTER
            val from = Vec3d(eye.x + jx, eye.y + jy, eye.z + jz)
            val to = Vec3d(eye.x - ox * THIRD_PERSON_DISTANCE + jx + jz, eye.y - oy * THIRD_PERSON_DISTANCE + jy, eye.z - oz * THIRD_PERSON_DISTANCE + jz)
            val hit = world.rayTrace(from, to) ?: continue
            val reach = hit.facePos.distanceTo(eye)
            if (reach < distance) distance = reach
        }
        return distance
    }
    *///?}

    private fun resolve(
        right: Float,
        upward: Float,
        depth: Float,
        worldUpY: Float,
        worldUpDepth: Float,
        scaleX: Float,
        scaleY: Float,
        width: Float,
        height: Float,
    ): ScreenPoint? {
        if (!right.isFinite() || !upward.isFinite() || !depth.isFinite()) return null
        val length = sqrt(right * right + upward * upward + depth * depth)
        if (length < 1e-4f) return ScreenPoint(width / 2f, height / 2f, true, 0f, 1f)

        if (depth > NEAR) {
            val nx = right * scaleX / depth
            val ny = upward * scaleY / depth
            if (nx in -1f..1f && ny in -1f..1f) {
                return ScreenPoint((nx + 1f) / 2f * width, (1f - ny) / 2f * height, true, 0f, 1f)
            }
        }

        val viewAngle = atan2(-upward * scaleY * height, right * scaleX * width)
        val flatDepth = worldUpY * depth - worldUpDepth * upward
        val flat = sqrt(right * right + flatDepth * flatDepth)
        if (flat < 1e-4f) return ScreenPoint(width / 2f, height / 2f, false, cos(viewAngle), sin(viewAngle))
        val turn = atan2(right, flatDepth)
        val turnAngle = atan2(-cos(turn), sin(turn))
        val t = smoothstep(BLEND, -BLEND, flatDepth / flat)
        val angle = lerpAngle(viewAngle, turnAngle, t)
        return ScreenPoint(width / 2f, height / 2f, false, cos(angle), sin(angle))
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var delta = (to - from) % TAU
        if (delta > PI_F) delta -= TAU
        if (delta < -PI_F) delta += TAU
        return from + delta * t
    }

    internal fun wrap(angle: Float): Float {
        var a = angle % TAU
        if (a > PI_F) a -= TAU
        if (a < -PI_F) a += TAU
        return a
    }

    private const val PI_F = Math.PI.toFloat()
    private const val TAU = (Math.PI * 2).toFloat()
}
