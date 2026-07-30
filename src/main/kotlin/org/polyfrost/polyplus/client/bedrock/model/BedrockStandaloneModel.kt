//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.model

import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.bedrockPivotOffset
import org.polyfrost.polyplus.client.bedrock.geometry.bedrockRotationRadians
import org.polyfrost.polyplus.client.bedrock.geometry.childrenByParent
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockMesh
import org.joml.Vector3f

class BedrockStandaloneModel private constructor(
    val roots: List<BedrockBoneRenderer>,
    val bonesByName: Map<String, BedrockBoneRenderer>,
) {
    fun resetPose() = bonesByName.values.forEach(BedrockBoneRenderer::resetPose)

    fun applyPose(sample: Map<String, BoneTransform>, weight: Float) {
        BedrockBoneRenderer.applyPose(bonesByName, bonesByName.keys, sample, weight)
    }

    companion object {
        fun build(geometry: BedrockGeometry): BedrockStandaloneModel {
            val childrenByParent = geometry.childrenByParent()
            val built = mutableMapOf<String, BedrockBoneRenderer>()
            val textureWidth = geometry.description.textureWidth
            val textureHeight = geometry.description.textureHeight

            fun buildBone(name: String): BedrockBoneRenderer {
                built[name]?.let { return it }
                val bone = geometry.bones.getValue(name)
                val children = (childrenByParent[name] ?: emptyList())
                    .filter { it != name }
                    .map { buildBone(it) }
                val referencePivot = geometry.bones[bone.parent]?.pivot ?: Vector3f()

                return BedrockBoneRenderer(
                    name = name,
                    mesh = BedrockMesh.fromBone(bone, textureWidth, textureHeight),
                    children = children,
                    initialPosition = bedrockPivotOffset(bone.pivot, referencePivot),
                    initialRotation = bone.rotation.bedrockRotationRadians(),
                ).also { built[name] = it }
            }

            val rootNames = geometry.bones.values
                .filter { it.parent.isEmpty() || it.parent !in geometry.bones }
                .map { it.name }
                .ifEmpty { listOfNotNull(geometry.bones.keys.firstOrNull()) }

            val roots = rootNames.map(::buildBone)
            return BedrockStandaloneModel(roots, built)
        }
    }
}
//?}
