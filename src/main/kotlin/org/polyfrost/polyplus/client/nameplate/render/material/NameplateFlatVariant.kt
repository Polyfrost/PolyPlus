package org.polyfrost.polyplus.client.nameplate.render.material

//? if = 1.21.5 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.opengl.GlProgram
import com.mojang.blaze3d.opengl.GlRenderPipeline
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.util.OptionalDouble
import java.util.OptionalInt

internal class NameplateFlatVariant(private val program: NameplateShaderProgram, val depth: Boolean) {
    val pipeline: RenderPipeline = build()

    val label: String
        get() = pipeline.location.toString()

    fun compile(): Throwable? {
        val compiled = precompiled()
        return if (compiled.isValid) null else IllegalStateException("Pipeline $label did not compile or link; the game log above has the driver output")
    }

    fun close() = Unit

    fun glProgram(): GlProgram? = (precompiled() as? GlRenderPipeline)?.takeIf { it.isValid }?.program()

    fun renderType(name: String, textures: List<Identifier>): NameplateLayer = NameplateFlatMaterialRenderType(name, this, program, textures)

    private fun precompiled() = RenderSystem.getDevice().precompilePipeline(pipeline) { id, type -> source(id, type) }

    private fun source(id: Identifier, type: ShaderType): String? = when {
        type == ShaderType.VERTEX && id == program.vertexShader -> program.vertexSource
        type == ShaderType.FRAGMENT && id == program.fragmentShader -> program.fragmentSource
        else -> null
    }

    private fun build(): RenderPipeline {
        val location = "nameplate_material_${program.hash}${if (depth) DEPTH_SUFFIX else ""}"
        val builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/$location"))
            .withVertexShader(program.vertexShader)
            .withFragmentShader(program.fragmentShader)
            .withSampler(LIGHTMAP)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        for ((name, type) in BUILT_IN) {
            builder.withUniform(name, type)
        }
        for (uniform in program.uniforms) {
            builder.withUniform(uniform.name, if (uniform.ints) UniformType.INT else UniformType.VEC4)
        }
        for (sampler in program.samplers) {
            builder.withSampler(sampler)
        }
        if (depth) {
            builder.withShaderDefine(DEPTH_ONLY).withoutBlend().withColorWrite(false, false).withDepthWrite(true)
        } else {
            builder.withBlend(if (program.blend == Material.Blend.ADDITIVE) BlendFunction.LIGHTNING else BlendFunction.TRANSLUCENT).withDepthWrite(false)
        }
        return builder.build()
    }

    companion object {
        private const val DEPTH_ONLY = "NAMEPLATE_DEPTH_ONLY"
        private const val DEPTH_SUFFIX = "_depth"
        private const val LIGHTMAP = "Sampler2"
        private val BUILT_IN = listOf(
            "ModelViewMat" to UniformType.MATRIX4X4,
            "ProjMat" to UniformType.MATRIX4X4,
            "ColorModulator" to UniformType.VEC4,
            "FogStart" to UniformType.FLOAT,
            "FogEnd" to UniformType.FLOAT,
            "FogColor" to UniformType.VEC4,
            "FogShape" to UniformType.INT,
            "ScreenSize" to UniformType.VEC2,
        )

        fun driver(): Map<String, String> {
            val device = RenderSystem.getDevice()
            return mapOf("name" to device.renderer, "vendor" to device.vendor, "driver" to device.version, "backend" to device.backendName)
        }

        fun debugMessages(): List<String> = RenderSystem.getDevice().lastDebugMessages.toList()
    }
}

