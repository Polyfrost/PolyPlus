package org.polyfrost.polyplus.nameplate.engine.internal.bundle.json

internal object BundleFiles {
    const val MANIFEST = "manifest.json"
    const val PALETTES = "palettes.json"
    const val FLAGS = "flags.json"

    private val SPRITE_ID = Regex("[a-z0-9_]{1,64}")

    fun layerPath(id: String): String = "layers/$id.json"

    fun spritePath(id: String): String = "sprites/$id.json"

    fun framesOf(sprite: SpriteFileJson): List<List<String>>? = when {
        sprite.rows != null && sprite.frames == null -> listOf(sprite.rows)
        sprite.frames != null && sprite.rows == null -> sprite.frames
        else -> null
    }

    fun frameCount(sprite: SpriteFileJson?): Int? = sprite?.let(::framesOf)?.size

    fun isSpriteId(id: String): Boolean = SPRITE_ID.matches(id)
}
