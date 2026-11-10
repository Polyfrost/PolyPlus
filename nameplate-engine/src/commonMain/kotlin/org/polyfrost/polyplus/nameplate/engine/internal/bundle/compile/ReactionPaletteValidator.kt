package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef

internal class ReactionPaletteValidator(
    private val manifest: ManifestJson,
    private val tones: Map<String, Int>?,
    private val report: IssueSink,
) {
    fun validate(path: String, name: String, animation: AnimationJson, length: Int, oneShot: Boolean) {
        val palette = animation.tracks.filterKeys(PaletteTracks::isPaletteTrack)
        if (palette.isEmpty()) return

        var molang = false
        for ((track, channels) in palette) {
            val keys = channels[KEYS] as? JsonArray ?: continue
            for ((i, key) in keys.withIndex()) {
                val color = (key as? JsonArray)?.getOrNull(1) ?: continue
                if (usesMolang(color)) {
                    report.issue(
                        "$path.tracks.$track.$KEYS[$i]",
                        "reaction palettes take #hex or slot colors; Molang colors only work in the loop",
                        IssueCode.PALETTE_MOLANG,
                    )
                    molang = true
                }
            }
        }
        val known = tones ?: return
        if (molang) return

        val loopPrefix = "${BundleFiles.MANIFEST}#animations.${AnimationCompiler.LOOP}.tracks."
        val ownPrefix = "${BundleFiles.MANIFEST}#animations.$name.tracks."
        val synthetic = manifest.copy(loop = length, animations = mapOf(AnimationCompiler.LOOP to AnimationJson(tracks = palette)))
        val own = report.rewritten(
            path = { at -> if (at.startsWith(loopPrefix)) ownPrefix + at.removePrefix(loopPrefix) else at },
            message = { text -> if (oneShot) text.replace(LOOP_WORDS, ONE_SHOT_WORDS) else text },
        )
        PaletteTracks.validate(synthetic, known, own)
    }

    private fun usesMolang(element: JsonElement): Boolean = when (element) {
        is JsonArray -> element.any(::usesMolang)
        is JsonPrimitive -> element.isString && !element.content.startsWith('#') && ColorRef.parse(element.content) == null
        else -> false
    }

    private companion object {
        const val KEYS = "keys"
        const val LOOP_WORDS = "the loop ("
        const val ONE_SHOT_WORDS = "the one-shot's length ("
    }
}
