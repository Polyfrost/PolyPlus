package org.polyfrost.polyplus.nameplate.engine.api.text

class TextPaint private constructor(val kind: Kind, val rgb: Int) {
    enum class Kind {
        NONE,
        FIXED,
        AUTO,
    }

    override fun equals(other: Any?): Boolean = other is TextPaint && other.kind == kind && other.rgb == rgb

    override fun hashCode(): Int = kind.hashCode() * 31 + rgb

    override fun toString(): String = if (kind == Kind.FIXED) "TextPaint(#${rgb.toString(16)})" else "TextPaint($kind)"

    companion object {
        val NONE: TextPaint = TextPaint(Kind.NONE, 0)
        internal val AUTO: TextPaint = TextPaint(Kind.AUTO, 0)

        fun of(rgb: Int): TextPaint = TextPaint(Kind.FIXED, rgb and 0xFFFFFF)
    }
}
