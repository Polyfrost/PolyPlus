package org.polyfrost.polyplus.nameplate.engine.internal.palette

internal object HexColor {
    private const val OPAQUE = 0xFF000000L
    private const val RGB_DIGITS = 6
    private const val ARGB_DIGITS = 8

    fun parse(text: String): Int? {
        if (!text.startsWith('#')) return null

        val digits = text.substring(1)
        if (digits.length != RGB_DIGITS && digits.length != ARGB_DIGITS) return null

        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

        val value = digits.toLong(16)
        return if (digits.length == RGB_DIGITS) (value or OPAQUE).toInt() else value.toInt()
    }
}
