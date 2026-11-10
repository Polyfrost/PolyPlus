package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonElement
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink

internal object FrameKeys {
    const val UNSUPPORTED = "not supported yet"

    class Parsed(val steps: IntArray, val values: IntArray)

    fun parse(element: JsonElement, path: String, report: IssueSink): Parsed? =
        ChannelKeys.parse(ChannelKeys.FRAME, element, path, report)?.let { Parsed(it.steps, it.values) }
}
