package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.text.LetterProgram
import org.polyfrost.polyplus.nameplate.engine.internal.text.TEXT_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.text.TextTrack

internal object TextTracks {
    const val MOTION = "motion"

    fun compile(animation: AnimationJson, params: Collection<String>): TextTrack? {
        val element = animation.tracks[TEXT_TARGET]?.get(MOTION) ?: return null
        val source = source(element) ?: return null
        if (source.isBlank()) return TextTrack(null)
        return LetterProgram.compile(source, params, "") {}?.let(::TextTrack)
    }

    fun source(element: JsonElement): String? = when {
        element is JsonNull -> ""
        element is JsonPrimitive && element.isString -> element.content
        else -> null
    }
}
