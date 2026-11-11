package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 26.3 || >= 1.21.8 && < 26.2 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.ShaderSource
import com.mojang.renderpearl.api.pipeline.ShaderType
//?} else {
/*import com.mojang.blaze3d.shaders.ShaderType
*///?}
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import java.util.concurrent.ConcurrentHashMap

object NameplateShaderSources {
    private val stages = ConcurrentHashMap<Stage, String>()
    //? if >= 26.3 {
    private val includes = ConcurrentHashMap<Identifier, Include>()
    //?}

    fun add(program: NameplateShaderProgram) {
        stages[Stage(program.vertexShader, ShaderType.VERTEX)] = program.vertexSource
        stages[Stage(program.fragmentShader, ShaderType.FRAGMENT)] = program.fragmentSource
        //? if >= 26.3 {
        for ((id, text) in program.includes) {
            includes.compute(id) { _, old -> if (old != null && old.text == text) old else Include(id, text) }
        }
        //?}
    }

    fun remove(program: NameplateShaderProgram) {
        stages.remove(Stage(program.fragmentShader, ShaderType.FRAGMENT))
    }

    @JvmStatic
    fun shader(id: Identifier, type: ShaderType): String? =
        if (id.namespace == PolyPlusConstants.ID) stages[Stage(id, type)] else null

    //? if >= 26.3 {
    @JvmStatic
    fun include(id: Identifier): ShaderSource.CachedIncludeSource? =
        if (id.namespace == PolyPlusConstants.ID) includes[id]?.cached else null
    //?}

    private data class Stage(val id: Identifier, val type: ShaderType)

    //? if >= 26.3 {
    private class Include(id: Identifier, val text: String) {
        val cached: ShaderSource.CachedIncludeSource = ShaderSource.CachedIncludeSource.create(id, text)
    }
    //?}
}

//? if >= 26.3 {
class NameplateShaderSource(private val vanilla: Map<Identifier, ShaderSource.CachedIncludeSource>) : ShaderSource {
    override fun getShader(id: Identifier, type: ShaderType): String? = NameplateShaderSources.shader(id, type)

    override fun getInclude(id: Identifier): ShaderSource.CachedIncludeSource? = NameplateShaderSources.include(id) ?: vanilla[id]

    override fun close() {
        vanilla.values.forEach { it.close() }
    }
}
//?}
//?}