private class NameplateFlatBatchState(
    private val key: String,
    private val variant: NameplateFlatVariant,
    private val program: NameplateShaderProgram,
    private val textures: List<Identifier>,
) {
    private val units = IntArray(textures.size) { program.samplers[it].removePrefix(SAMPLER).toInt() }
    private val previous = arrayOfNulls<GpuTexture>(textures.size)

    fun setup() {
        val minecraft = Minecraft.getInstance()
        minecraft.gameRenderer.lightTexture().turnOnLightLayer()
        for ((i, texture) in textures.withIndex()) {
            previous[i] = RenderSystem.getShaderTexture(units[i])
            RenderSystem.setShaderTexture(units[i], minecraft.textureManager.getTexture(texture).texture)
        }
        val words = NameplateMaterialUniforms.words(key) ?: return
        val shader = variant.glProgram() ?: return
        for (uniform in program.uniforms) {
            val target = shader.safeGetUniform(uniform.name)
            val at = uniform.offset
            if (uniform.ints) {
                target.set(words[at])
            } else {
                target.set(float(words, at), float(words, at + 1), float(words, at + 2), float(words, at + 3))
            }
        }
    }

    fun clear() {
        for (i in units.indices) {
            RenderSystem.setShaderTexture(units[i], previous[i])
            previous[i] = null
        }
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer()
    }

    private companion object {
        const val SAMPLER = "Sampler"
    }
}

