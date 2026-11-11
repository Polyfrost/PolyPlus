package org.polyfrost.polyplus.nameplate.engine.api.shader

class FlatUniform internal constructor(val name: String, val type: String, val offset: Int) {
    val ints: Boolean
        get() = type == INT

    companion object {
        const val VEC4 = "vec4"
        const val INT = "int"
    }
}
