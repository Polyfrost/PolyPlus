@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.lobby

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import net.minecraft.client.Minecraft
//? if >= 1.21.10
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

object LobbyWardrobe {
    class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float)

    class Pose(val aim: Vec3, val yaw: Double, val pitch: Double, val distance: Double)

    private class Extent(val points: List<Pair<Double, Double>>, val top: Double, val bottom: Double) {
        fun span(turnDegrees: Float): Double {
            val radians = Math.toRadians(turnDegrees.toDouble())
            val across = points.maxOfOrNull { (x, z) -> abs(x * cos(radians) + z * sin(radians)) } ?: 0.0
            return max(BODY_SPAN, across * 2.0)
        }
    }

    private const val OPEN_SECONDS = 0.95
    private const val FIT_RATE = 5.0
    private const val FIT_MARGIN = 1.1
    private const val MIN_DISTANCE = 4.6
    private const val MAX_DISTANCE = 11.0
    private const val SWAY_YAW = 0.5
    private const val SWAY_PITCH = 0.25
    private const val HEAD_FOLLOW = 0.6f
    private const val HEAD_LIMIT = 40f
    private const val HEAD_SWAY = 5f
    private const val BODY_HEIGHT = 1.9
    private const val BODY_SPAN = 1.0
    private const val PIXELS_PER_BLOCK = 16.0
    private const val CAST_FADE_START = 0.12
    private const val CAST_FADE_SPAN = 0.4
    private const val ANIMATED_SWEEP = 0.7

    var open = false
    var turn = 0f
    var restTurn = 0f
    var frame = Frame(0.25f, 0.1f, 0.75f, 0.9f)
    var preview: PlayerPreviewSource.Override? = null

    private var progress = 0.0
    private var fitted = 0.0
    private var settled: Frame? = null
    private var measured: List<AttachedCosmetic>? = null
    private var extent = Extent(emptyList(), BODY_HEIGHT, 0.0)

    val amount: Double
        get() = progress * progress * progress * (progress * (progress * 6.0 - 15.0) + 10.0)

    val castFade: Double
        get() = ((amount - CAST_FADE_START) / CAST_FADE_SPAN).coerceIn(0.0, 1.0)

    fun advance(elapsed: Double) {
        val step = if (Motion.reduced) 1.0 else elapsed / OPEN_SECONDS
        progress = (progress + if (open) step else -step).coerceIn(0.0, 1.0)
    }

    fun reset() {
        progress = 0.0
        fitted = 0.0
        settled = null
        preview = null
        measured = null
    }

    fun pose(shot: LobbyShot, feet: Vec3, worn: List<AttachedCosmetic>, fov: Float, elapsed: Double, time: Double): Pose {
        measure(worn)
        val mc = Minecraft.getInstance()
        val blend = 1.0 - exp(-elapsed * FIT_RATE)
        val shown = settleFrame(blend.toFloat())
        val aspect = mc.window.width.toDouble() / mc.window.height.coerceAtLeast(1)
        val halfTan = tan(Math.toRadians(fov / 2.0))
        val height = extent.top - extent.bottom
        val freeWidth = (shown.right - shown.left).toDouble().coerceAtLeast(0.1)
        val freeHeight = (shown.bottom - shown.top).toDouble().coerceAtLeast(0.1)
        val needed = max(height / (2.0 * halfTan * freeHeight), extent.span(restTurn) / (2.0 * halfTan * aspect * freeWidth)) * FIT_MARGIN
        val wanted = needed.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        fitted = if (fitted == 0.0) wanted else fitted + (wanted - fitted) * blend

        val yaw = shot.yaw + sin(time * 0.17) * SWAY_YAW
        val pitch = shot.pitch + sin(time * 0.23 + 2.0) * SWAY_PITCH
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        val look = Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
        val right = Vec3(-cos(yawRad), 0.0, -sin(yawRad))
        val up = right.cross(look)
        val halfHeight = fitted * halfTan
        val centerX = (shown.left + shown.right - 1f).toDouble()
        val centerY = (1f - shown.top - shown.bottom).toDouble()
        val subject = feet.add(0.0, (extent.top + extent.bottom) / 2.0, 0.0)
        val aim = subject.subtract(right.scale(centerX * halfHeight * aspect)).subtract(up.scale(centerY * halfHeight))
        return Pose(aim, yaw, pitch, fitted)
    }

    private fun settleFrame(blend: Float): Frame {
        val from = settled ?: frame
        val next = Frame(
            from.left + (frame.left - from.left) * blend,
            from.top + (frame.top - from.top) * blend,
            from.right + (frame.right - from.right) * blend,
            from.bottom + (frame.bottom - from.bottom) * blend,
        )
        settled = next
        return next
    }

    fun poseAvatar(avatar: LobbyAvatar, shot: LobbyShot, time: Double) {
        val blend = amount.toFloat()
        if (blend <= 0f || avatar.arrival != LobbyAvatar.Arrival.LANDED) return
        val facing = shot.yaw + 180f + turn
        val yaw = avatar.rest.yaw + Mth.wrapDegrees(facing - avatar.rest.yaw) * blend
        val toCamera = (-Mth.wrapDegrees(turn) * HEAD_FOLLOW).coerceIn(-HEAD_LIMIT, HEAD_LIMIT)
        avatar.face(yaw, (toCamera + sin(time * 0.43).toFloat() * HEAD_SWAY) * blend)
    }

    fun previewCape(): Identifier? {
        val source = preview ?: return null
        return source.capeCosmeticId?.let(CosmeticAssetCache::getCapeResource) ?: source.mojangCape
    }

    //? if > 1.8.9 {
    fun withPreviewCape(skin: PlayerSkin): PlayerSkin {
        val cape = previewCape() ?: return skin
        //? if >= 1.21.10 {
        return PlayerSkin(skin.body(), ClientAsset.ResourceTexture(cape), skin.elytra(), skin.model(), skin.secure())
        //?} else {
        /*return PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure())
        *///?}
    }
    //?}

    private fun measure(worn: List<AttachedCosmetic>) {
        if (worn == measured) return
        measured = worn
        var top = BODY_HEIGHT
        var bottom = 0.0
        val points = ArrayList<Pair<Double, Double>>()
        for (cosmetic in worn) {
            val scale = cosmetic.scale / PIXELS_PER_BLOCK
            val reach = if (cosmetic.animation != null) ANIMATED_SWEEP else 1.0
            for (bone in cosmetic.model.geometry.bones.values) {
                for (cube in bone.cubes) {
                    for (x in listOf(cube.origin.x, cube.origin.x + cube.size.x)) {
                        for (z in listOf(cube.origin.z, cube.origin.z + cube.size.z)) {
                            points += x * scale * reach to z * scale * reach
                        }
                    }
                    top = max(top, (cube.origin.y + cube.size.y) * scale)
                    bottom = minOf(bottom, cube.origin.y * scale)
                }
            }
        }
        extent = Extent(points, top, bottom)
    }
}
