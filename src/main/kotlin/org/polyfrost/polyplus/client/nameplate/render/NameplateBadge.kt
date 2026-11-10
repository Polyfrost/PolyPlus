package org.polyfrost.polyplus.client.nameplate.render

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedBadge
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
//? if > 1.8.9 {
import net.minecraft.network.chat.Component
import java.util.UUID
//?}

object NameplateBadge {
    val TEXTURE: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/nameplate/badge.png")
    val OUTLINE_TEXTURE: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/nameplate/badge_outline.png")

    private const val TEXTURE_SIZE = 32
    private const val TEXELS_PER_PIXEL = 4
    private const val OUTLINE_TEXELS = 3
    private const val OUTLINE_TEXTURE_SIZE = TEXTURE_SIZE + 2 * OUTLINE_TEXELS
    private const val EDGE = 0.01f

    private const val SIZE = 1f * TEXTURE_SIZE / TEXELS_PER_PIXEL
    private const val OUTLINE_INSET = 1f * OUTLINE_TEXELS / TEXELS_PER_PIXEL
    private const val OUTLINE_SIZE = 1f * OUTLINE_TEXTURE_SIZE / TEXELS_PER_PIXEL
    private const val UV0 = EDGE / TEXTURE_SIZE
    private const val UV1 = (TEXTURE_SIZE - EDGE) / TEXTURE_SIZE
    private const val OUTLINE_UV0 = EDGE / OUTLINE_TEXTURE_SIZE
    private const val OUTLINE_UV1 = (OUTLINE_TEXTURE_SIZE - EDGE) / OUTLINE_TEXTURE_SIZE

    private const val DECORATION_Z = 0f
    private const val BADGE_Z = 0.03f * Z_TOWARD_CAMERA
    private const val ART_TONE = BadgeStyling.ART_RGB and 0xFF
    private const val CHANNEL_MAX = 255

    //? if > 1.8.9 {
    @JvmStatic
    fun decorate(name: Component, uuid: UUID): Component? {
        if (!NameplateGate.active()) {
            return null
        }
        val nameplate = NameplateGate.bound(uuid) ?: return null
        if (!nameplate.variant.badge.placement.keepsGap) {
            return name
        }
        return Component.empty().append(NameplateText.badgeGap()).append(name)
    }
    //?}

    fun submit(frame: NameplateFrame, x: Float, y: Float, badge: ResolvedBadge, opacity: Int = CHANNEL_MAX) {
        val light = frame.lightCoords
        val alpha = if (opacity >= CHANNEL_MAX) frame.alpha else frame.alpha * opacity / CHANNEL_MAX
        val color = NameplateFrame.withAlpha(alpha, if (badge.tint == ResolvedText.UNSET) 0xFFFFFF else vertexRgb(badge.tint))
        if (badge.decoration == Contrast.Decoration.OUTLINE) {
            val outline = NameplateFrame.withAlpha(alpha, badge.decorationRgb)
            val x0 = x - OUTLINE_INSET
            val y0 = y - OUTLINE_INSET
            frame.submitGeometry(NameplateLayers.text(OUTLINE_TEXTURE)) { pose, buffer ->
                quad(buffer, pose, x0, y0, x0 + OUTLINE_SIZE, y0 + OUTLINE_SIZE, OUTLINE_UV0, OUTLINE_UV1, DECORATION_Z, outline, light)
            }
        }

        val under = NameplateFrame.withAlpha(alpha, vertexRgb(badge.decorationRgb))
        val shadow = badge.decoration == Contrast.Decoration.SHADOW
        frame.submitGeometry(NameplateLayers.text(TEXTURE)) { pose, buffer ->
            if (shadow) {
                quad(buffer, pose, x + 1, y + 1, x + 1 + SIZE, y + 1 + SIZE, UV0, UV1, DECORATION_Z, under, light)
            }
            quad(buffer, pose, x, y, x + SIZE, y + SIZE, UV0, UV1, BADGE_Z, color, light)
        }
    }

    private fun quad(
        buffer: NameplateVertexSink,
        pose: NameplatePose,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        uv0: Float,
        uv1: Float,
        z: Float,
        color: Int,
        light: Int,
    ) {
        val guarded = PortraitGuard.vertex(color)
        Vertices.add(buffer, pose, x0, y1, z).setUv(uv0, uv1).setLight(light).setColor(guarded)
        Vertices.add(buffer, pose, x1, y1, z).setUv(uv1, uv1).setLight(light).setColor(guarded)
        Vertices.add(buffer, pose, x1, y0, z).setUv(uv1, uv0).setLight(light).setColor(guarded)
        Vertices.add(buffer, pose, x0, y0, z).setUv(uv0, uv0).setLight(light).setColor(guarded)
    }

    private fun vertexRgb(rgb: Int): Int =
        (brighten((rgb shr 16) and CHANNEL_MAX) shl 16) or (brighten((rgb shr 8) and CHANNEL_MAX) shl 8) or brighten(rgb and CHANNEL_MAX)

    private fun brighten(channel: Int): Int = minOf(CHANNEL_MAX, (channel * CHANNEL_MAX + ART_TONE / 2) / ART_TONE)
}
