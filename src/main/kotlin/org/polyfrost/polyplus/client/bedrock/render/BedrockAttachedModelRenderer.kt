package org.polyfrost.polyplus.client.bedrock.render

import net.minecraft.resources.Identifier

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?}

//? if < 1.21.11 && > 1.8.9 {
/*import net.minecraft.client.renderer.RenderType
*///?}

object BedrockAttachedModelRenderer {
    //? if > 1.8.9 {
    private const val MAX_CACHED = 1024
    private val cutoutTypes = HashMap<Identifier, RenderType>()
    private val translucentTypes = HashMap<Identifier, RenderType>()

    fun renderType(texture: Identifier, translucent: Boolean): RenderType {
        val cache = if (translucent) translucentTypes else cutoutTypes
        val cached = cache[texture]
        if (cached != null) return cached
        if (cache.size >= MAX_CACHED) cache.clear()
        val created = create(texture, translucent)
        cache[texture] = created
        return created
    }
    //?}

    /** Two-sided, as cosmetics are modelled. */
    //? if >= 26.1 {
    private fun create(texture: Identifier, translucent: Boolean): RenderType =
        if (translucent) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutout(texture)
    //?} elif >= 1.21.11 {
    /*private fun create(texture: Identifier, translucent: Boolean): RenderType =
        if (translucent) RenderTypes.entityTranslucent(texture) else RenderTypes.entityCutoutNoCull(texture)
    *///?} elif > 1.8.9 {
    /*private fun create(texture: Identifier, translucent: Boolean): RenderType =
        if (translucent) RenderType.entityTranslucent(texture) else RenderType.entityCutoutNoCull(texture)
    *///?}
}
