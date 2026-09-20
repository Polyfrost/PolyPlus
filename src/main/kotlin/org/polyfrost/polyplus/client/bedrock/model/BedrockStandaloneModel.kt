package org.polyfrost.polyplus.client.bedrock.model

import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.bedrockPivotOffset
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer

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
            val builder = BedrockBoneTreeBuilder(geometry, propagateLightLevels = false) { bone ->
                bedrockPivotOffset(bone.pivot, geometry.bones[bone.parent]?.pivot ?: Vector3f())
            }

            val rootNames = geometry.bones.values
                .filter { it.parent.isEmpty() || it.parent !in geometry.bones }
                .map { it.name }
                .ifEmpty { listOfNotNull(geometry.bones.keys.firstOrNull()) }

            val roots = rootNames.map { builder.buildBone(it) }
            return BedrockStandaloneModel(roots, builder.bones)
        }
    }
}
