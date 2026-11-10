package org.polyfrost.polyplus.nameplate.engine.internal.material

internal object NodeCatalog {
    val kinds: Map<String, NodeKind> = (
        InputNodes.kinds() + MathNodes.kinds() + VectorNodes.kinds() + PatternNodes.kinds() + ColorNodes.kinds() +
            StructureNodes.kinds() + listOf(
                TextureKind("BundleTexture", TextureKind.Source.BUNDLE),
                TextureKind("VanillaTexture", TextureKind.Source.VANILLA),
                TextureKind("AtlasSprite", TextureKind.Source.ATLAS),
                OutputKind,
            )
        ).associateBy { it.type }

    fun kind(type: String): NodeKind? = kinds[type]
}
