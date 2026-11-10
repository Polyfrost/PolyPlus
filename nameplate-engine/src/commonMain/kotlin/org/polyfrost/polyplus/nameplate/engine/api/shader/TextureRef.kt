package org.polyfrost.polyplus.nameplate.engine.api.shader

sealed interface TextureRef {
    data class Bundle(val id: String) : TextureRef

    data class Vanilla(val name: String) : TextureRef

    data class Atlas(val sprite: String) : TextureRef
}
