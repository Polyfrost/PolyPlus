//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometryParser
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.conditions.EmoteRules
import org.polyfrost.polyplus.client.emotes.conditions.EmoteRulesParser
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffect
import org.polyfrost.polyplus.client.emotes.registry.EmotePack
import org.polyfrost.polyplus.client.emotes.registry.animationStem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

internal object EmoteAssetParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/emotes")

    fun parse(cosmeticId: Int, root: Path, playerGeometry: BedrockGeometry): List<Emote> {
        BedrockPlayerGeometryCache.tryCaptureFrom(root)

        val emotes = mutableListOf<Emote>()
        val animationFiles = DiskAssetReader.walk(root) { path ->
            path.endsWith(".json") &&
                !path.endsWith(".geo.json") &&
                !path.endsWith(".emote.json")
        }

        for (asset in animationFiles) {
            loadAnimationFile(cosmeticId, asset, root, playerGeometry, emotes)
        }

        return emotes
    }

    private fun loadAnimationFile(
        cosmeticId: Int,
        asset: DiskAssetReader.Asset,
        root: Path,
        playerGeometry: BedrockGeometry,
        target: MutableList<Emote>,
    ) {
        try {
            asset.open().use { stream ->
                val file = BedrockAnimationParser.parseStream(stream)
                val animationFileId = syntheticId(cosmeticId, asset.relativePath)
                val effects = loadPairedEffects(root, asset.relativePath, playerGeometry)
                val rulesByAnimation = loadEmoteRules(root, asset.relativePath)

                for ((animationName, animation) in file.animations) {
                    val emoteId = resolveEmoteId(cosmeticId, asset.relativePath, animationName)
                    val rules = rulesByAnimation[animationName] ?: EmoteRules.DEFAULT
                    target += Emote(emoteId, animation, playerGeometry, effects, rules)
                    logger.debug("Loaded emote {} from cosmetic {}", emoteId, cosmeticId)
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to load emote animation {} for cosmetic {}", asset.relativePath, cosmeticId, ex)
        }
    }

    private fun loadEmoteRules(root: Path, animationRelative: String): Map<String, EmoteRules> {
        val manifestRelative = animationRelative
            .removeSuffix(".animation.json")
            .removeSuffix(".json") + ".emote.json"
        val manifest = DiskAssetReader.findFirst(root) { it == manifestRelative || it.endsWith("/$manifestRelative") }
            ?: return emptyMap()

        return try {
            manifest.open().use(EmoteRulesParser::parseStream)
        } catch (ex: Exception) {
            logger.error("Failed to load emote rules {}", manifest.relativePath, ex)
            emptyMap()
        }
    }

    private fun loadPairedEffects(
        root: Path,
        animationRelative: String,
        playerGeometry: BedrockGeometry,
    ): List<EmoteEffect> {
        val pack = parsePackFromRelative(animationRelative) ?: return emptyList()
        val geometryRelative = "emotes/${pack.name}/${pack.name}.geo.json"
        val geometryAsset = DiskAssetReader.findFirst(root) {
            it == geometryRelative ||
                it.endsWith("/$geometryRelative") ||
                it.endsWith("${pack.name}.geo.json")
        } ?: run {
            logger.warn("Emote pack {} is missing geometry", pack.name)
            return emptyList()
        }

        return try {
            geometryAsset.open().use { stream ->
                val geometry = BedrockGeometryParser.parse(stream)
                if (geometry.bones.values.none { it.cubes.isNotEmpty() }) {
                    logger.warn("Effect geometry {} has no cubes", geometryAsset.relativePath)
                    return emptyList()
                }

                val textureFile = RemoteTextures.findTexture(root, pack.name) ?: run {
                    logger.warn("Emote pack {} is missing texture", pack.name)
                    return emptyList()
                }

                val textureId = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cosmetics/${pack.name}/texture",
                )
                listOf(
                    EmoteEffect(
                        id = geometry.identifier,
                        geometry = geometry,
                        texture = RemoteTextures.register(textureId, textureFile),
                        model = BedrockEffectModel.build(geometry, playerGeometry),
                    ),
                )
            }
        } catch (ex: Exception) {
            logger.error("Failed to load effect geometry for {}", pack.name, ex)
            emptyList()
        }
    }

    private fun parsePackFromRelative(relative: String): EmotePack? {
        val normalized = relative.removePrefix("emotes/")
        if (!normalized.contains('/')) {
            return null
        }
        val directory = normalized.substringBefore('/')
        val fileStem = normalized.substringAfterLast('/').animationStem()
        if (fileStem != directory) {
            return null
        }
        return EmotePack(directory)
    }

    private fun resolveEmoteId(cosmeticId: Int, animationRelative: String, animationName: String): Identifier {
        parsePackFromRelative(animationRelative)?.let { pack ->
            return cosmeticEmoteId(cosmeticId, pack.emoteIdFor(animationName).path.removePrefix("emotes/"))
        }

        val flatStem = animationRelative.removePrefix("emotes/").animationStem()
        val path = if (animationName == flatStem) flatStem else animationName
        return cosmeticEmoteId(cosmeticId, path)
    }

    private fun syntheticId(cosmeticId: Int, relative: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$cosmeticId/$relative")

    private fun cosmeticEmoteId(cosmeticId: Int, suffix: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$cosmeticId/emote/$suffix")
}
//?}
