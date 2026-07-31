//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.model

import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.childrenByParent
import org.polyfrost.polyplus.client.bedrock.geometry.initialEffectPosition
import org.polyfrost.polyplus.client.bedrock.geometry.initialEffectRotation
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockMesh

internal class BedrockEffectBoneTreeBuilder(
    private val geometry: BedrockGeometry,
    private val playerGeometry: BedrockGeometry,
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
        val effectiveLevel = bone.lightLevel.takeIf { it >= 0 } ?: parentLevel
        check(visiting.add(name)) { "Cyclic bone parenting detected at $name" }
        val children = (childrenByParent[name] ?: emptyList())
            .filter { it != name && it !in visiting }
            .map { buildBone(it, effectiveLevel) }
        visiting.remove(name)
        val position = bone.initialEffectPosition(geometry.bones, playerGeometry)
        val rotation = bone.initialEffectRotation()

        return BedrockBoneRenderer(
            name = name,
            mesh = BedrockMesh.fromBone(bone, textureWidth, textureHeight, effectiveLevel),
            children = children,
            initialPosition = position,
            initialRotation = rotation,
        ).also { built[name] = it }
    }

    private companion object {
        const val MAX_DEPTH = 256
    }
}
//?}
