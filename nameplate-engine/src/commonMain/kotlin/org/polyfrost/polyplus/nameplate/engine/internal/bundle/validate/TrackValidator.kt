package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import kotlinx.serialization.json.JsonElement
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.ChannelKeys
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.issue.keyValues

internal class TrackValidator(
    private val layers: Map<String, LayerFileJson>,
    private val sprites: Map<String, SpriteFileJson>,
    private val report: IssueSink,
    private val environment: MolangEnvironment,
) {
    fun validate(path: String, channel: String, element: JsonElement, target: String, length: Int?) {
        val sprite = layers[target] as? SpriteLayerJson
        if (channel == ChannelKeys.FRAME && sprite == null) {
            report(path, "frame tracks need a sprite layer")
            return
        }

        val keys = ChannelKeys.parse(channel, element, path, report, environment) ?: return
        if (keys.steps.isEmpty()) {
            report(path, "needs at least one key")
            return
        }

        val frames = sprite?.let(::frameCount)
        for (i in keys.steps.indices) {
            checkStep("$path[$i]", keys.steps, i, length)
            if (channel == ChannelKeys.FRAME && frames != null && keys.molang[i] == null && keys.values[i] !in 0 until frames) {
                report("$path[$i]", "frame must be 0 to ${frames - 1}")
            }
        }
    }

    private fun checkStep(path: String, steps: IntArray, index: Int, length: Int?) {
        val step = steps[index]
        when {
            index == 0 && step != 0 -> report.issue(path, "the first key must be at step 0", IssueCode.KEY_START, keyValues(step, length))
            index > 0 && step <= steps[index - 1] ->
                report.issue(path, "keys must be in increasing step order", IssueCode.KEY_ORDER, keyValues(step, length))
            step < 0 || (length != null && step >= length) ->
                report.issue(path, "step must be inside the animation's length", IssueCode.KEY_RANGE, keyValues(step, length))
            step > BundleLimits.MAX_TRACK_STEP -> report(path, "step must be at most ${BundleLimits.MAX_TRACK_STEP}")
        }
    }

    private fun frameCount(layer: SpriteLayerJson): Int? = sprites[layer.sprite]?.let(BundleFiles::framesOf)?.size
}
