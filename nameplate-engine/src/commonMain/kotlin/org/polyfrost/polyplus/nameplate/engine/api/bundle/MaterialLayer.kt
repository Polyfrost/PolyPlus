package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialGlsl
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect

class MaterialLayer internal constructor(
    val material: Material,
    val of: String?,
    val shader: MaterialShader,
    val drift: Boolean = false,
    val mirror: Boolean = false,
    internal val mirrorDelay: Int = 0,
    internal val mirrorSeed: Boolean = false,
) : NameplateBundle.LayerContent {
    private val alternates = HashMap<GlslDialect, MaterialShader>()

    var fallback: MaterialFallback = MaterialFallback.NONE
        internal set

    fun shader(dialect: GlslDialect): MaterialShader =
        if (dialect == shader.dialect) shader else alternates.getOrPut(dialect) { MaterialGlsl.generate(material, dialect) }

    companion object {
        const val CAPABILITY = "materials"
    }
}

class MaterialFallback internal constructor(
    val frames: Int,
    internal val frameBytes: Long,
    val animatedBytes: Long,
    val still: Boolean,
    internal val rebuildMs: Double = 0.0,
    internal val shotStates: Int = 0,
) {
    val bytes: Long
        get() = if (still) frameBytes else animatedBytes

    companion object {
        val NONE: MaterialFallback = MaterialFallback(1, 0, 0, false)
    }
}

val NameplateBundle.materialLayers: List<Pair<NameplateBundle.Layer, MaterialLayer>>
    get() = layers.mapNotNull { layer -> (layer.content as? MaterialLayer)?.let { layer to it } }
