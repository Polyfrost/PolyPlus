package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockBone
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockCube
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometryParser
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.geometry.renderableBoneNames
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.math.abs

internal object AttachedCosmeticParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

    fun parse(
        cosmeticId: Int,
        root: Path,
        slot: BodySlot,
        playerGeometry: BedrockGeometry,
        scale: Float = 1f,
        anchor: PlayerModelBone? = null,
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
                val textureFile = DiskAssetReader.findTexture(root) ?: run {
                    logger.warn("Cosmetic {} bundle has no texture (.png)", cosmeticId)
                    return null
                }

                // Blockbench exports rarely parent bones to a player bone so fall back to the
                // slot's natural body part and let uploads work unedited
                val attachedGeometry = ensureAttached(parsed, slot, anchor)
                val textureFrameCount = detectTextureFrameCount(attachedGeometry, textureFile)
                val geometry = applyAutoMirror(
                    if (textureFrameCount > 1) attachedGeometry else reconcileTextureSize(attachedGeometry, textureFile, cosmeticId),
                )
                if (textureFrameCount > 1) {
                    logger.debug(
                        "Cosmetic {} texture read as a {}-frame vertical sheet of {}px frames",
                        cosmeticId,
                        textureFrameCount,
                        attachedGeometry.description.textureHeight,
                    )
                }

                val textureId = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cosmetics/$cosmeticId/texture",
                )
                AttachedCosmetic(
                    id = attachedCosmeticId(cosmeticId),
                    slot = slot,
                    texture = RemoteTextures.register(textureId, textureFile),
                    model = BedrockEffectModel.build(geometry, playerGeometry),
                    animation = findAnimation(root, cosmeticId),
                    scale = scale,
                    textureFrameCount = textureFrameCount,
                )
            }
        } catch (ex: Exception) {
            logger.error("Failed to load attached cosmetic {}", cosmeticId, ex)
            null
        }
    }

    private fun applyAutoMirror(geometry: BedrockGeometry): BedrockGeometry {
        if (geometry.bones.values.any { bone -> bone.cubes.any { it.mirror } }) {
            return geometry
        }

        val bones = geometry.bones.values
        val rewritten = geometry.bones.mapValues inner@{ (_, bone) ->
            if (bone.pivot.x <= 0f || bone.cubes.none { it.uv.faces.isEmpty() }) return@inner bone
            val hasNegativeMirror = bones.any { other ->
                other.name != bone.name &&
                    other.pivot.x < 0f &&
                    abs(other.pivot.x + bone.pivot.x) < MIRROR_EPSILON &&
                    abs(other.pivot.y - bone.pivot.y) < MIRROR_EPSILON &&
                    abs(other.pivot.z - bone.pivot.z) < MIRROR_EPSILON
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
        val size = DiskAssetReader.pngSize(textureFile) ?: return geometry
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

    private fun detectTextureFrameCount(geometry: BedrockGeometry, textureFile: Path): Int {
        val (width, height) = DiskAssetReader.pngSize(textureFile) ?: return 1
        val description = geometry.description
        return detectVerticalTextureFrameCount(
            description.textureWidth,
            description.textureHeight,
            width,
            height,
            maxUvVExtent(geometry),
        )
    }

    private fun maxUvVExtent(geometry: BedrockGeometry): Float {
        var extent = 0f
        for (bone in geometry.bones.values) {
            for (cube in bone.cubes) {
                if (cube.uv.faces.isEmpty()) {
                    val box = cube.uv.box
                    if (box.size < 2) continue
                    extent = maxOf(extent, box[1] + abs(cube.size.z) + abs(cube.size.y))
                    continue
                }
                for (face in cube.uv.faces.values) {
                    if (face.size.x == 0f || face.size.y == 0f) continue
                    extent = maxOf(extent, face.uv.y + maxOf(face.size.y, 0f))
                }
            }
        }
        return extent
    }

    private fun defaultAttachBone(slot: BodySlot): PlayerModelBone? = when (slot) {
        BodySlot.Backpack, BodySlot.Wings -> PlayerModelBone.BODY
        BodySlot.Glasses, BodySlot.Hat -> PlayerModelBone.HEAD
        BodySlot.LeftHand -> PlayerModelBone.LEFT_ARM
        BodySlot.RightHand -> PlayerModelBone.RIGHT_ARM
        BodySlot.Aura -> PlayerModelBone.BODY
        BodySlot.Boots -> PlayerModelBone.BODY
        BodySlot.Shoulder -> PlayerModelBone.BODY
        BodySlot.Pet -> PlayerModelBone.BODY
        BodySlot.Cape, BodySlot.Unknown -> null
    }

    private fun ensureAttached(geometry: BedrockGeometry, slot: BodySlot, anchor: PlayerModelBone? = null): BedrockGeometry {
        if (geometry.bones.values.any { PlayerModelBone.fromBedrockNameOrNull(it.parent) != null }) {
            return geometry
        }

        val prepared = if (slot == BodySlot.Boots) splitStraddlingLegBones(geometry) else geometry

        val renderable = prepared.renderableBoneNames()
        val rewritten = prepared.bones.mapValues { (name, bone) ->
            if (name !in renderable || !isAttachmentRoot(prepared, bone)) {
                return@mapValues bone
            }
            val target = anchor ?: attachTargetFor(prepared, slot, bone) ?: return@mapValues bone
            bone.copy(parent = target.serializedName)
        }
        return prepared.copy(bones = rewritten)
    }

    private fun isAttachmentRoot(geometry: BedrockGeometry, bone: BedrockBone): Boolean {
        if (bone.cubes.isEmpty()) return false
        var parent = geometry.bones[bone.parent]
        while (parent != null) {
            if (parent.cubes.isNotEmpty()) return false
            parent = geometry.bones[parent.parent]
        }
        return true
    }

    private fun splitStraddlingLegBones(geometry: BedrockGeometry): BedrockGeometry {
        val hasChild = geometry.bones.values.mapTo(hashSetOf()) { it.parent }
        val renderable = geometry.renderableBoneNames()
        val result = LinkedHashMap<String, BedrockBone>(geometry.bones.size)
        for ((name, bone) in geometry.bones) {
            if (name !in renderable || name in hasChild || bone.cubes.isEmpty()) {
                result[name] = bone
                continue
            }
            val right = bone.cubes.filter { cubeCenterX(it) < 0f }
            val left = bone.cubes.filter { cubeCenterX(it) >= 0f }
            if (right.isEmpty() || left.isEmpty()) {
                result[name] = bone
                continue
            }
            result["${name}_r"] = bone.copy(name = "${name}_r", cubes = right)
            result["${name}_l"] = bone.copy(name = "${name}_l", cubes = left)
        }
        return geometry.copy(bones = result)
    }

    private fun cubeCenterX(cube: BedrockCube): Float = cube.origin.x + cube.size.x * 0.5f

    private fun attachTargetFor(geometry: BedrockGeometry, slot: BodySlot, bone: BedrockBone): PlayerModelBone? {
        if (slot == BodySlot.Boots) {
            legForBone(geometry, bone)?.let { return it }
        }
        if (slot == BodySlot.LeftHand || slot == BodySlot.RightHand || slot == BodySlot.Shoulder) {
            armForBone(geometry, bone)?.let { return it }
        }
        return defaultAttachBone(slot)
    }

    private fun legForBone(geometry: BedrockGeometry, bone: BedrockBone): PlayerModelBone? =
        sideForBone(geometry, bone, PlayerModelBone.RIGHT_LEG, PlayerModelBone.LEFT_LEG)

    private fun armForBone(geometry: BedrockGeometry, bone: BedrockBone): PlayerModelBone? =
        sideForBone(geometry, bone, PlayerModelBone.RIGHT_ARM, PlayerModelBone.LEFT_ARM)

    private fun sideForBone(
        geometry: BedrockGeometry,
        bone: BedrockBone,
        negativeSide: PlayerModelBone,
        positiveSide: PlayerModelBone,
    ): PlayerModelBone? {
        val cubes = subtreeCubes(geometry, bone)
        if (cubes.isEmpty()) return null
        val centerX = cubes.fold(0f) { acc, cube -> acc + cube.origin.x + cube.size.x * 0.5f } / cubes.size
        return when {
            centerX < 0f -> negativeSide
            centerX > 0f -> positiveSide
            else -> null
        }
    }

    private fun subtreeCubes(geometry: BedrockGeometry, root: BedrockBone): List<BedrockCube> {
        val cubes = ArrayList(root.cubes)
        val queue = ArrayDeque<String>().apply { add(root.name) }
        val seen = hashSetOf(root.name)
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            for (bone in geometry.bones.values) {
                if (bone.parent == parent && seen.add(bone.name)) {
                    cubes.addAll(bone.cubes)
                    queue.add(bone.name)
                }
            }
        }
        return cubes
    }

    private fun findAnimation(root: Path, cosmeticId: Int): BedrockAnimation? {
        val asset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".json") &&
                !path.endsWith(".geo.json") &&
                !path.endsWith(".emote.json") &&
                path != "pet.json" &&
                !path.endsWith(".animation_controllers.json")
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
}

private const val MIN_SHEET_FRAMES = 3

internal fun detectVerticalTextureFrameCount(
    declaredWidth: Int,
    declaredHeight: Int,
    actualWidth: Int,
    actualHeight: Int,
    maxUvV: Float,
    minFrames: Int = MIN_SHEET_FRAMES,
): Int {
    if (declaredWidth <= 0 || declaredHeight <= 0) return 1
    if (actualWidth != declaredWidth || actualHeight <= declaredHeight) return 1
    if (actualHeight % declaredHeight != 0) return 1
    if (maxUvV > declaredHeight) return 1
    val frames = actualHeight / declaredHeight
    return if (frames >= minFrames) frames else 1
}
