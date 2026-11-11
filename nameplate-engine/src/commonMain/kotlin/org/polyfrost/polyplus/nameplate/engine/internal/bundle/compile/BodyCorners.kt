package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.CornerRadii
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues

internal object BodyCorners {
    const val MAX_CORNER = 32
    private const val COUNT = 4

    fun of(body: BodyLayerJson): CornerRadii =
        body.corners?.let { CornerRadii(it[0], it[1], it[2], it[3]) } ?: CornerRadii.uniform(body.radius)

    fun validate(path: String, body: BodyLayerJson, report: IssueSink) {
        val corners = body.corners ?: return
        if (corners.size != COUNT || corners.any { it !in 0..MAX_CORNER }) {
            report.issue(
                "$path#corners",
                "must be [top left, top right, bottom right, bottom left], each 0 to $MAX_CORNER",
                IssueCode.CORNERS,
                issueValues("limit" to MAX_CORNER),
            )
        }
        if (body.radius != 0) {
            report.issue("$path#radius", "set either radius (every corner) or corners (each corner), not both", IssueCode.CORNERS_BOTH)
        }
    }
}
