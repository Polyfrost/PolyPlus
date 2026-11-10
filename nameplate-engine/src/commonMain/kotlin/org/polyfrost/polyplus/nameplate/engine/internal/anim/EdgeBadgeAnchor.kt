package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeFollow
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor

internal class EdgeBadgeAnchor private constructor(
    val of: String,
    private val body: NameplateBundle.LayerContent.Body,
    private val routes: EdgeRoutes,
    private val follow: EdgeFollow,
    private val place: OrnamentAnchor,
    private val dx: Int,
    private val dy: Int,
) : BadgeStyling.Anchor {
    override fun x(block: TagBlock): Int = EdgeSpot.x(spot(block)) + dx

    override fun y(block: TagBlock): Int = EdgeSpot.y(spot(block)) + dy

    private fun spot(block: TagBlock): Long =
        routes.spot(follow, place, body.rect(block), BadgeStyling.CELLS_WIDE, BadgeStyling.CELLS_HIGH)

    companion object {
        fun of(badge: BadgeJson, bodies: Map<String, NameplateBundle.LayerContent.Body>): BadgeStyling.Anchor? {
            val follow = badge.follow?.let(EdgeFollow::named) ?: return null
            val of = badge.of ?: return null
            val body = bodies[of] ?: return null
            val place = OrnamentAnchor.named(badge.anchor.orEmpty(), badge.at) ?: return null
            return EdgeBadgeAnchor(of, body, EdgeRoutes(BodyOutline.of(body)), follow, place, badge.offset[0], badge.offset[1])
        }
    }
}
