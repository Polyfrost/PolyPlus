package org.polyfrost.polyplus.client.bedrock.model

import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockBone
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.childrenByParent
import org.polyfrost.polyplus.client.bedrock.geometry.initialEffectRotation
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockMesh

internal class BedrockBoneTreeBuilder(
    private val geometry: BedrockGeometry,
    private val propagateLightLevels: Boolean,
    private val initialPosition: (BedrockBone) -> Vector3f,
) {
    private val textureWidth = geometry.description.textureWidth
    private val textureHeight = geometry.description.textureHeight
    private val childrenByParent = geometry.childrenByParent()
    private val built = mutableMapOf<String, BedrockBoneRenderer>()
    private val visiting = mutableSetOf<String>()

    val bones: Map<String, BedrockBoneRenderer> get() = built

    fun buildBone(name: String, parentLevel: Int = -1): BedrockBoneRenderer {
        built[name]?.let { return it }

        check(visiting.size < MAX_DEPTH) { "Bone tree deeper than $MAX_DEPTH bones at $name" }

        val bone = geometry.bones[name] ?: error("Missing bone $name")
        val lightLevel = if (propagateLightLevels) bone.lightLevel.takeIf { it >= 0 } ?: parentLevel else -1
        check(visiting.add(name)) { "Cyclic bone parenting detected at $name" }
        val children = (childrenByParent[name] ?: emptyList())
            .filter { it != name && it !in visiting }
            .map { buildBone(it, lightLevel) }
        visiting.remove(name)

        return BedrockBoneRenderer(
            name = name,
            mesh = BedrockMesh.fromBone(bone, textureWidth, textureHeight, lightLevel),
            children = children,
            initialPosition = initialPosition(bone),
            initialRotation = bone.initialEffectRotation(),
        ).also { built[name] = it }
    }

    private companion object {
        const val MAX_DEPTH = 256
    }
}
