//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.geometry

import org.joml.Vector2f
import org.joml.Vector3f

data class BedrockGeometry(
    val description: BedrockGeometryDescription,
    val bones: Map<String, BedrockBone>,
) {
    val identifier: String get() = description.identifier
}

data class BedrockGeometryDescription(
    val identifier: String,
    val textureWidth: Int,
    val textureHeight: Int,
    val visibleBounds: BedrockVisibleBounds,
)

data class BedrockVisibleBounds(
    val width: Float,
    val height: Float,
    val offset: Vector3f,
)

data class BedrockBone(
    val name: String,
    val parent: String,
    val pivot: Vector3f,
    val rotation: Vector3f,
    val cubes: List<BedrockCube>,
)

data class BedrockCube(
    val origin: Vector3f,
    val size: Vector3f,
    val uv: BedrockCubeUv,
    val inflate: Float,
    val pivot: Vector3f? = null,
    val rotation: Vector3f = Vector3f(),
    /**
     * Horizontal texture flip (Bedrock `"mirror"`). Independent of a negative
     * [size]
     * Auto-detected for mirrored bone pairs in AttachedCosmeticParser.
     */
    val mirror: Boolean = false,
)

data class BedrockCubeUv(
    val box: List<Int>,
    val faces: Map<BedrockCubeFace, BedrockFaceUv>,
)

data class BedrockFaceUv(
    val uv: Vector2f,
    val size: Vector2f,
)

enum class BedrockCubeFace {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    UP,
    DOWN,
}
//?}
