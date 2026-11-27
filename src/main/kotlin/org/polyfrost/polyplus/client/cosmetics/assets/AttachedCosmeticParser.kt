package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path

internal class ParsedAttachedCosmetic(
    val cosmetic: AttachedCosmetic,
    private val staged: List<RemoteTextures.Staged>,
) {
    val textures: List<Identifier> get() = staged.map { it.id }

    fun publish(): AttachedCosmetic {
        staged.forEach { it.publish() }
        return cosmetic
    }

    fun discard() {
        staged.forEach { it.close() }
        cosmetic.scene.close()
    }
}

internal object AttachedCosmeticParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

    fun parse(
        cosmeticId: Int,
        root: Path,
        slot: BodySlot,
        scale: Float = 1f,
        anchor: PlayerModelBone? = null,
        hash: String? = null,
        name: String = "cosmetics/$cosmeticId",
        variantKey: String? = null,
        revision: CosmeticRevision = CosmeticRevision.of(hash, null, variantKey),
        bundleFailure: () -> Unit = {},
    ): ParsedAttachedCosmetic? {
        val prefix = "$name/${revision.tag}"
        when (val worn = CosmeticBundles.parseWorn(cosmeticId, hash, root)) {
            is CosmeticBundles.Worn.Loaded -> return parseAsset(cosmeticId, name, prefix, root, worn.parsed, slot, scale, variantKey, bundleFailure)
            CosmeticBundles.Worn.Rejected -> {
                bundleFailure()
                return null
            }
            CosmeticBundles.Worn.Legacy -> Unit
        }
        val geometryAsset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".geo.json") && !path.endsWith("player.geo.json")
        } ?: run {
            logger.warn("Cosmetic {} bundle has no geometry (.geo.json)", cosmeticId)
            bundleFailure()
            return null
        }
        val textureFile = DiskAssetReader.findTexture(root, variantKey) ?: run {
            logger.warn("Cosmetic {} bundle has no texture (.png)", cosmeticId)
            return null
        }

        val scene = try {
            // plusrenderer attaches unparented Blockbench exports to the slot's body part and reads sheets off the PNG's size
            val (width, height) = DiskAssetReader.pngSize(textureFile) ?: (0 to 0)
            CosmeticScene(geometryAsset.readText(), slot, anchor?.serializedName, width, height, scale)
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            bundleFailure()
            return null
        } catch (ex: LinkageError) {
            logger.error("Cosmetic {} can't be shown because plusrenderer has no native library for this platform", cosmeticId, ex)
            bundleFailure()
            return null
        }

        return try {
            if (scene.isEmpty) {
                logger.warn("Cosmetic {} geometry {} has no cubes", cosmeticId, geometryAsset.relativePath)
                scene.close()
                bundleFailure()
                return null
            }
            findAnimation(root)?.let { animate(scene, it, cosmeticId) }

            val texture = RemoteTextures.stagePng(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$prefix/texture"), textureFile)
            ParsedAttachedCosmetic(
                AttachedCosmetic(
                    id = attachedId(name),
                    cosmeticId = cosmeticId,
                    slot = slot,
                    texture = texture.id,
                    scene = scene,
                    colorway = CosmeticAsset.NO_COLORWAY,
                ),
                listOf(texture),
            )
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            scene.close()
            null
        }
    }

    private fun parseAsset(
        cosmeticId: Int,
        name: String,
        revision: String,
        root: Path,
        parsed: CosmeticBundles.Parsed,
        slot: BodySlot,
        scale: Float,
        variantKey: String?,
        bundleFailure: () -> Unit,
    ): ParsedAttachedCosmetic? {
        val asset = parsed.asset
        val scene = try {
            CosmeticScene(parsed, slot, scale)
        } catch (ex: RuntimeException) {
            logger.error("Failed to load cosmetic {}", cosmeticId, ex)
            asset.close()
            bundleFailure()
            return null
        } catch (ex: LinkageError) {
            logger.error("Cosmetic {} can't be shown because plusrenderer has no native library for this platform", cosmeticId, ex)
            asset.close()
            bundleFailure()
            return null
        }
        val staged = ArrayList<RemoteTextures.Staged>()
        return try {
            if (scene.isEmpty && !scene.hasEffects) {
                logger.warn("Cosmetic {} geometry has no cubes", cosmeticId)
                scene.close()
                bundleFailure()
                return null
            }
            if (asset.textureCount() == 0 && !scene.isEmpty) {
                logger.warn("Cosmetic {} has no texture", cosmeticId)
                scene.close()
                bundleFailure()
                return null
            }
            val info = intArrayOf(1, 1, 0)
            val rgba = if (asset.textureCount() == 0) {
                ByteArray(4)
            } else {
                ByteArray(asset.textureInfo(0, info)).also {
                    asset.resolveTexture(0, CosmeticAsset.NO_COLORWAY, null, null, null, it)
                }
            }
            val texture = RemoteTextures.stagePixels(
                Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$revision/texture"),
                info[CosmeticAsset.TEXTURE_WIDTH],
                info[CosmeticAsset.TEXTURE_HEIGHT],
                rgba,
            )
            staged += texture
            for (index in 0 until asset.effectTextureCount()) {
                val effectInfo = IntArray(CosmeticAsset.TEXTURE_INFO_INTS)
                val pixels = ByteArray(asset.effectTextureInfo(index, effectInfo))
                asset.effectTexture(index, pixels)
                val width = effectInfo[CosmeticAsset.TEXTURE_WIDTH].coerceAtLeast(1)
                val height = effectInfo[CosmeticAsset.TEXTURE_HEIGHT].coerceAtLeast(1)
                val filled = if (pixels.size == width * height * 4) pixels else ByteArray(width * height * 4)
                staged += RemoteTextures.stagePixels(
                    Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$revision/effect_$index"),
                    width,
                    height,
                    filled,
                )
            }
            scene.attachEffectTextures(staged.drop(1).map { it.id })
            ParsedAttachedCosmetic(
                AttachedCosmetic(
                    id = attachedId(name),
                    cosmeticId = cosmeticId,
                    slot = slot,
                    texture = texture.id,
                    scene = scene,
                    colorway = scene.colorwayIndex(CosmeticPalettes.designedColorway(cosmeticId, root, variantKey)),
                ),
                staged,
            )
        } catch (ex: Exception) {
            logger.error("Failed to load cosmetic {}", cosmeticId, ex)
            staged.forEach(RemoteTextures.Staged::close)
            scene.close()
            null
        }
    }

    fun attachedCosmeticId(cosmeticId: Int): Identifier = attachedId("cosmetics/$cosmeticId")

    private fun attachedId(name: String): Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$name/attached")

    private fun animate(scene: CosmeticScene, asset: DiskAssetReader.Asset, cosmeticId: Int) {
        try {
            val json = asset.readText()
            // shortcut: plusrenderer doesn't expose the length, drop this parse once it does
            val seconds = runCatching { AnimationLength.seconds(json) }.getOrNull() ?: 0f
            scene.animate(json, seconds)
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
