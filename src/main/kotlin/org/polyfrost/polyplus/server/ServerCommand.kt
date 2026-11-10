package org.polyfrost.polyplus.server

sealed interface ServerCommand {
    data class SetOptions(val values: Map<String, OptionValue?>) : ServerCommand
    data object ResetOptions : ServerCommand
    data class Notify(val title: String, val description: String, val durationMs: Long, val icon: String?) : ServerCommand
    data object ResetNotifications : ServerCommand
}

sealed interface OptionValue {
    data class Bool(val value: Boolean) : OptionValue
    data class IntV(val value: Int) : OptionValue
    data class Num(val value: Double) : OptionValue
    data class Str(val value: String) : OptionValue

    fun asLookupKey(): String = when (this) {
        is Bool -> value.toString()
        is IntV -> value.toString()
        is Num -> if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
        is Str -> value
    }

    fun asDouble(): Double? = when (this) {
        is IntV -> value.toDouble()
        is Num -> value.takeIf { it.isFinite() }
        else -> null
    }

    fun asArgb(): Int? {
        if (this !is Str) return null
        val hex = value.removePrefix("#").removePrefix("0x")
        if (hex.isEmpty() || hex.length > 8) return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return if (hex.length <= 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    }
}
