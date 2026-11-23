package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

internal object AttachedCosmeticParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

    fun parse(
        cosmeticId: Int,
        root: Path,
        slot: BodySlot,
        scale: Float = 1f,
        anchor: PlayerModelBone? = null,
    ): AttachedCosmetic? {
        val geometryAsset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".geo.json") && !path.endsWith("player.geo.json")
        } ?: run {
            logger.warn("Cosmetic {} bundle has no geometry (.geo.json)", cosmeticId)
            return null
        }
        val textureFile = DiskAssetReader.findTexture(root) ?: run {
            logger.warn("Cosmetic {} bundle has no texture (.png)", cosmeticId)
            return null
        }

        val scene = try {
            // plusrenderer attaches unparented Blockbench exports to the slot's body part and reads sheets off the PNG's size
            val (width, height) = DiskAssetReader.pngSize(textureFile) ?: (0 to 0)
            CosmeticScene(geometryAsset.readText(), slot, anchor?.serializedName, width, height, scale)
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            return null
        } catch (ex: LinkageError) {
            logger.error("Cosmetic {} can't be shown because plusrenderer has no native library for this platform", cosmeticId, ex)
            return null
        }

        return try {
            if (scene.isEmpty) {
                logger.warn("Cosmetic {} geometry {} has no cubes", cosmeticId, geometryAsset.relativePath)
                scene.close()
                return null
            }
            findAnimation(root)?.let { animate(scene, it, cosmeticId) }

            val textureId = Identifier.fromNamespaceAndPath(
                PolyPlusConstants.ID,
                "cosmetics/$cosmeticId/texture",
            )
            AttachedCosmetic(
                id = attachedCosmeticId(cosmeticId),
                slot = slot,
                texture = RemoteTextures.register(textureId, textureFile),
                scene = scene,
            )
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            scene.close()
            null
        }
    }

    fun attachedCosmeticId(cosmeticId: Int): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$cosmeticId/attached")

    private fun animate(scene: CosmeticScene, asset: DiskAssetReader.Asset, cosmeticId: Int) {
        try {
            val json = asset.readText()
            // shortcut: plusrenderer doesn't expose the length, drop this parse once it does
            val ticks = runCatching {
                BedrockAnimationParser.parseStream(json.byteInputStream()).animations.values.firstOrNull()?.lengthTicks
            }.getOrNull() ?: 0f
            scene.animate(json, ticks / BedrockConstants.TICKS_PER_SECOND)
        } catch (ex: Exception) {
            logger.warn("Cosmetic {} animation {} failed to parse", cosmeticId, asset.relativePath, ex)
        }
    }

    private fun findAnimation(root: Path): DiskAssetReader.Asset? = DiskAssetReader.findFirst(root) { path ->
        path.endsWith(".json") &&
            !path.endsWith(".geo.json") &&
            !path.endsWith(".emote.json") &&
            path != "pet.json" &&
            !path.endsWith(".animation_controllers.json")
    }

    private fun DiskAssetReader.Asset.readText(): String = open().use { it.readBytes().toString(Charsets.UTF_8) }
}
