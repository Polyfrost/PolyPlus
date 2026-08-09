//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.assets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationController
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationControllerParser
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometryParser
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.cosmetics.PetArchetype
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import org.polyfrost.polyplus.client.utils.optionalFloat
import org.polyfrost.polyplus.client.utils.optionalString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import javax.imageio.ImageIO

internal object PetAssetParser {
    private val logger: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pets")

    fun peekArchetype(root: Path): PetArchetype? {
        val manifestAsset = DiskAssetReader.findFirst(root) { it == "pet.json" } ?: return null
        return try {
            manifestAsset.open().use { stream ->
                val manifest = JsonParser.parseReader(stream.reader()).asJsonObject
                PetArchetype.fromSerializedName(manifest.optionalString("archetype"))
            }
        } catch (ex: Exception) {
            logger.warn("Failed to read pet manifest archetype at {}", manifestAsset.relativePath, ex)
            null
        }
    }

    fun peekScale(root: Path): Float {
        val manifestAsset = DiskAssetReader.findFirst(root) { it == "pet.json" } ?: return 1f
        return try {
            manifestAsset.open().use { stream ->
                val manifest = JsonParser.parseReader(stream.reader()).asJsonObject
                manifest.optionalFloat("scale", 1f)
            }
        } catch (ex: Exception) {
            logger.warn("Failed to read pet manifest scale at {}", manifestAsset.relativePath, ex)
            1f
        }
    }

    fun peekAnchor(root: Path): PlayerModelBone? {
        val manifestAsset = DiskAssetReader.findFirst(root) { it == "pet.json" } ?: return null
        return try {
            manifestAsset.open().use { stream ->
                val manifest = JsonParser.parseReader(stream.reader()).asJsonObject
                PlayerModelBone.fromBedrockNameOrNull(manifest.optionalString("anchor"))
            }
        } catch (ex: Exception) {
            logger.warn("Failed to read pet manifest anchor at {}", manifestAsset.relativePath, ex)
            null
        }
    }

    fun parse(cosmeticId: Int, root: Path): PetDefinition? {
        val manifestAsset = DiskAssetReader.findFirst(root) { it == "pet.json" } ?: run {
            logger.warn("Pet cosmetic {} bundle is missing pet.json", cosmeticId)
            return null
        }

        val manifest = try {
            manifestAsset.open().use { JsonParser.parseReader(it.reader()).asJsonObject }
        } catch (ex: Exception) {
            logger.error("Failed to parse pet manifest for cosmetic {}", cosmeticId, ex)
            return null
        }

        val archetype = PetArchetype.fromSerializedName(manifest.optionalString("archetype"))
        if (archetype == null) {
            logger.warn("Pet cosmetic {} has a missing/invalid archetype", cosmeticId)
            return null
        }

        val geometryAsset = DiskAssetReader.findFirst(root) { path ->
            path.endsWith(".geo.json") && !path.endsWith("player.geo.json")
        } ?: run {
            logger.warn("Pet cosmetic {} bundle has no geometry (.geo.json)", cosmeticId)
            return null
        }

        val textureFile = findTexture(root) ?: run {
            logger.warn("Pet cosmetic {} bundle has no texture (.png)", cosmeticId)
            return null
        }

        return try {
            val geometry = geometryAsset.open().use(BedrockGeometryParser::parse)
            if (geometry.bones.values.none { it.cubes.isNotEmpty() }) {
                logger.warn("Pet cosmetic {} geometry {} has no cubes", cosmeticId, geometryAsset.relativePath)
                return null
            }

            val textureId = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$cosmeticId/texture")

            PetDefinition(
                id = cosmeticId,
                archetype = archetype,
                geometry = geometry,
                texture = RemoteTextures.register(textureId, textureFile),
                textureFrameCount = detectFrameCount(textureFile, geometry.description.textureHeight),
                animations = loadAnimations(root),
                controller = loadController(root, manifest.optionalString("controller")),
                stateMap = readStateMap(manifest),
                leashRadius = manifest.optionalFloat("leashRadius", 3f),
                moveSpeed = manifest.optionalFloat("moveSpeed", 0.3f),
                scale = manifest.optionalFloat("scale", 1f),
            )
        } catch (ex: Exception) {
            logger.error("Failed to load pet cosmetic {}", cosmeticId, ex)
            null
        }
    }

    private fun loadAnimations(root: Path): Map<String, BedrockAnimation> {
        val result = LinkedHashMap<String, BedrockAnimation>()
        val animationFiles = DiskAssetReader.walk(root) { path ->
            path.endsWith(".json") &&
                !path.endsWith(".geo.json") &&
                path != "pet.json" &&
                !path.endsWith(".animation_controllers.json")
        }

        for (asset in animationFiles) {
            try {
                asset.open().use { stream ->
                    result += BedrockAnimationParser.parseStream(stream).animations
                }
            } catch (ex: Exception) {
                logger.warn("Failed to parse pet animation file {}", asset.relativePath, ex)
            }
        }

        return result
    }

    private fun loadController(root: Path, controllerName: String): BedrockAnimationController? {
        if (controllerName.isBlank()) return null

        val controllerAsset = DiskAssetReader.findFirst(root) { it.endsWith(".animation_controllers.json") }
            ?: run {
                logger.warn("Pet manifest references controller {} but no *.animation_controllers.json was found", controllerName)
                return null
            }

        return try {
            controllerAsset.open().use { stream ->
                val controller = BedrockAnimationControllerParser.parseStream(stream).controllers[controllerName]
                if (controller == null) {
                    logger.warn("Controller {} not found in {}", controllerName, controllerAsset.relativePath)
                }
                controller
            }
        } catch (ex: Exception) {
            logger.warn("Failed to parse pet animation controller {}", controllerAsset.relativePath, ex)
            null
        }
    }

    private fun readStateMap(manifest: JsonObject): Map<String, String> {
        val statesObj = manifest.getAsJsonObject("states") ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for ((state, element) in statesObj.entrySet()) {
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                result[state] = element.asString
            }
        }
        return result
    }

    private fun findTexture(root: Path): Path? {
        val pngs = DiskAssetReader.walk(root) { it.endsWith(".png") }
        if (pngs.isEmpty()) return null
        val preferred = pngs.firstOrNull { it.relativePath.startsWith("textures/") }
        return (preferred ?: pngs.first()).file
    }

    private fun detectFrameCount(textureFile: Path, declaredHeight: Int): Int {
        if (declaredHeight <= 0) return 1
        val pixelHeight = runCatching { ImageIO.read(textureFile.toFile())?.height }.getOrNull() ?: return 1
        if (pixelHeight <= declaredHeight || pixelHeight % declaredHeight != 0) return 1
        return pixelHeight / declaredHeight
    }
}
//?}
