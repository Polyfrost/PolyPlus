package org.polyfrost.polyplus.client.nameplate.render

import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.nameplate.engine.api.anim.badgePose
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import kotlin.math.roundToInt

object NameplateDecorations {
    const val NO_BADGE: Float = Float.NaN

    fun submit(
        frame: NameplateFrame,
        nameplate: BoundNameplate,
        index: Int,
        state: TagState,
        detailed: Boolean,
        badge: Boolean,
        gapX: Float,
    ) {
        try {
            val ops = NameplatePaint.paint(nameplate, frame.block, index, state, detailed)
            val looped = NameplatePaint.palette(nameplate, frame.block, index)
            val palette = NameplatePaint.reacted(nameplate, looped, index, state)
            NameplateCanvas.submit(frame, nameplate.bundle, ops, palette) { error -> NameplateGate.fail(nameplate, error) }
            if (badge) {
                submitBadge(frame, nameplate, gapX, badgePose(nameplate.bundle, index, nameplate.seed, state), index, state)
            }
        } catch (e: RuntimeException) {
            NameplateGate.fail(nameplate, e)
        }
    }

    private fun submitBadge(frame: NameplateFrame, nameplate: BoundNameplate, gapX: Float, pose: LayerPose, index: Int, state: TagState) {
        if (!pose.visible || pose.opacity <= 0) {
            return
        }
        val block = frame.block
        val placement = nameplate.variant.badge.placement
        val x: Float
        val y: Int
        if (placement.kind == BadgeStyling.Placement.Kind.PLACED) {
            val anchor = placement.anchor ?: return
            x = anchor.x(block).toFloat()
            y = anchor.y(block)
        } else if (block.hasBadge) {
            x = block.badgeX.toFloat()
            y = block.badgeY
        } else {
            if (gapX.isNaN() || gapX < 0) {
                return
            }
            x = gapX
            y = block.nameLineY
        }
        val follow = NameplatePaint.badgeFollow(nameplate, block, index, state)
        val placedX = x + pose.dx + follow.dx
        val placedY = y + pose.dy + follow.dy
        NameplateBadge.submit(frame, placedX, placedY.toFloat(), NameplatePaint.badge(nameplate, block, placedX.roundToInt(), placedY), pose.opacity)
    }
}
