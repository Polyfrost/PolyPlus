package org.polyfrost.polyplus.nameplate.engine.internal.palette

internal sealed interface ColorRef {
    data object Auto : ColorRef

    data object None : ColorRef

    data class Hex(val argb: Int) : ColorRef

    data class Slot(val id: String, val tone: Int) : ColorRef

    companion object {
        private val SLOT = Regex("""([a-z0-9_]{1,64})(?:\[(\d{1,2})\])?""")

        fun parse(text: String): ColorRef? {
            when (text) {
                "auto" -> return Auto
                "none" -> return None
            }

            if (text.startsWith('#')) return HexColor.parse(text)?.let(::Hex)

            val match = SLOT.matchEntire(text) ?: return null
            return Slot(match.groupValues[1], match.groupValues[2].ifEmpty { "0" }.toInt())
        }
    }
}
