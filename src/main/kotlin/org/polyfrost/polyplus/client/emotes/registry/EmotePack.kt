package org.polyfrost.polyplus.client.emotes.registry

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants

internal data class EmotePack(val name: String) {
    fun emoteIdFor(animationName: String): Identifier {
        val path = when {
            animationName == name || animationName.startsWith("$name.") -> name
            else -> animationName
        }

        return emoteAsset("emotes/$path")
    }
}

internal fun String.animationStem(): String =
    removeSuffix(".json").removeSuffix(".animation")

private fun emoteAsset(path: String): Identifier =
    Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, path)
