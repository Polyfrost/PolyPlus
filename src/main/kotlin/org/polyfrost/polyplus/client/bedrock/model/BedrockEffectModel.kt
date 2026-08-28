package org.polyfrost.polyplus.client.bedrock.model

import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.bedrock.geometry.renderableBoneNames
import org.polyfrost.polyplus.client.bedrock.geometry.topLevelBoneName
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer

class BedrockEffectModel private constructor(
    val geometry: BedrockGeometry,
    val attachments: List<EffectAttachment>,
    private val bonesByName: Map<String, BedrockBoneRenderer>,
) {
    data class EffectAttachment(
        val attachBone: PlayerModelBone,
        val rootBoneName: String,
        val rootBone: BedrockBoneRenderer,
    )

    fun resetPose() {
        bonesByName.values.forEach(BedrockBoneRenderer::resetPose)
    }

    fun applyPose(sample: Map<String, BoneTransform>, weight: Float) {
        BedrockBoneRenderer.applyPose(bonesByName, geometry.bones.keys, sample, weight)
    }

    companion object {
        fun build(geometry: BedrockGeometry, playerGeometry: BedrockGeometry): BedrockEffectModel {
            val boneTree = BedrockEffectBoneTreeBuilder(geometry, playerGeometry)
            val attachments = buildEffectAttachments(geometry, boneTree::buildBone)
            return BedrockEffectModel(geometry, attachments, boneTree.bones)
        }

        private fun buildEffectAttachments(
            geometry: BedrockGeometry,
            buildBone: (String) -> BedrockBoneRenderer,
        ): List<EffectAttachment> {
            val renderableBones = geometry.renderableBoneNames()

            val attachments = geometry.bones.mapNotNull { (boneName, bone) ->
                val attachBone = PlayerModelBone.fromBedrockNameOrNull(bone.parent) ?: return@mapNotNull null
                if (boneName !in renderableBones) return@mapNotNull null

                EffectAttachment(attachBone, boneName, buildBone(boneName))
            }

            if (attachments.isNotEmpty())
                return attachments

            val fallbackRoot = geometry.topLevelBoneName() ?: return emptyList()

            return listOf(
                EffectAttachment(PlayerModelBone.ROOT, fallbackRoot, buildBone(fallbackRoot)),
            )
        }
    }
}
