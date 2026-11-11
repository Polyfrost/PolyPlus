package org.polyfrost.polyplus.nameplate.engine.api.shader

fun interface MaterialTextures {
    fun pixels(texture: TextureRef): TexturePixels?

    companion object {
        val NONE: MaterialTextures = MaterialTextures { null }
    }
}
