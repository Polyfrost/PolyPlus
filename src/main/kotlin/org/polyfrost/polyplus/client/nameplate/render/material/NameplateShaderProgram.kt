package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.1 {
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
//? if >= 1.21.8 && < 1.21.11 {
/*import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslGlobals
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslSamplers
*///?}
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialGlsl
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import org.polyfrost.polyplus.nameplate.engine.api.shader.FlatUniform

enum class NameplateShaderStatus {
    READY,
    PENDING,
    FAILED,
}

class NameplateShaderProgram private constructor(
    val hash: String,
    val fragmentShader: Identifier,
    val vertexSource: String,
    val fragmentSource: String,
    val includes: Map<Identifier, String>,
    val uniformBytes: Int,
    val samplers: List<String>,
    val uniforms: List<FlatUniform>,
    val blend: Material.Blend,
    val emissive: Boolean,
) {
    val vertexShader: Identifier
        get() = VERTEX

    companion object {
        const val UNIFORM_BLOCK: String = "NameplateMaterial"

        val VERTEX: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "nameplate/material")

        //? if >= 26.3 {
        val DIALECT: GlslDialect = GlslDialect.CORE_330
        //?} elif >= 1.21.8 {
        /*val DIALECT: GlslDialect = GlslDialect.CORE_330_UBO
        *///?} else {
        /*val DIALECT: GlslDialect = GlslDialect.LEGACY_FLAT
        *///?}

        private const val WORD_BYTES = 4
        private const val LANES = 4
        private const val HEX = 16
        private const val CHAR_DIGITS = 4
        private const val ENCODED_PREFIX = "u-"
        private val SAFE = Regex("[a-z0-9_]{1,64}")

        fun shader(layer: MaterialLayer): MaterialShader =
            //? if >= 26.3 {
            layer.shader
            //?} elif >= 1.21.11 {
            /*layer.shader(GlslDialect.CORE_330_UBO)
            *///?} elif >= 1.21.8 {
            /*MaterialGlsl.generate(layer.material, GlslDialect.CORE_330_UBO, GlslSamplers.NUMBERED, GlslGlobals.SCREEN_FIRST)
            *///?} else {
            /*layer.shader(GlslDialect.LEGACY_FLAT)
            *///?}

        fun of(hash: String, shader: MaterialShader, blend: Material.Blend, emissive: Boolean): NameplateShaderProgram {
            require(shader.dialect == DIALECT) { "Nameplate pipelines need the $DIALECT dialect, got ${shader.dialect}" }
            require(DIALECT != GlslDialect.CORE_330 || shader.include.isNotEmpty()) { "Nameplate shader $hash has no shared include" }
            val path = pathSafe(hash)
            return NameplateShaderProgram(
                hash = path,
                fragmentShader = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "nameplate/material_$path"),
                vertexSource = shader.vertex,
                fragmentSource = shader.fragment,
                includes = if (shader.include.isEmpty()) emptyMap() else mapOf(Identifier.parse(MaterialGlsl.INCLUDE) to shader.include),
                uniformBytes = (MaterialUniforms.TONES + shader.layout.tones * LANES) * WORD_BYTES,
                samplers = shader.samplers,
                uniforms = shader.uniforms,
                blend = blend,
                emissive = emissive,
            )
        }

        private fun pathSafe(hash: String): String {
            if (SAFE.matches(hash)) {
                return hash
            }
            val out = StringBuilder(ENCODED_PREFIX)
            for (c in hash) {
                out.append(c.code.toString(HEX).padStart(CHAR_DIGITS, '0'))
            }
            return out.toString()
        }
    }
}
//?}
