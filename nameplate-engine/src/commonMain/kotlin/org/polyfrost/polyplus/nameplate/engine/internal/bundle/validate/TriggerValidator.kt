package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import kotlinx.serialization.json.JsonElement
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TriggerCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink

internal class TriggerValidator(private val loop: Int, private val report: IssueSink) {
    fun validate(path: String, animation: AnimationJson, element: JsonElement): Int? {
        val trigger = TriggerCompiler.parse(element, "$path.trigger", report) ?: return null
        animation.reduced?.let { reduced ->
            when {
                trigger !is Trigger.While -> report.issue(
                    "$path.reduced",
                    "only while reactions take reduced; one-shots never play when motion is reduced",
                    IssueCode.REDUCED_WHILE,
                )
                reduced != TriggerCompiler.REDUCED_AUTO && reduced != TriggerCompiler.REDUCED_DROP ->
                    report.issue(
                        "$path.reduced",
                        "must be \"${TriggerCompiler.REDUCED_AUTO}\" or \"${TriggerCompiler.REDUCED_DROP}\"",
                        IssueCode.REDUCED_CHOICE,
                    )
            }
        }
        if (trigger is Trigger.While) {
            if (animation.length != null) {
                report("$path.length", "a while reaction plays in the loop's frames; it takes no length")
            }
            if (animation.restart != null) {
                report("$path.restart", "only one-shots take restart")
            }
            return loop
        }

        val length = animation.length
        return when {
            length == null -> failed("$path.length", "a one-shot needs a length in frames")
            length !in 1..BundleLimits.MAX_LOOP -> failed("$path.length", "must be 1 to ${BundleLimits.MAX_LOOP} frames")
            else -> length
        }
    }

    private fun failed(path: String, message: String): Int? {
        report(path, message)
        return null
    }
}
