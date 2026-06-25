//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockBone
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometryParser
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.geometry.renderableBoneNames
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path

internal object AttachedCosmeticParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

    fun parse(
        cosmeticId: Int,
        root: Path,
        slot: BodySlot,
        playerGeometry: BedrockGeometry,
    ): AttachedCosmetic? {
        val geometryAsset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".geo.json") && !path.endsWith("player.geo.json")
        } ?: run {
            logger.warn("Cosmetic {} bundle has no geometry (.geo.json)", cosmeticId)
            return null
        }

        return try {
            geometryAsset.open().use { stream ->
                val parsed = BedrockGeometryParser.parse(stream)
                if (parsed.bones.values.none { it.cubes.isNotEmpty() }) {
                    logger.warn("Cosmetic {} geometry {} has no cubes", cosmeticId, geometryAsset.relativePath)
                    return null
                }
                val textureFile = findTexture(root) ?: run {
                    logger.warn("Cosmetic {} bundle has no texture (.png)", cosmeticId)
                    return null
                }

                // A model exported straight from Blockbench usually doesn't parent
                // its bones to a player bone, so it would have nothing to attach to.
                // Auto-attach it to the slot's natural body part (wings -> body,
                // hat -> head, ...) so uploads just work without hand-editing.
                val geometry = applyAutoMirror(
                    reconcileTextureSize(
                        ensureAttached(parsed, slot),
                        textureFile,
                        cosmeticId,
                    ),
                )

                val textureId = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cosmetics/$cosmeticId/texture",
                )
                AttachedCosmetic(
                    id = attachedCosmeticId(cosmeticId),
                    slot = slot,
                    geometry = geometry,
                    texture = RemoteTextures.register(textureId, textureFile),
                    model = BedrockEffectModel.build(geometry, playerGeometry),
                    animation = findAnimation(root, cosmeticId),
                )
            }
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            null
        }
    }

    private fun applyAutoMirror(geometry: BedrockGeometry): BedrockGeometry {
        val bones = geometry.bones.values
        val rewritten = geometry.bones.mapValues inner@{ (_, bone) ->
            if (bone.pivot.x <= 0f || bone.cubes.none { it.uv.faces.isEmpty() }) return@inner bone
            val hasNegativeMirror = bones.any { other ->
                other.name != bone.name &&
                    other.pivot.x < 0f &&
                    kotlin.math.abs(other.pivot.x + bone.pivot.x) < MIRROR_EPSILON &&
                    kotlin.math.abs(other.pivot.y - bone.pivot.y) < MIRROR_EPSILON &&
                    kotlin.math.abs(other.pivot.z - bone.pivot.z) < MIRROR_EPSILON
            }
            if (!hasNegativeMirror) return@inner bone
            bone.copy(cubes = bone.cubes.map { if (it.uv.faces.isEmpty()) it.copy(mirror = !it.mirror) else it })
        }
        return geometry.copy(bones = rewritten)
    }

    private const val MIRROR_EPSILON = 0.01f

    fun attachedCosmeticId(cosmeticId: Int): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$cosmeticId/attached")

    private fun reconcileTextureSize(
        geometry: BedrockGeometry,
        textureFile: Path,
        cosmeticId: Int,
    ): BedrockGeometry {
        val size = pngSize(textureFile) ?: return geometry
        val (width, height) = size
        val description = geometry.description
        if (width == description.textureWidth && height == description.textureHeight) {
            return geometry
        }
        logger.debug(
            "Cosmetic {} texture is {}x{} but geometry declares {}x{}; using the actual texture size for UVs",
            cosmeticId, width, height, description.textureWidth, description.textureHeight,
        )
        return geometry.copy(
            description = description.copy(textureWidth = width, textureHeight = height),
        )
    }

    private fun pngSize(file: Path): Pair<Int, Int>? = try {
        DataInputStream(Files.newInputStream(file)).use { input ->
            input.skipBytes(16)
            val width = input.readInt()
            val height = input.readInt()
            if (width > 0 && height > 0) width to height else null
        }
    } catch (ex: Exception) {
        logger.warn("Failed to read texture dimensions from {}", file, ex)
        null
    }

    private fun defaultAttachBone(slot: BodySlot): PlayerModelBone? = when (slot) {
        BodySlot.Backpack, BodySlot.Wings -> PlayerModelBone.BODY
        BodySlot.Glasses, BodySlot.Hat -> PlayerModelBone.HEAD
        BodySlot.LeftHand -> PlayerModelBone.LEFT_ARM
        BodySlot.RightHand -> PlayerModelBone.RIGHT_ARM
        BodySlot.Aura -> PlayerModelBone.BODY
        BodySlot.Boots -> PlayerModelBone.BODY
        BodySlot.Shoulder -> PlayerModelBone.BODY
        BodySlot.Cape, BodySlot.Unknown -> null
    }

    private fun ensureAttached(geometry: BedrockGeometry, slot: BodySlot): BedrockGeometry {
        if (geometry.bones.values.any { PlayerModelBone.fromBedrockNameOrNull(it.parent) != null }) {
            return geometry
        }

        val boneNames = geometry.bones.keys
        val renderable = geometry.renderableBoneNames()
        val rewritten = geometry.bones.mapValues { (name, bone) ->
            val isTopLevel = bone.parent.isEmpty() || bone.parent !in boneNames
            if (!isTopLevel || name !in renderable) {
                return@mapValues bone
            }
            val target = attachTargetFor(slot, bone) ?: return@mapValues bone
            bone.copy(parent = target.serializedName)
        }
        return geometry.copy(bones = rewritten)
    }

    private fun attachTargetFor(slot: BodySlot, bone: BedrockBone): PlayerModelBone? {
        if (slot == BodySlot.Boots) {
            legForBone(bone)?.let { return it }
        }
        if (slot == BodySlot.LeftHand || slot == BodySlot.RightHand) {
            armForBone(bone)?.let { return it }
        }
        return defaultAttachBone(slot)
    }

    private fun legForBone(bone: BedrockBone): PlayerModelBone? =
        sideForBone(bone, PlayerModelBone.RIGHT_LEG, PlayerModelBone.LEFT_LEG)

    private fun armForBone(bone: BedrockBone): PlayerModelBone? =
        sideForBone(bone, PlayerModelBone.RIGHT_ARM, PlayerModelBone.LEFT_ARM)

    private fun sideForBone(
        bone: BedrockBone,
        negativeSide: PlayerModelBone,
        positiveSide: PlayerModelBone,
    ): PlayerModelBone? {
        if (bone.cubes.isEmpty()) return null
        val centerX = bone.cubes.fold(0f) { acc, cube -> acc + cube.origin.x + cube.size.x * 0.5f } / bone.cubes.size
        return when {
            centerX < 0f -> negativeSide
            centerX > 0f -> positiveSide
            else -> null
        }
    }

    private fun findAnimation(root: Path, cosmeticId: Int): BedrockAnimation? {
        val asset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".json") &&
                !path.endsWith(".geo.json") &&
                !path.endsWith(".emote.json")
        } ?: return null

        return try {
            asset.open().use { stream ->
                BedrockAnimationParser.parseStream(stream).animations.values.firstOrNull()
            }
        } catch (ex: Exception) {
            logger.warn("Cosmetic {} animation {} failed to parse", cosmeticId, asset.relativePath, ex)
            null
        }
    }

    private fun findTexture(root: Path): Path? {
        val pngs = DiskAssetReader.walk(root) { it.endsWith(".png") }
        if (pngs.isEmpty()) return null
        val preferred = pngs.firstOrNull { it.relativePath.startsWith("textures/") }
        return (preferred ?: pngs.first()).file
    }
}
//?}