internal class NameplateFlatMaterialRenderType private constructor(
    private val key: String,
    private val materialPipeline: RenderPipeline,
    private val program: NameplateShaderProgram,
    private val textures: List<Identifier>,
    state: NameplateFlatBatchState,
) : RenderType(key, BUFFER_SIZE, false, false, { state.setup() }, { state.clear() }) {
    constructor(key: String, variant: NameplateFlatVariant, program: NameplateShaderProgram, textures: List<Identifier>) :
        this(key, variant.pipeline, program, textures, NameplateFlatBatchState(key, variant, program, textures))

    override fun format(): VertexFormat = materialPipeline.vertexFormat

    override fun mode(): VertexFormat.Mode = materialPipeline.vertexFormatMode

    override fun getRenderPipeline(): RenderPipeline = materialPipeline

    override fun getRenderTarget(): RenderTarget = Minecraft.getInstance().mainRenderTarget

    override fun draw(mesh: MeshData) {
        mesh.use { data ->
            val words = NameplateMaterialUniforms.words(key) ?: return
            val minecraft = Minecraft.getInstance()
            val format = materialPipeline.vertexFormat
            val vertices = format.uploadImmediateVertexBuffer(data.vertexBuffer())
            val sorted = data.indexBuffer()
            val indices: GpuBuffer
            val indexType: VertexFormat.IndexType
            if (sorted == null) {
                val sequential = RenderSystem.getSequentialBuffer(data.drawState().mode())
                indices = sequential.getBuffer(data.drawState().indexCount())
                indexType = sequential.type()
            } else {
                indices = format.uploadImmediateIndexBuffer(sorted)
                indexType = data.drawState().indexType()
            }
            val target = renderTarget
            val color = target.colorTexture ?: return
            val lightTexture = minecraft.gameRenderer.lightTexture()
            lightTexture.turnOnLightLayer()
            try {
                RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(color, OptionalInt.empty(), if (target.useDepth) target.depthTexture else null, OptionalDouble.empty())
                    .use { pass ->
                        pass.setPipeline(materialPipeline)
                        pass.setVertexBuffer(0, vertices)
                        if (RenderSystem.SCISSOR_STATE.isEnabled) {
                            pass.enableScissor(RenderSystem.SCISSOR_STATE)
                        }
                        RenderSystem.getShaderTexture(LIGHTMAP_UNIT)?.let { pass.bindSampler(LIGHTMAP, it) }
                        for ((i, texture) in textures.withIndex()) {
                            pass.bindSampler(program.samplers[i], minecraft.textureManager.getTexture(texture).texture)
                        }
                        for (uniform in program.uniforms) {
                            val at = uniform.offset
                            if (uniform.ints) {
                                pass.setUniform(uniform.name, words[at])
                            } else {
                                pass.setUniform(uniform.name, float(words, at), float(words, at + 1), float(words, at + 2), float(words, at + 3))
                            }
                        }
                        pass.setIndexBuffer(indices, indexType)
                        pass.drawIndexed(0, data.drawState().indexCount())
                    }
            } finally {
                lightTexture.turnOffLightLayer()
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1536
        const val LIGHTMAP = "Sampler2"
        const val LIGHTMAP_UNIT = 2
    }
}
*///?}
//? if = 1.21.4 {
/*import com.mojang.blaze3d.platform.GlDebug
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.GlUtil
import com.mojang.blaze3d.shaders.CompiledShader
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.CompiledShaderProgram
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderManager
import net.minecraft.client.renderer.ShaderProgramConfig
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.nameplate.engine.api.material.Material

internal class NameplateFlatVariant(private val program: NameplateShaderProgram, val depth: Boolean) {
    var compiled: CompiledShaderProgram? = null
        private set

    val label: String
        get() = flatLabel(program, depth)

    fun compile(): Throwable? {
        close()
        var vertex: CompiledShader? = null
        var fragment: CompiledShader? = null
        try {
            vertex = CompiledShader.compile(program.vertexShader, CompiledShader.Type.VERTEX, program.vertexSource)
            fragment = CompiledShader.compile(program.fragmentShader, CompiledShader.Type.FRAGMENT, flatFragmentSource(program, depth))
            val linked = CompiledShaderProgram.link(vertex, fragment, FORMAT)
            linked.setupUniforms(uniforms(), program.samplers.plus(LIGHTMAP).map { ShaderProgramConfig.Sampler(it) })
            compiled = linked
            return null
        } catch (e: ShaderManager.CompilationException) {
            return e
        } finally {
            vertex?.close()
            fragment?.close()
        }
    }

    fun close() {
        compiled?.close()
        compiled = null
    }

    fun renderType(name: String, textures: List<Identifier>): NameplateLayer = NameplateFlatMaterialRenderType(name, this, program, textures)

    private fun uniforms(): List<ShaderProgramConfig.Uniform> = BUILT_IN.map { (name, type, count) ->
        ShaderProgramConfig.Uniform(name, type, count, ZERO)
    } + program.uniforms.map { uniform ->
        if (uniform.ints) ShaderProgramConfig.Uniform(uniform.name, INT, 1, ZERO) else ShaderProgramConfig.Uniform(uniform.name, FLOAT, VEC4, ZERO)
    }

    companion object {
        val FORMAT: VertexFormat = DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR

        private val ZERO = listOf(0f)

        fun driver(): Map<String, String> =
            mapOf("name" to GlUtil.getRenderer(), "vendor" to GlUtil.getVendor(), "driver" to GlUtil.getOpenGLVersion(), "backend" to "OpenGL")

        fun debugMessages(): List<String> = GlDebug.getLastOpenGlDebugMessages().toList()
    }
}

private class NameplateFlatDrawState(
    private val key: String,
    private val variant: NameplateFlatVariant,
    private val program: NameplateShaderProgram,
    private val textures: List<Identifier>,
) {
    private var active = false

    fun setup() {
        val shader = variant.compiled
        val words = NameplateMaterialUniforms.words(key)
        if (shader == null || words == null) {
            RenderSystem.clearShader()
            return
        }
        active = true
        RenderSystem.setShader(shader)
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer()
        for ((i, texture) in textures.withIndex()) {
            RenderSystem.setShaderTexture(program.samplers[i].removePrefix(SAMPLER).toInt(), texture)
        }
        setFlatUniforms(program, words, shader::safeGetUniform)
        beginFlatState(program, variant.depth)
    }

    fun clear() {
        if (!active) {
            return
        }
        active = false
        endFlatState(variant.depth)
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer()
    }
}

internal class NameplateFlatMaterialRenderType private constructor(key: String, state: NameplateFlatDrawState) :
    RenderType(key, NameplateFlatVariant.FORMAT, VertexFormat.Mode.QUADS, BUFFER_SIZE, false, false, { state.setup() }, { state.clear() }) {
    constructor(key: String, variant: NameplateFlatVariant, program: NameplateShaderProgram, textures: List<Identifier>) :
        this(key, NameplateFlatDrawState(key, variant, program, textures))

    private companion object {
        const val BUFFER_SIZE = 1536
    }
}
*///?}
//? if = 1.21.1 {
/*import com.mojang.blaze3d.platform.GlDebug
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.GlUtil
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Optional

internal class NameplateFlatVariant(private val program: NameplateShaderProgram, val depth: Boolean) {
    var compiled: ShaderInstance? = null
        private set

    private val name = "$SHADER_DIRECTORY/material_${program.hash}${if (depth) DEPTH_SUFFIX else ""}"

    val label: String
        get() = flatLabel(program, depth)

    fun compile(): Throwable? {
        close()
        val shader = try {
            ShaderInstance(provider(), name, FORMAT)
        } catch (e: IOException) {
            return e
        }
        if (GlStateManager.glGetProgrami(shader.id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            val log = GlStateManager.glGetProgramInfoLog(shader.id, INFO_LOG_LENGTH)
            shader.close()
            return IllegalStateException("Shader $name did not link: $log")
        }
        compiled = shader
        return null
    }

    fun close() {
        compiled?.close()
        compiled = null
    }

    fun renderType(name: String, textures: List<Identifier>): NameplateLayer = NameplateFlatMaterialRenderType(name, this, program, textures)

    private fun provider(): ResourceProvider {
        val files = mapOf(
            core(".json") to definition(),
            core(".vsh") to program.vertexSource,
            core(".fsh") to flatFragmentSource(program, depth),
        )
        val minecraft = Minecraft.getInstance()
        return ResourceProvider { id ->
            val text = files[id]
            if (text == null) {
                minecraft.resourceManager.getResource(id)
            } else {
                val bytes = text.toByteArray(Charsets.UTF_8)
                Optional.of(Resource(minecraft.vanillaPackResources) { ByteArrayInputStream(bytes) })
            }
        }
    }

    private fun core(extension: String): Identifier = Identifier.withDefaultNamespace("shaders/core/$name$extension")

    private fun definition(): String {
        val json = JsonObject()
        json.addProperty("vertex", name)
        json.addProperty("fragment", name)
        val samplers = JsonArray()
        for (sampler in program.samplers + LIGHTMAP) {
            samplers.add(JsonObject().apply { addProperty("name", sampler) })
        }
        json.add("samplers", samplers)
        val uniforms = JsonArray()
        for ((uniform, type, count) in BUILT_IN) {
            uniforms.add(uniform(uniform, type, count))
        }
        for (uniform in program.uniforms) {
            uniforms.add(if (uniform.ints) uniform(uniform.name, INT, 1) else uniform(uniform.name, FLOAT, VEC4))
        }
        json.add("uniforms", uniforms)
        return json.toString()
    }

    private fun uniform(name: String, type: String, count: Int): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("type", type)
        addProperty("count", count)
        add("values", JsonArray().apply { add(0) })
    }

    companion object {
        val FORMAT: VertexFormat = DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR

        private const val SHADER_DIRECTORY = "polyplus_nameplate"
        private const val INFO_LOG_LENGTH = 32768

        fun driver(): Map<String, String> =
            mapOf("name" to GlUtil.getRenderer(), "vendor" to GlUtil.getVendor(), "driver" to GlUtil.getOpenGLVersion(), "backend" to "OpenGL")

        fun debugMessages(): List<String> = GlDebug.getLastOpenGlDebugMessages().toList()
    }
}

internal class NameplateFlatMaterialRenderType(
    private val key: String,
    private val variant: NameplateFlatVariant,
    private val program: NameplateShaderProgram,
    private val textures: List<Identifier>,
) : RenderType(key, NameplateFlatVariant.FORMAT, VertexFormat.Mode.QUADS, BUFFER_SIZE, false, false, {}, {}) {
    override fun draw(mesh: MeshData) {
        val shader = variant.compiled
        val words = NameplateMaterialUniforms.words(key)
        if (shader == null || words == null) {
            mesh.close()
            return
        }
        RenderSystem.setShader { shader }
        val lightTexture = Minecraft.getInstance().gameRenderer.lightTexture()
        lightTexture.turnOnLightLayer()
        for ((i, texture) in textures.withIndex()) {
            RenderSystem.setShaderTexture(program.samplers[i].removePrefix(SAMPLER).toInt(), texture)
        }
        setFlatUniforms(program, words, shader::safeGetUniform)
        beginFlatState(program, variant.depth)
        try {
            BufferUploader.drawWithShader(mesh)
        } finally {
            endFlatState(variant.depth)
            lightTexture.turnOffLightLayer()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1536
    }
}
*///?}
//? if = 1.21.4 || = 1.21.1 {
/*private const val DEPTH_ONLY = "NAMEPLATE_DEPTH_ONLY"
private const val DEPTH_SUFFIX = "_depth"
private const val LIGHTMAP = "Sampler2"
private const val SAMPLER = "Sampler"
private const val INT = "int"
private const val FLOAT = "float"
private const val MATRIX = "matrix4x4"
private const val VEC4 = 4
private const val MATRIX_FLOATS = 16
private val BUILT_IN = listOf(
    Triple("ModelViewMat", MATRIX, MATRIX_FLOATS),
    Triple("ProjMat", MATRIX, MATRIX_FLOATS),
    Triple("ColorModulator", FLOAT, VEC4),
    Triple("FogStart", FLOAT, 1),
    Triple("FogEnd", FLOAT, 1),
    Triple("FogColor", FLOAT, VEC4),
    Triple("FogShape", INT, 1),
    Triple("ScreenSize", FLOAT, 2),
)

private fun flatLabel(program: NameplateShaderProgram, depth: Boolean): String =
    "${PolyPlusConstants.ID}:pipeline/nameplate_material_${program.hash}${if (depth) DEPTH_SUFFIX else ""}"

private fun flatFragmentSource(program: NameplateShaderProgram, depth: Boolean): String {
    val source = program.fragmentSource
    if (!depth) {
        return source
    }
    val line = source.indexOf('\n') + 1
    return source.substring(0, line) + "#define $DEPTH_ONLY\n" + source.substring(line)
}

private fun setFlatUniforms(program: NameplateShaderProgram, words: IntArray, uniform: (String) -> com.mojang.blaze3d.shaders.AbstractUniform) {
    for (flat in program.uniforms) {
        val target = uniform(flat.name)
        val at = flat.offset
        if (flat.ints) {
            target.set(words[at])
        } else {
            target.set(float(words, at), float(words, at + 1), float(words, at + 2), float(words, at + 3))
        }
    }
}

private fun beginFlatState(program: NameplateShaderProgram, depth: Boolean) {
    RenderSystem.enableDepthTest()
    RenderSystem.depthFunc(GL11.GL_LEQUAL)
    if (depth) {
        RenderSystem.colorMask(false, false, false, false)
    } else {
        RenderSystem.enableBlend()
        if (program.blend == Material.Blend.ADDITIVE) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
        } else {
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            )
        }
        RenderSystem.depthMask(false)
    }
}

private fun endFlatState(depth: Boolean) {
    if (depth) {
        RenderSystem.colorMask(true, true, true, true)
    } else {
        RenderSystem.depthMask(true)
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
    }
    RenderSystem.disableDepthTest()
    RenderSystem.depthFunc(GL11.GL_LEQUAL)
}
*///?}
//? if = 1.21.5 || = 1.21.4 || = 1.21.1 {
/*private fun float(words: IntArray, at: Int): Float = Float.fromBits(words[at])
*///?}
