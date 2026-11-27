package org.polyfrost.polyplus.nameplate.engine.api.paint

enum class Detail {
    FULL,
    REDUCED,
    ;

    companion object {
        fun named(name: String): Detail? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
