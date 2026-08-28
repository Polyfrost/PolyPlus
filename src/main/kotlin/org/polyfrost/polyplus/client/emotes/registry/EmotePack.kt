package org.polyfrost.polyplus.client.emotes.registry

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.slf4j.Logger

internal data class EmotePack(val name: String) {
    fun emoteIdFor(animationName: String): Identifier {
        val path = when {
            animationName == name || animationName.startsWith("$name.") -> name
            else -> animationName
        }

        return emoteAsset("emotes/$path")
    }

    companion object {
        fun parse(animationRelative: String, logger: Logger): EmotePack? {
            val relative = animationRelative.removePrefix("emotes/")
            if (!relative.contains('/')) {
                return null
            }

            val directory = relative.substringBefore('/')
            val fileStem = relative.substringAfterLast('/').animationStem()

            if (fileStem != directory) {
                logger.warn(
                    "Emote pack file name must match folder (expected {}/{}.animation.json, got {})",
                    directory,
                    directory,
                    animationRelative,
                )
                return null
            }

            return EmotePack(directory)
        }
    }
}

internal fun String.animationStem(): String =
    removeSuffix(".json").removeSuffix(".animation")

private fun emoteAsset(path: String): Identifier =
    Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, path)
