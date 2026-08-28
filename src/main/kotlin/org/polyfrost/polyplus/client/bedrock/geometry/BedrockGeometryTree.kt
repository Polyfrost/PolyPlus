package org.polyfrost.polyplus.client.bedrock.geometry

fun BedrockGeometry.childrenByParent(): Map<String, List<String>> =
    bones.values
        .groupBy { it.parent }
        .mapValues { (_, childBones) -> childBones.map { it.name } }

fun BedrockGeometry.renderableBoneNames(): Set<String> {
    val children = childrenByParent()
    val memo = mutableMapOf<String, Boolean>()

    fun hasRenderableContent(name: String): Boolean = memo.getOrPut(name) {
        val bone = bones[name] ?: return false
        bone.cubes.isNotEmpty() || (children[name] ?: emptyList()).any(::hasRenderableContent)
    }

    return bones.keys.filterTo(mutableSetOf()) { hasRenderableContent(it) }
}

fun BedrockGeometry.topLevelBoneName(): String? =
    bones.entries.firstOrNull { it.value.parent.isEmpty() }?.key ?: bones.keys.firstOrNull()

fun BedrockGeometry.resolvePlayerAttachBone(): PlayerModelBone? {
    for (bone in bones.values) {
        PlayerModelBone.fromBedrockNameOrNull(bone.parent)?.let { return it }
    }

    var current = topLevelBoneName()
    while (current != null) {
        val bone = bones[current] ?: break
        PlayerModelBone.fromBedrockNameOrNull(bone.parent)?.let { return it }
        current = bone.parent.takeIf { bones.containsKey(it) }
    }

    return null
}
