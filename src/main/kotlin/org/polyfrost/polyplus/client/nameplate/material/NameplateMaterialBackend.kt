package org.polyfrost.polyplus.client.nameplate.material

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.NameplateAsset
import org.polyfrost.polyplus.client.nameplate.engine.NameplateVanillaTextures
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_INDEX
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_OPACITY
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_UNIFORM_AT
//? if > 1.8.9 {
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlas
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.nameplate.engine.NameplateAtlas
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_UNIFORM_WORDS
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLContext
import org.polyfrost.polyplus.client.nameplate.engine.LegacyBlockTextures
import org.polyfrost.polyplus.client.nameplate.render.LegacyCeraShaders
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
*///?}
//? if >= 26.3 {
import com.mojang.blaze3d.pipeline.PipelineCache
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.ShaderSource
import com.mojang.renderpearl.api.pipeline.ShaderType
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline
import com.mojang.renderpearl.frontend.FrontendRenderPipeline
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.ShaderManager
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import org.polyfrost.polyplus.client.cosmetics.render.PooledGeometry
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import org.lwjgl.system.MemoryUtil
import org.polyfrost.polyplus.mixin.client.access.GameRendererAccessor
import org.polyfrost.polyplus.mixin.client.nameplate.NameplatePipelineCacheAccessor
import org.polyfrost.polyplus.mixin.client.nameplate.NameplatePipelineCacheMapAccessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional
import java.util.concurrent.Executor
//?} elif >= 1.21.5 {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.mixin.client.nameplate.NameplateGlPipelinesAccessor
import com.mojang.blaze3d.opengl.GlProgram
import com.mojang.blaze3d.opengl.GlRenderPipeline
import java.util.IdentityHashMap
//? if >= 1.21.11 {
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
//?} else {
/^import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import java.util.function.BiFunction
^///?}
//? if < 1.21.6 {
/^import net.minecraft.util.TriState
^///?}
//? if >= 26.2 {
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline
import net.minecraft.client.renderer.rendertype.PreparedRenderType
import org.polyfrost.polyplus.mixin.client.nameplate.NameplateVulkanPipelinesAccessor
//?}
//? if >= 26.1 {
import org.polyfrost.polyplus.mixin.client.nameplate.NameplateDeviceBackendAccessor
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.platform.CompareOp
import java.util.Optional
//?} else {
/^import com.mojang.blaze3d.platform.DepthTestFunction
^///?}
//? if >= 1.21.6 {
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.shaders.UniformType
import net.minecraft.client.renderer.fog.FogRenderer
import org.lwjgl.system.MemoryUtil
import org.polyfrost.polyplus.mixin.client.access.GameRendererAccessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
//?} else {
/^import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.shaders.UniformType
import org.lwjgl.opengl.GL20
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer
import java.nio.IntBuffer
^///?}
//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.OrderedSubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeCollector
import org.polyfrost.polyplus.client.cosmetics.render.PooledGeometry
//?} else {
/^import net.minecraft.client.renderer.MultiBufferSource
^///?}
*///?} elif > 1.8.9 {
/*import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.shaders.AbstractUniform
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import org.lwjgl.opengl.GL11
//? if >= 1.21.4 {
import com.mojang.blaze3d.shaders.CompiledShader
import net.minecraft.client.renderer.CompiledShaderProgram
import net.minecraft.client.renderer.ShaderProgramConfig
//?} else {
/^import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import java.io.ByteArrayInputStream
import java.util.Optional
^///?}
*///?}

object NameplateMaterialBackend : NameplatePackTarget {
    //? if >= 1.21.5 {
    private const val NAMESPACE = "polyplus"
    private const val MATERIAL_BLOCK = "NameplateMaterial"
    private const val TYPE_PREFIX = "polyplus_nameplate_material_"
    private const val BUNDLE_PATH = "nameplate/bundle/"
    //?}
    //? if > 1.8.9 {
    private const val MAX_DRAWS = 1024
    //?}
    //? if > 1.8.9 && < 26.3 {
    /*private const val BUFFER_SIZE = 1536
    *///?}
    //? if >= 26.3 {
    private const val LIGHTMAP = "Sampler2"
    private const val FOG = "Fog"
    private val DIRECT = Executor { it.run() }
    private var additivePipeline: RenderPipeline? = null
    private var additiveCompiled: CompiledRenderPipeline? = null
    //?} elif >= 1.21.5 {
    /*private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private const val RETIRE_FRAMES = 8
    *///?} elif > 1.8.9 {
    /*private const val GL_LEQUAL = 515
    private const val GL_SRC_ALPHA = 770
    private const val GL_ONE_MINUS_SRC_ALPHA = 771
    private const val GL_ONE = 1
    private const val LIGHTMAP_UNIT = 2
    private const val NAME_PREFIX = "polyplus_nameplate_"
    *///?} else {
    /*private const val LIGHTMAP_UNIT = 1
    private const val MAX_VECTORS = 64
    private const val FRAMEBUFFER_SLOTS = 4
    private const val MAX_DRAW_BUFFERS = 16
    private const val GL_SRC_ALPHA = 770
    private const val GL_ONE_MINUS_SRC_ALPHA = 771
    private const val GL_ONE = 1
    private const val GL_ZERO = 0
    private const val GL_FOG_LINEAR = 0x2601
    private const val GL_FOG_EXP = 0x0800
    private const val GL_FOG_EXP2 = 0x0801
    private const val INFO_LOG = 32768
    *///?}
    //? if >= 26.2 {
    private val DEPTH_TEST = CompareOp.GREATER_THAN_OR_EQUAL
    //?} elif >= 26.1 {
    /*private val DEPTH_TEST = CompareOp.LESS_THAN_OR_EQUAL
    *///?}

    //? if >= 1.21.5 {
    class Program(
        val shader: NameplateShader,
        val color: RenderPipeline,
        val depth: RenderPipeline?,
        //? if < 26.3 {
        /*val compiled: CompiledRenderPipeline,
        *///?}
    ) {
        //? if >= 26.3 {
        var compiledColor: CompiledRenderPipeline? = null
        var compiledDepth: CompiledRenderPipeline? = null
        //?}
        val colorTypes = ArrayList<RenderType>()
        val depthTypes = ArrayList<RenderType>()
        //? if >= 26.3 {
        val colorSlots = ArrayList<Slot>()
        val depthSlots = ArrayList<Slot>()
        //?}
        var colorUsed = 0
        var depthUsed = 0
        var texturesLoaded = false
        //? if < 1.21.6 {
        /*val locations = IntArray(shader.loose.size) { -2 }
        *///?}
    }
    //?} elif >= 1.21.4 {
    /*class Program(val shader: NameplateShader, val instance: CompiledShaderProgram) {
        val targets = arrayOfNulls<AbstractUniform>(shader.loose.size)
    }
    *///?} elif > 1.8.9 {
    /*class Program(val shader: NameplateShader, val instance: ShaderInstance) {
        val targets = arrayOfNulls<AbstractUniform>(shader.loose.size)
    }
    *///?} else {
    /*class Program(val id: Int, val shader: NameplateShader) {
        val loose = IntArray(shader.loose.size)
        var lit = -1
        var fogMode = -1
        var screen = -1
    }
    *///?}

    //? if >= 1.21.5 {
    class Texture(val id: Identifier, val texture: DynamicTexture)
    //?} elif > 1.8.9 {
    /*class Texture(val texture: DynamicTexture)
    *///?} else {
    /*class Texture(val id: Int)
    *///?}

    //? if > 1.8.9 {
    //? if >= 26.3 {
    class Slot internal constructor(val name: String) : PooledGeometry, NameplateQuadSink {
    //?} elif >= 1.21.10 {
    /*private class Slot : PooledGeometry, NameplateQuadSink {
    *///?} else {
    /*private class Slot {
    *///?}
        var program: Program? = null
        var programs: NameplatePrograms? = null
        //? if >= 1.21.6 {
        var uniforms: GpuBufferSlice? = null
        //?} else {
        /*var words = IntArray(64)
        *///?}
        //? if < 1.21.5 {
        /*var depthOnly = false
        *///?}
        //? if >= 1.21.10 {
        var quads = FloatArray(8 * 4)
        var count = 0
        var z = 0f
        var color = -1
        var light = 0

        override fun quad(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
            if ((count + 1) * 8 > quads.size) quads = quads.copyOf(quads.size * 2)
            val q = count * 8
            quads[q] = x0
            quads[q + 1] = y0
            quads[q + 2] = x1
            quads[q + 3] = y1
            quads[q + 4] = u0
            quads[q + 5] = v0
            quads[q + 6] = u1
            quads[q + 7] = v1
            count++
        }

        override fun render(pose: PoseStack.Pose, buffer: VertexConsumer) {
            val m = pose.pose()
            for (k in 0 until count) {
                val q = k * 8
                vertexQuad(buffer, m, quads[q], quads[q + 1], quads[q + 2], quads[q + 3], quads[q + 4], quads[q + 5], quads[q + 6], quads[q + 7], z, color, light)
            }
        }
        //?}
    }
    //?}

    //? if > 1.8.9 && < 1.21.5 {
    /*private class MaterialType(name: String, slot: Slot) : RenderType(
        name,
        DefaultVertexFormat.PARTICLE,
        VertexFormat.Mode.QUADS,
        BUFFER_SIZE,
        false,
        false,
        Runnable { setup(slot) },
        Runnable { clear(slot) },
    )
    *///?}

    //? if >= 26.3 {
    private val slots = HashMap<String, Slot>()
    //?} elif >= 1.21.5 {
    /*private val slots = IdentityHashMap<RenderType, Slot>()
    *///?} elif > 1.8.9 {
    /*private val slots = ArrayList<Slot>()
    private val types = ArrayList<RenderType>()
    private var used = 0
    *///?}
    //? if = 26.2 {
    /*private val prepared = IdentityHashMap<PreparedRenderType, Slot>()
    *///?}
    //? if >= 1.21.5 {
    private val active = ArrayList<Program>()
    //?}
    private val atlasRects = HashMap<String, FloatArray?>()
    //? if < 26.3 {
    /*private val sink = Sink()
    *///?}
    //? if >= 26.3 {
    private var includes: Map<Identifier, ShaderSource.CachedIncludeSource>? = null
    //?}
    //? if >= 1.21.5 {
    private var textureSerial = 0
    private var pipelineSerial = 0
    //?}
    //? if >= 1.21.6 {
    private const val UNIFORM_CHUNK = 64 * 1024
    private val uniformBuffers = ArrayList<GpuBuffer>()
    private var uniformBuffer = 0
    //? if >= 26.3 {
    private var uniformOffset = 0L
    //?} else {
    /*private var uniformOffset = 0
    *///?}
    private var staging: ByteBuffer = MemoryUtil.memAlloc(4096).order(ByteOrder.nativeOrder())
    //?} elif >= 1.21.5 {
    /*private val floats: FloatBuffer = MemoryUtil.memAllocFloat(4)
    private val ints: IntBuffer = MemoryUtil.memAllocInt(4)
    *///?}
    //? if >= 26.3 {
    private var additive: RenderType? = null
    private var lastSlot: Slot? = null
    private var additiveFailed = false
    private var foreignBackend = false
    //?} elif >= 1.21.5 {
    /*private val retired = ArrayDeque<Any>()
    private val retiredAt = ArrayDeque<Int>()
    private var frames = 0
    *///?}
    //? if > 1.8.9 {
    private val owned = NameplateOwnedPipelines()
    //?} else {
    /*private val floats: FloatBuffer = BufferUtils.createFloatBuffer(MAX_VECTORS * 4)
    private val ints: IntBuffer = BufferUtils.createIntBuffer(MAX_VECTORS * 4)
    private val queries: IntBuffer = BufferUtils.createIntBuffer(16)
    private val drawBuffers = NameplateDrawBuffers(FRAMEBUFFER_SLOTS, MAX_DRAW_BUFFERS)
    private val restoreBuffers: IntBuffer = BufferUtils.createIntBuffer(MAX_DRAW_BUFFERS)
    private val gl20: Boolean by lazy { GLContext.getCapabilities().OpenGL20 }
    private val drawBufferCount: Int by lazy { GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS).coerceIn(1, MAX_DRAW_BUFFERS) }
    private var savedTextures = IntArray(8)
    private var boundSamplers = 0
    *///?}

    //? if >= 26.3 {
    val additiveImages: RenderType
        get() {
            additive?.let {
                ensure(additivePipeline, additiveCompiled)
                return it
            }
            if (additiveFailed) return fallbackImages()
            return try {
                val pipeline = RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nameplate_additive_images_" + pipelineSerial++))
                    .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
                    .build()
                val compiled = compile(pipeline, null)
                insert(pipeline, compiled)
                additivePipeline = pipeline
                additiveCompiled = compiled
                RenderType.create(
                    "polyplus_nameplate_additive_images",
                    RenderSetup.builder(pipeline).withTexture("Sampler0", NameplateAtlas.ID).useLightmap().createRenderSetup(),
                ).also { additive = it }
            } catch (e: RuntimeException) {
                additiveFailed = true
                fallbackImages()
            }
        }

    private fun fallbackImages(): RenderType = RenderTypes.text(NameplateAtlas.ID)
    //?} elif >= 1.21.5 {
    /*private val additivePipeline: RenderPipeline by lazy {
        //? if >= 26.2 {
        RenderPipeline.builder(RenderPipelines.WORLD_TEXT_SNIPPET)
        //?} else {
        /^RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
        ^///?}
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nameplate_additive_images"))
            //? if >= 26.2 {
            .withVertexShader(RenderPipelines.TEXT.vertexShader)
            .withFragmentShader(RenderPipelines.TEXT.fragmentShader)
            //?}
            //? if >= 26.1 {
            .withColorTargetState(ColorTargetState(BlendFunction.LIGHTNING))
            //?} else {
            /^.withBlend(BlendFunction.LIGHTNING)
            ^///?}
            .build()
            .also(owned::add)
    }

    val additiveImages: RenderType by lazy {
        //? if >= 1.21.11 {
        RenderType.create(
            "polyplus_nameplate_additive_images",
            //? if >= 26.2 {
            RenderSetup.builder(additivePipeline).withTexture("Sampler0", NameplateAtlas.ID).useLightmap().createRenderSetup(),
            //?} else {
            /^RenderSetup.builder(additivePipeline).withTexture("Sampler0", NameplateAtlas.ID).useLightmap().bufferSize(BUFFER_SIZE).createRenderSetup(),
            ^///?}
        )
        //?} else {
        /^RenderType.create(
            "polyplus_nameplate_additive_images",
            BUFFER_SIZE,
            false,
            false,
            additivePipeline,
            RenderType.CompositeState.builder()
                //? if >= 1.21.6 {
                .setTextureState(RenderStateShard.TextureStateShard(NameplateAtlas.ID, false))
                //?} else {
                /^.setTextureState(RenderStateShard.TextureStateShard(NameplateAtlas.ID, TriState.FALSE, false))
                ^///?}
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .createCompositeState(false),
        )
        ^///?}
    }
    *///?} elif > 1.8.9 {
    /*val additiveImages: RenderType = object : RenderType(
        "polyplus_nameplate_additive_images",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
        VertexFormat.Mode.QUADS,
        BUFFER_SIZE,
        false,
        false,
        Runnable {
            RenderType.text(NameplateAtlas.ID).setupRenderState()
            RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE)
        },
        Runnable { RenderType.text(NameplateAtlas.ID).clearRenderState() },
    ) {}
    *///?}

    val dialect: Int =
        //? if >= 26.3 {
        NameplateAsset.DIALECT_CORE_330
        //?} elif >= 1.21.6 {
        /*NameplateAsset.DIALECT_CORE_330_UBO
        *///?} elif > 1.8.9 {
        /*NameplateAsset.DIALECT_LEGACY_150
        *///?} else {
        /*NameplateAsset.DIALECT_COMPAT_130
        *///?}

    fun compile(shader: NameplateShader): Any {
        //? if >= 26.3 {
        val path = "nameplate/" + shader.key.lowercase() + "_" + pipelineSerial++
        val color = pipeline(shader, path, false)
        val depth = if (shader.depthPrepass) pipeline(shader, path + "_depth", true) else null
        val program = Program(shader, color, depth)
        try {
            val compiledColor = compile(color, shader)
            program.compiledColor = compiledColor
            insert(color, compiledColor)
            if (depth != null) {
                val compiledDepth = compile(depth, shader)
                program.compiledDepth = compiledDepth
                insert(depth, compiledDepth)
            }
        } catch (e: Throwable) {
            discard(color, program.compiledColor)
            discard(depth, program.compiledDepth)
            throw e
        }
        return program
        //?} elif >= 1.21.5 {
        /*val serial = pipelineSerial++
        val path = "nameplate/" + shader.key.lowercase() + "_" + serial
        val color = pipeline(shader, path, false)
        val depth = if (shader.depthPrepass) pipeline(shader, path + "_depth", true) else null
        try {
            val compiled = precompile(color, shader)
            if (depth != null) precompile(depth, shader)
            owned.add(color)
            owned.add(depth)
            return Program(shader, color, depth, compiled)
        } catch (e: Throwable) {
            discard(color)
            if (depth != null) discard(depth)
            throw e
        }
        *///?} elif > 1.8.9 {
        /*val name = NAME_PREFIX + shader.key.lowercase()
        //? if >= 1.21.4 {
        val vertex = CompiledShader.compile(Identifier.fromNamespaceAndPath("polyplus", "$name.vsh"), CompiledShader.Type.VERTEX, shader.vertex)
        val fragment = try {
            CompiledShader.compile(Identifier.fromNamespaceAndPath("polyplus", "$name.fsh"), CompiledShader.Type.FRAGMENT, shader.fragment)
        } catch (e: Exception) {
            vertex.close()
            throw e
        }
        val program = try {
            CompiledShaderProgram.link(vertex, fragment, DefaultVertexFormat.PARTICLE)
        } finally {
            vertex.close()
            fragment.close()
        }
        try {
            program.setupUniforms(uniformConfigs(shader), shader.samplers.map { ShaderProgramConfig.Sampler(it.name) } + ShaderProgramConfig.Sampler("Sampler2"))
        } catch (e: RuntimeException) {
            program.close()
            throw e
        }
        owned.add(program)
        return Program(shader, program)
        //?} else {
        /^val files = HashMap<String, ByteArray>()
        files["shaders/core/$name.json"] = config(name, shader).toByteArray()
        files["shaders/core/$name.vsh"] = shader.vertex.toByteArray()
        files["shaders/core/$name.fsh"] = shader.fragment.toByteArray()
        val pack = Minecraft.getInstance().vanillaPackResources
        val provider = ResourceProvider { location ->
            val bytes = if (location.namespace == "minecraft") files[location.path] else null
            if (bytes == null) Minecraft.getInstance().resourceManager.getResource(location)
            else Optional.of(Resource(pack) { ByteArrayInputStream(bytes) })
        }
        return Program(shader, ShaderInstance(provider, name, DefaultVertexFormat.PARTICLE).also(owned::add))
        ^///?}
        *///?} else {
        /*val vertex = stage(GL20.GL_VERTEX_SHADER, shader.vertex)
        val fragment = try {
            stage(GL20.GL_FRAGMENT_SHADER, shader.fragment)
        } catch (e: RuntimeException) {
            GL20.glDeleteShader(vertex)
            throw e
        }
        val id = GL20.glCreateProgram()
        GL20.glAttachShader(id, vertex)
        GL20.glAttachShader(id, fragment)
        GL20.glLinkProgram(id)
        GL20.glDetachShader(id, vertex)
        GL20.glDetachShader(id, fragment)
        GL20.glDeleteShader(vertex)
        GL20.glDeleteShader(fragment)
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetProgramInfoLog(id, INFO_LOG)
            GL20.glDeleteProgram(id)
            throw IllegalStateException("Nameplate effect program did not link: $log")
        }
        val program = Program(id, shader)
        val previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        GL20.glUseProgram(id)
        try {
            for (sampler in shader.samplers) {
                val location = GL20.glGetUniformLocation(id, sampler.name)
                if (location >= 0) GL20.glUniform1i(location, sampler.unit)
            }
            val lightmap = GL20.glGetUniformLocation(id, "NameplateLightmap")
            if (lightmap >= 0) GL20.glUniform1i(lightmap, LIGHTMAP_UNIT)
            program.lit = GL20.glGetUniformLocation(id, "NameplateLit")
            program.fogMode = GL20.glGetUniformLocation(id, "NameplateFogMode")
            program.screen = GL20.glGetUniformLocation(id, NameplateShader.SCREEN_SIZE)
            for (i in shader.loose.indices) program.loose[i] = GL20.glGetUniformLocation(id, shader.loose[i].name)
        } catch (e: RuntimeException) {
            GL20.glDeleteProgram(id)
            throw e
        } finally {
            GL20.glUseProgram(previous)
        }
        return program
        *///?}
    }

    //? if >= 1.21.5 {
    private fun pipeline(shader: NameplateShader, path: String, depthOnly: Boolean): RenderPipeline {
        val shaderId = Identifier.fromNamespaceAndPath(NAMESPACE, path)
        //? if >= 26.3 {
        val layout = BindGroupLayout.builder().withUniform(LIGHTMAP, UniformType.COMBINED_IMAGE_SAMPLER)
        for (sampler in shader.samplers) layout.withUniform(sampler.name, UniformType.COMBINED_IMAGE_SAMPLER)
        //?} elif >= 26.2 {
        /*val layout = BindGroupLayout.builder().withSampler("Sampler2")
        for (sampler in shader.samplers) layout.withSampler(sampler.name)
        *///?}
        //? if >= 26.2 {
        layout.withUniform(MATERIAL_BLOCK, UniformType.UNIFORM_BUFFER)
        val builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/$path"))
            .withVertexShader(shaderId)
            .withFragmentShader(shaderId)
            .withBindGroupLayout(layout.build())
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
        //?} else {
        /*val builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/$path"))
            .withVertexShader(shaderId)
            .withFragmentShader(shaderId)
            .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
            .withCull(false)
            .withSampler("Sampler2")
        for (sampler in shader.samplers) builder.withSampler(sampler.name)
        *///?}
        //? if >= 1.21.6 && < 26.2 {
        /*builder.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform(MATERIAL_BLOCK, UniformType.UNIFORM_BUFFER)
        if (shader.readsScreen) builder.withUniform("Globals", UniformType.UNIFORM_BUFFER)
        *///?} elif < 1.21.6 {
        /*builder.withUniform("ModelViewMat", UniformType.MATRIX4X4)
            .withUniform("ProjMat", UniformType.MATRIX4X4)
            .withUniform("ColorModulator", UniformType.VEC4)
            .withUniform("FogStart", UniformType.FLOAT)
            .withUniform("FogEnd", UniformType.FLOAT)
            .withUniform("FogColor", UniformType.VEC4)
            .withUniform("FogShape", UniformType.INT)
        if (shader.readsScreen) builder.withUniform(NameplateShader.SCREEN_SIZE, UniformType.VEC2)
        *///?}
        //? if >= 1.21.8 && < 1.21.11 {
        /*if (shader.readsScreen) builder.withShaderDefine("NAMEPLATE_SCREEN_FIRST")
        *///?}
        //? if >= 26.1 {
        if (depthOnly) {
            builder.withDepthStencilState(DepthStencilState(DEPTH_TEST, true))
            //? if >= 26.2 {
            builder.withColorTargetState(ColorTargetState(Optional.empty(), ColorTargetState.DEFAULT.format(), ColorTargetState.WRITE_NONE))
            //?} else {
            /*builder.withColorTargetState(ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE))
            *///?}
        } else {
            builder.withDepthStencilState(DepthStencilState(DEPTH_TEST, false))
            builder.withColorTargetState(ColorTargetState(if (shader.additive) BlendFunction.LIGHTNING else BlendFunction.TRANSLUCENT))
        }
        //?} else {
        /*builder.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        if (depthOnly) {
            builder.withoutBlend().withColorWrite(false).withDepthWrite(true)
        } else {
            builder.withBlend(if (shader.additive) BlendFunction.LIGHTNING else BlendFunction.TRANSLUCENT).withDepthWrite(false)
        }
        *///?}
        return builder.build()
    }
    //?}

    //? if >= 26.3 {
    private fun compile(pipeline: RenderPipeline, shader: NameplateShader?): CompiledRenderPipeline {
        val source = object : ShaderSource {
            override fun getShader(id: Identifier, type: ShaderType): String? {
                if (shader != null && id.namespace == NAMESPACE) return if (type == ShaderType.VERTEX) shader.vertex else shader.fragment
                val file = type.idConverter().idToFile(id)
                return Minecraft.getInstance().resourceManager.getResource(file).orElse(null)?.openAsReader()?.use { it.readText() }
            }

            override fun getInclude(id: Identifier): ShaderSource.CachedIncludeSource? = includes()[id]

            override fun close() = Unit
        }
        val compiled = RenderSystem.getDevice().compilePipeline(pipeline, source, DIRECT).join().finishCompile()
        check(compiled != null && !compiled.isClosed) { "Nameplate effect pipeline ${pipeline.location} did not compile" }
        val backend = backendOf(compiled)
        if (backend is GlRenderPipeline) owned.add(backend) else foreignBackend = true
        return compiled
    }

    private fun backendOf(compiled: CompiledRenderPipeline?): Any? = (compiled as? FrontendRenderPipeline)?.backendRenderPipeline()

    private fun includes(): Map<Identifier, ShaderSource.CachedIncludeSource> {
        includes?.let { return it }
        return ShaderManager.listAllIncludes(Minecraft.getInstance().resourceManager).also { includes = it }
    }

    private fun ensure(pipeline: RenderPipeline?, compiled: CompiledRenderPipeline?) {
        if (pipeline == null || compiled == null) return
        if (RenderSystem.getCompiledPipelineNullable(pipeline) == null) insert(pipeline, compiled)
    }

    private fun insert(pipeline: RenderPipeline, compiled: CompiledRenderPipeline) {
        val current: PipelineCache? = NameplatePipelineCacheAccessor.`polyplus$current`()
        val fallback: PipelineCache? = NameplatePipelineCacheAccessor.`polyplus$fallback`()
        check(current != null || fallback != null) { "No pipeline cache to hold nameplate effects" }
        current?.insert(pipeline, compiled)
        fallback?.insert(pipeline, compiled)
    }

    private fun discard(pipeline: RenderPipeline?, compiled: CompiledRenderPipeline?) {
        if (pipeline != null) {
            val current = (NameplatePipelineCacheAccessor.`polyplus$current`() as NameplatePipelineCacheMapAccessor?)?.`polyplus$cache`()?.remove(pipeline)
            val fallback = (NameplatePipelineCacheAccessor.`polyplus$fallback`() as NameplatePipelineCacheMapAccessor?)?.`polyplus$cache`()?.remove(pipeline)
            if (current !== compiled) close(current)
            if (fallback !== compiled && fallback !== current) close(fallback)
        }
        close(compiled)
    }

    private fun close(compiled: CompiledRenderPipeline?) {
        owned.remove(backendOf(compiled))
        if (compiled != null && !compiled.isClosed) compiled.close()
    }
    //?} elif >= 1.21.5 {
    /*private fun precompile(pipeline: RenderPipeline, shader: NameplateShader): CompiledRenderPipeline {
        //? if >= 1.21.11 {
        val source = ShaderSource { _, type -> if (type == ShaderType.VERTEX) shader.vertex else shader.fragment }
        //?} else {
        /^val source = BiFunction<Identifier, ShaderType, String> { _, type -> if (type == ShaderType.VERTEX) shader.vertex else shader.fragment }
        ^///?}
        val compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source)
        check(compiled.isValid) { "Nameplate effect pipeline ${pipeline.location} did not compile" }
        return compiled
    }

    private fun discard(pipeline: RenderPipeline) {
        owned.remove(pipeline)
        val compiled = pipelines()?.remove(pipeline) ?: return
        retired.addLast(compiled)
        retiredAt.addLast(frames + RETIRE_FRAMES)
    }

    private fun deviceBackend(): Any {
        //? if >= 26.1 {
        return (RenderSystem.getDevice() as NameplateDeviceBackendAccessor).`polyplus$backend`()
        //?} else {
        /^return RenderSystem.getDevice()
        ^///?}
    }

    private fun pipelines(): MutableMap<RenderPipeline, *>? {
        val backend = deviceBackend()
        //? if >= 26.2 {
        (backend as? NameplateVulkanPipelinesAccessor)?.let { return it.`polyplus$pipelines`() }
        //?}
        return (backend as? NameplateGlPipelinesAccessor)?.`polyplus$pipelines`()
    }

    private fun close(compiled: Any) {
        //? if >= 26.2 {
        if (compiled is VulkanRenderPipeline) {
            compiled.destroy()
            return
        }
        //?}
        val program = (compiled as? GlRenderPipeline)?.program() ?: return
        if (program !== GlProgram.INVALID_PROGRAM) program.close()
    }

    private fun closeRetired() {
        while (retired.isNotEmpty() && retiredAt.first() <= frames) {
            val compiled = retired.removeFirst()
            retiredAt.removeFirst()
            try {
                close(compiled)
            } catch (e: RuntimeException) {
                LOGGER.warn("Could not free a nameplate effect pipeline", e)
            }
        }
    }
    *///?} elif >= 1.21.4 {
    /*private fun uniformConfigs(shader: NameplateShader): List<ShaderProgramConfig.Uniform> {
        val out = ArrayList<ShaderProgramConfig.Uniform>()
        out += ShaderProgramConfig.Uniform("ModelViewMat", "matrix4x4", 16, IDENTITY)
        out += ShaderProgramConfig.Uniform("ProjMat", "matrix4x4", 16, IDENTITY)
        out += ShaderProgramConfig.Uniform("ColorModulator", "float", 4, listOf(1f, 1f, 1f, 1f))
        out += ShaderProgramConfig.Uniform("FogStart", "float", 1, listOf(0f))
        out += ShaderProgramConfig.Uniform("FogEnd", "float", 1, listOf(1f))
        out += ShaderProgramConfig.Uniform("FogColor", "float", 4, listOf(0f, 0f, 0f, 0f))
        out += ShaderProgramConfig.Uniform("FogShape", "int", 1, listOf(0f))
        if (shader.readsScreen) out += ShaderProgramConfig.Uniform(NameplateShader.SCREEN_SIZE, "float", 2, listOf(1f, 1f))
        for (uniform in shader.loose) {
            out += ShaderProgramConfig.Uniform(uniform.name, if (uniform.ints) "int" else "float", 4, listOf(0f, 0f, 0f, 0f))
        }
        return out
    }

    private val IDENTITY: List<Float> = listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    *///?} elif > 1.8.9 {
    /*private fun config(name: String, shader: NameplateShader): String {
        val identity = "[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]"
        val out = StringBuilder()
        out.append("{\"vertex\":\"").append(name).append("\",\"fragment\":\"").append(name).append("\",\"samplers\":[{\"name\":\"Sampler2\"}")
        for (sampler in shader.samplers) out.append(",{\"name\":\"").append(sampler.name).append("\"}")
        out.append("],\"uniforms\":[")
        out.append("{\"name\":\"ModelViewMat\",\"type\":\"matrix4x4\",\"count\":16,\"values\":").append(identity).append("},")
        out.append("{\"name\":\"ProjMat\",\"type\":\"matrix4x4\",\"count\":16,\"values\":").append(identity).append("},")
        out.append("{\"name\":\"ColorModulator\",\"type\":\"float\",\"count\":4,\"values\":[1.0,1.0,1.0,1.0]},")
        out.append("{\"name\":\"FogStart\",\"type\":\"float\",\"count\":1,\"values\":[0.0]},")
        out.append("{\"name\":\"FogEnd\",\"type\":\"float\",\"count\":1,\"values\":[1.0]},")
        out.append("{\"name\":\"FogColor\",\"type\":\"float\",\"count\":4,\"values\":[0.0,0.0,0.0,0.0]},")
        out.append("{\"name\":\"FogShape\",\"type\":\"int\",\"count\":1,\"values\":[0]}")
        if (shader.readsScreen) out.append(",{\"name\":\"ScreenSize\",\"type\":\"float\",\"count\":2,\"values\":[1.0,1.0]}")
        for (uniform in shader.loose) {
            out.append(",{\"name\":\"").append(uniform.name).append("\",\"type\":\"").append(if (uniform.ints) "int" else "float")
                .append("\",\"count\":4,\"values\":[0,0,0,0]}")
        }
        out.append("]}")
        return out.toString()
    }
    *///?} else {
    /*private fun stage(type: Int, source: String): Int {
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, source)
        GL20.glCompileShader(id)
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(id, INFO_LOG)
            GL20.glDeleteShader(id)
            throw IllegalStateException("Nameplate effect shader did not compile: $log")
        }
        return id
    }
    *///?}

    fun release(program: Any) {
        //? if >= 26.3 {
        val p = program as Program
        for (slot in p.colorSlots) slots.remove(slot.name)
        for (slot in p.depthSlots) slots.remove(slot.name)
        p.colorTypes.clear()
        p.depthTypes.clear()
        p.colorSlots.clear()
        p.depthSlots.clear()
        active.remove(p)
        discard(p.color, p.compiledColor)
        discard(p.depth, p.compiledDepth)
        p.compiledColor = null
        p.compiledDepth = null
        //?} elif >= 1.21.5 {
        /*val p = program as Program
        for (type in p.colorTypes) slots.remove(type)
        for (type in p.depthTypes) slots.remove(type)
        p.colorTypes.clear()
        p.depthTypes.clear()
        active.remove(p)
        discard(p.color)
        p.depth?.let(::discard)
        *///?} elif > 1.8.9 {
        /*val instance = (program as Program).instance
        owned.remove(instance)
        instance.close()
        *///?} else {
        /*GL20.glDeleteProgram((program as Program).id)
        *///?}
    }

    //? if >= 26.3 {
    override fun resolve(): Boolean = !foreignBackend
    //?} elif >= 1.21.5 {
    /*override fun resolve(): Boolean = deviceBackend() is NameplateGlPipelinesAccessor
    *///?} elif > 1.8.9 {
    /*override fun resolve(): Boolean = true
    *///?} else {
    /*override fun resolve(): Boolean {
        drawBuffers.reset()
        val managed = LegacyCeraShaders.managed()
        if (LegacyCeraShaders.unreadable() || (managed && !LegacyCeraShaders.packHooks())) {
            NameplateShaderPacks.targetFailed(IllegalStateException("Cera's shader pack hooks are missing"))
            return false
        }
        return managed && gl20
    }
    *///?}

    //? if > 1.8.9 {
    override fun owns(pipeline: Any): Boolean = pipeline in owned
    //?} else {
    /*override fun owns(pipeline: Any): Boolean = pipeline is Program
    *///?}

    fun texture(label: String, width: Int, height: Int, rgba: ByteArray): Any {
        //? if > 1.8.9 {
        val image = NativeImage(width, height, false)
        var at = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rgba[at].toInt() and 0xFF
                val g = rgba[at + 1].toInt() and 0xFF
                val b = rgba[at + 2].toInt() and 0xFF
                val a = rgba[at + 3].toInt() and 0xFF
                at += 4
                //? if >= 1.21.4 {
                image.setPixel(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                //?} else {
                /*image.setPixelRGBA(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
                *///?}
            }
        }
        //? if >= 1.21.5 {
        val texture = DynamicTexture({ label }, image)
        texture.upload()
        val id = Identifier.fromNamespaceAndPath(NAMESPACE, BUNDLE_PATH + textureSerial++)
        Minecraft.getInstance().textureManager.register(id, texture)
        return Texture(id, texture)
        //?} else {
        /*val texture = DynamicTexture(image)
        texture.upload()
        return Texture(texture)
        *///?}
        //?} else {
        /*val pixels: ByteBuffer = BufferUtils.createByteBuffer(rgba.size)
        pixels.put(rgba).flip()
        val id = GlStateManager.genTextures()
        GlStateManager.bindTexture(id)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
        GlStateManager.bindTexture(0)
        return Texture(id)
        *///?}
    }

    fun releaseTexture(texture: Any) {
        //? if >= 1.21.5 {
        Minecraft.getInstance().textureManager.release((texture as Texture).id)
        //?} elif > 1.8.9 {
        /*(texture as Texture).texture.close()
        *///?} else {
        /*GlStateManager.deleteTextures((texture as Texture).id)
        *///?}
    }

    fun atlasRect(name: String, out: FloatArray): Boolean {
        val rect = atlasRects.getOrPut(name) { sprite(name) } ?: return false
        System.arraycopy(rect, 0, out, 0, 4)
        return true
    }

    private fun sprite(name: String): FloatArray? {
        val split = name.indexOf(':')
        val namespace = if (split < 0) "minecraft" else name.substring(0, split)
        val path = if (split < 0) name else name.substring(split + 1)
        //? if >= 1.21.5 {
        val atlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS) as? TextureAtlas ?: return null
        val sprite = atlas.getSprite(Identifier.fromNamespaceAndPath(namespace, path))
        val contents = sprite.contents()
        if (contents.name() == MissingTextureAtlasSprite.getLocation()) return null
        val width = contents.width()
        val height = contents.height()
        val x = Math.round(sprite.u0 * width / (sprite.u1 - sprite.u0))
        val y = Math.round(sprite.v0 * height / (sprite.v1 - sprite.v0))
        return floatArrayOf(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        //?} elif > 1.8.9 {
        /*val sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(Identifier.fromNamespaceAndPath(namespace, path))
        val contents = sprite.contents()
        if (contents.name() == MissingTextureAtlasSprite.getLocation()) return null
        return floatArrayOf(sprite.x.toFloat(), sprite.y.toFloat(), contents.width().toFloat(), contents.height().toFloat())
        *///?} else {
        /*if (!path.startsWith("block/")) return null
        val legacy = "blocks/" + LegacyBlockTextures.name(path.substring("block/".length))
        val atlas = Minecraft.getInstance().blocksAtlas
        val sprite = atlas.getSprite("$namespace:$legacy")
        if (sprite == null || sprite === atlas.missingSprite) return null
        return floatArrayOf(sprite.x.toFloat(), sprite.y.toFloat(), sprite.width.toFloat(), sprite.height.toFloat())
        *///?}
    }

    fun reload() {
        atlasRects.clear()
        //? if >= 26.3 {
        includes?.values?.forEach { runCatching { it.close() } }
        includes = null
        additive = null
        additiveFailed = false
        discard(additivePipeline, additiveCompiled)
        additivePipeline = null
        additiveCompiled = null
        //?}
    }

    //? if >= 26.2 {
    fun vendor(): String = RenderSystem.getDevice().deviceInfo.vendorName()

    fun api(): String = RenderSystem.getDevice().deviceInfo.backendName()
    //?} elif >= 1.21.5 {
    /*fun vendor(): String = RenderSystem.getDevice().vendor

    fun api(): String = RenderSystem.getDevice().backendName + " " + RenderSystem.getDevice().version
    *///?} elif > 1.8.9 {
    /*fun vendor(): String = GlStateManager._getString(GL11.GL_VENDOR) ?: "unknown"

    fun api(): String = "OpenGL " + (GlStateManager._getString(GL11.GL_VERSION) ?: "unknown")
    *///?} else {
    /*fun vendor(): String = GL11.glGetString(GL11.GL_VENDOR) ?: "unknown"

    fun api(): String = "OpenGL " + (GL11.glGetString(GL11.GL_VERSION) ?: "unknown")
    *///?}

    //? if > 1.8.9 {
    fun endFrame() {
        //? if >= 1.21.5 {
        //? if < 26.3 {
        /*frames++
        closeRetired()
        *///?}
        for (program in active) {
            //? if >= 26.3 {
            for (i in 0 until program.colorUsed) clear(program.colorSlots[i])
            for (i in 0 until program.depthUsed) clear(program.depthSlots[i])
            //?} else {
            /*for (i in 0 until program.colorUsed) slots[program.colorTypes[i]]?.let { clear(it) }
            for (i in 0 until program.depthUsed) slots[program.depthTypes[i]]?.let { clear(it) }
            *///?}
            program.colorUsed = 0
            program.depthUsed = 0
        }
        active.clear()
        //? if = 26.2 {
        /*prepared.clear()
        *///?}
        //? if >= 1.21.6 {
        uniformBuffer = 0
        uniformOffset = 0
        //?}
        //?} else {
        /*for (i in 0 until used) {
            slots[i].program = null
            slots[i].programs = null
        }
        used = 0
        *///?}
    }
    //?}

    //? if >= 1.21.5 {
    private fun clear(slot: Slot) {
        slot.program = null
        slot.programs = null
        //? if >= 1.21.6 {
        slot.uniforms = null
        //?}
        //? if >= 26.3 {
        slot.count = 0
        //?}
    }
    //?}

    //? if = 26.2 {
    /*@JvmStatic
    fun prepared(type: Any, result: PreparedRenderType) {
        val slot = slots[type] ?: return
        prepared[result] = slot
    }

    @JvmStatic
    fun bindPrepared(type: PreparedRenderType, pass: RenderPass) {
        val slot = prepared[type] ?: return
        bind(slot, pass)
    }
    *///?}

    //? if >= 26.3 {
    @JvmStatic
    fun bind(name: String, pass: RenderPass) {
        if (!name.startsWith(TYPE_PREFIX)) return
        val slot = slots[name] ?: return
        val program = slot.program ?: return
        val uniforms = slot.uniforms ?: return
        pass.setUniform(MATERIAL_BLOCK, uniforms)
        if (NameplateShaderPacks.fogOff()) pass.setUniform(FOG, (Minecraft.getInstance().gameRenderer as GameRendererAccessor).`polyplus$fogRenderer`().getBuffer(FogRenderer.FogMode.NONE))
        val manager = Minecraft.getInstance().textureManager
        val sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
        for (s in program.shader.samplers) {
            val id = when (s.kind) {
                NameplateSampler.KIND_BUNDLE -> (slot.programs?.texture(s.index) as? Texture)?.id
                NameplateSampler.KIND_VANILLA -> NameplateVanillaTextures.location(s.texture)
                NameplateSampler.KIND_ATLAS -> TextureAtlas.LOCATION_BLOCKS
                else -> null
            } ?: continue
            pass.setUniform(s.name, manager.getTexture(id).textureView, sampler)
        }
    }
    //?} elif >= 1.21.5 {
    /*@JvmStatic
    fun bind(type: Any, pass: RenderPass) {
        val slot = slots[type] ?: return
        bind(slot, pass)
    }

    private fun bind(slot: Slot, pass: RenderPass) {
        val program = slot.program ?: return
        //? if >= 1.21.6 {
        val uniforms = slot.uniforms ?: return
        pass.setUniform(MATERIAL_BLOCK, uniforms)
        if (NameplateShaderPacks.fogOff()) pass.setUniform("Fog", (Minecraft.getInstance().gameRenderer as GameRendererAccessor).`polyplus$fogRenderer`().getBuffer(FogRenderer.FogMode.NONE))
        //?} else {
        /^looseUniforms(program, slot.words)
        ^///?}
        val manager = Minecraft.getInstance().textureManager
        for (sampler in program.shader.samplers) {
            val id = when (sampler.kind) {
                NameplateSampler.KIND_BUNDLE -> (slot.programs?.texture(sampler.index) as? Texture)?.id
                NameplateSampler.KIND_VANILLA -> NameplateVanillaTextures.location(sampler.texture)
                NameplateSampler.KIND_ATLAS -> TextureAtlas.LOCATION_BLOCKS
                else -> null
            } ?: continue
            val texture = manager.getTexture(id)
            //? if >= 1.21.11 {
            pass.bindTexture(sampler.name, texture.textureView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST))
            //?} elif >= 1.21.6 {
            /^pass.bindSampler(sampler.name, texture.textureView)
            ^///?} else {
            /^pass.bindSampler(sampler.name, texture.texture)
            ^///?}
        }
    }
    *///?}

    //? if >= 1.21.5 && < 1.21.6 {
    /*private fun looseUniforms(program: Program, words: IntArray) {
        val id = (program.compiled as? GlRenderPipeline)?.program()?.programId ?: return
        GlStateManager._glUseProgram(id)
        val loose = program.shader.loose
        for (i in loose.indices) {
            if (program.locations[i] == -2) program.locations[i] = GlStateManager._glGetUniformLocation(id, loose[i].name)
            val location = program.locations[i]
            if (location < 0) continue
            val w = loose[i].word
            if (loose[i].ints) {
                ints.clear()
                ints.put(words[w]).put(words[w + 1]).put(words[w + 2]).put(words[w + 3]).flip()
                GL20.glUniform4iv(location, ints)
            } else {
                floats.clear()
                floats.put(Float.fromBits(words[w])).put(Float.fromBits(words[w + 1])).put(Float.fromBits(words[w + 2])).put(Float.fromBits(words[w + 3])).flip()
                GlStateManager._glUniform4(location, floats)
            }
        }
    }
    *///?}

    //? if >= 26.3 {
    private fun next(program: Program, depthOnly: Boolean): Int {
        val types = if (depthOnly) program.depthTypes else program.colorTypes
        val used = if (depthOnly) program.depthUsed else program.colorUsed
        if (program.colorUsed == 0 && program.depthUsed == 0) active += program
        if (used == types.size) {
            val pipeline = (if (depthOnly) program.depth else program.color) ?: program.color
            val name = TYPE_PREFIX + pipeline.location.path.replace('/', '_') + "_" + used
            types += RenderType.create(name, RenderSetup.builder(pipeline).useLightmap().createRenderSetup())
            val slot = Slot(name)
            (if (depthOnly) program.depthSlots else program.colorSlots) += slot
            slots[name] = slot
        }
        if (depthOnly) program.depthUsed++ else program.colorUsed++
        return used
    }
    //?} elif >= 1.21.5 {
    /*private fun next(program: Program, depthOnly: Boolean): RenderType {
        val types = if (depthOnly) program.depthTypes else program.colorTypes
        val used = if (depthOnly) program.depthUsed else program.colorUsed
        if (program.colorUsed == 0 && program.depthUsed == 0) active += program
        if (used == types.size) {
            val pipeline = (if (depthOnly) program.depth else program.color) ?: program.color
            val name = TYPE_PREFIX + pipeline.location.path.replace('/', '_') + "_" + used
            //? if >= 1.21.11 {
            //? if >= 26.2 {
            val type = RenderType.create(name, RenderSetup.builder(pipeline).useLightmap().createRenderSetup())
            //?} else {
            /^val type = RenderType.create(name, RenderSetup.builder(pipeline).useLightmap().bufferSize(BUFFER_SIZE).createRenderSetup())
            ^///?}
            //?} else {
            /^val type: RenderType = RenderType.create(
                name,
                BUFFER_SIZE,
                false,
                false,
                pipeline,
                RenderType.CompositeState.builder().setLightmapState(RenderStateShard.LIGHTMAP).createCompositeState(false),
            )
            ^///?}
            types += type
            slots[type] = Slot()
        }
        if (depthOnly) program.depthUsed++ else program.colorUsed++
        return types[used]
    }
    *///?}

    //? if >= 1.21.5 {
    private fun loadTextures(program: Program) {
        program.texturesLoaded = true
        val manager = Minecraft.getInstance().textureManager
        for (s in program.shader.samplers) {
            val id = when (s.kind) {
                NameplateSampler.KIND_VANILLA -> NameplateVanillaTextures.location(s.texture)
                NameplateSampler.KIND_ATLAS -> TextureAtlas.LOCATION_BLOCKS
                else -> null
            } ?: continue
            manager.getTexture(id)
        }
    }
    //?}

    //? if >= 26.3 {
    private fun prepare(list: NameplateDrawList, index: Int, depthOnly: Boolean): RenderType? {
        val programs = list.materialPrograms[index] ?: return null
        val program = programs.program(list.materialWord(index, MATERIAL_INDEX)) as? Program ?: return null
        if (depthOnly && program.depth == null) return null
        if (depthOnly) ensure(program.depth, program.compiledDepth) else ensure(program.color, program.compiledColor)
        if (!program.texturesLoaded) loadTextures(program)
        if ((if (depthOnly) program.depthUsed else program.colorUsed) >= MAX_DRAWS) return null
        val at = next(program, depthOnly)
        val type = (if (depthOnly) program.depthTypes else program.colorTypes)[at]
        val slot = (if (depthOnly) program.depthSlots else program.colorSlots)[at]
        slot.program = program
        slot.programs = programs
        slot.uniforms = upload(list.materialWords, list.materialWord(index, MATERIAL_UNIFORM_AT), list.materialWord(index, MATERIAL_UNIFORM_WORDS))
        lastSlot = slot
        return type
    }
    //?} elif >= 1.21.5 {
    /*private fun prepare(list: NameplateDrawList, index: Int, depthOnly: Boolean): RenderType? {
        val programs = list.materialPrograms[index] ?: return null
        val program = programs.program(list.materialWord(index, MATERIAL_INDEX)) as? Program ?: return null
        if (depthOnly && program.depth == null) return null
        if ((if (depthOnly) program.depthUsed else program.colorUsed) >= MAX_DRAWS) return null
        if (!program.texturesLoaded) loadTextures(program)
        val type = next(program, depthOnly)
        val slot = slots[type] ?: return null
        slot.program = program
        slot.programs = programs
        val at = list.materialWord(index, MATERIAL_UNIFORM_AT)
        val words = list.materialWord(index, MATERIAL_UNIFORM_WORDS)
        //? if >= 1.21.6 {
        slot.uniforms = upload(list.materialWords, at, words)
        //?} else {
        /^if (slot.words.size < words) slot.words = IntArray(words)
        System.arraycopy(list.materialWords, at, slot.words, 0, words)
        ^///?}
        return type
    }
    *///?}

    //? if >= 1.21.6 {
    private fun upload(words: IntArray, at: Int, count: Int): GpuBufferSlice {
        val device = RenderSystem.getDevice()
        val bytes = count * 4
        //? if >= 26.3 {
        val align = maxOf(device.deviceInfo.limits().minUniformOffsetAlignment(), 16).toLong()
        //?} elif >= 26.2 {
        /*val align = maxOf(device.deviceInfo.limits().minUniformOffsetAlignment(), 16)
        *///?} else {
        /*val align = maxOf(device.uniformOffsetAlignment, 16)
        *///?}
        if (staging.capacity() < bytes) {
            MemoryUtil.memFree(staging)
            staging = MemoryUtil.memAlloc(maxOf(bytes, staging.capacity() * 2)).order(ByteOrder.nativeOrder())
        }
        staging.clear()
        for (i in 0 until count) staging.putInt(words[at + i])
        staging.flip()
        var offset = (uniformOffset + align - 1) / align * align
        //? if >= 26.3 {
        if (uniformBuffer < uniformBuffers.size && offset + bytes > uniformBuffers[uniformBuffer].size()) {
        //?} else {
        /*if (uniformBuffer < uniformBuffers.size && offset + bytes > uniformBuffers[uniformBuffer].size().toInt()) {
        *///?}
            uniformBuffer++
            offset = 0
        }
        if (uniformBuffer == uniformBuffers.size) {
            //? if >= 1.21.11 {
            uniformBuffers += device.createBuffer({ "PolyPlus nameplate effects" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, maxOf(UNIFORM_CHUNK, bytes).toLong())
            //?} else {
            /*uniformBuffers += device.createBuffer({ "PolyPlus nameplate effects" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, maxOf(UNIFORM_CHUNK, bytes))
            *///?}
            offset = 0
        }
        //? if >= 26.3 {
        val slice = uniformBuffers[uniformBuffer].slice(offset, bytes.toLong())
        //?} elif >= 1.21.11 {
        /*val buffer = uniformBuffers[uniformBuffer]
        val slice = buffer.slice(offset.toLong(), bytes.toLong())
        *///?} else {
        /*val buffer = uniformBuffers[uniformBuffer]
        val slice = buffer.slice(offset, bytes)
        *///?}
        device.createCommandEncoder().writeToBuffer(slice, staging)
        uniformOffset = offset + bytes
        return slice
    }
    //?}

    //? if >= 26.3 {
    fun submit(collector: SubmitNodeCollector, order: Int, depthOrder: Int, pose: PoseStack, list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, light: Int, alpha: Float) {
        val a = ((list.materialWord(index, MATERIAL_OPACITY) * alpha) + 0.5f).toInt().coerceIn(0, 255)
        if (a == 0) return
        val shader = list.materialPrograms[index]?.shader(list.materialWord(index, MATERIAL_INDEX)) ?: return
        if (a == 255 && shader.depthPrepass) prepare(list, index, true)?.let { submit(collector.order(depthOrder), pose, it, list, index, x, y, z, light, a) }
        val type = prepare(list, index, false) ?: return
        submit(collector.order(order), pose, type, list, index, x, y, z, light, a)
    }
    //?} elif >= 1.21.10 {
    /*fun submit(collector: SubmitNodeCollector, order: Int, depthOrder: Int, pose: PoseStack, list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, light: Int, alpha: Float) {
        val a = opacity(list, index, alpha)
        if (a == 0) return
        val prepass = a == 255 && shaderOf(list, index)?.depthPrepass == true
        if (prepass) prepare(list, index, true)?.let { submit(collector.order(depthOrder), pose, it, list, index, x, y, z, light, a) }
        val type = prepare(list, index, false) ?: return
        submit(collector.order(order), pose, type, list, index, x, y, z, light, a)
    }
    *///?} elif >= 1.21.5 {
    /*fun submit(buffers: MultiBufferSource, pose: Matrix4f, list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, light: Int, alpha: Float) {
        val a = opacity(list, index, alpha)
        if (a == 0) return
        val prepass = a == 255 && shaderOf(list, index)?.depthPrepass == true
        if (prepass) prepare(list, index, true)?.let { emit(buffers.getBuffer(it), pose, list, index, x, y, z, light, a) }
        val type = prepare(list, index, false) ?: return
        emit(buffers.getBuffer(type), pose, list, index, x, y, z, light, a)
    }

    private fun emit(buffer: VertexConsumer, pose: Matrix4f, list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, light: Int, alpha: Int) {
        sink.buffer = buffer
        sink.pose = pose
        sink.z = z
        sink.light = light
        sink.color = (alpha shl 24) or 0xFFFFFF
        NameplateMaterialQuads.emit(list, index, x, y, sink)
        sink.buffer = null
    }
    *///?} elif > 1.8.9 {
    /*fun submit(buffers: MultiBufferSource, pose: Matrix4f, list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, light: Int, alpha: Float) {
        val programs = list.materialPrograms[index] ?: return
        val program = programs.program(list.materialWord(index, MATERIAL_INDEX)) as? Program ?: return
        val a = ((list.materialWord(index, MATERIAL_OPACITY) * alpha) + 0.5f).toInt().coerceIn(0, 255)
        if (a == 0) return
        val words = list.materialWord(index, MATERIAL_UNIFORM_WORDS)
        if (program.shader.depthPrepass && a == 255) draw(buffers, pose, list, index, programs, program, words, true, x, y, z, light, a)
        draw(buffers, pose, list, index, programs, program, words, false, x, y, z, light, a)
    }

    private fun draw(
        buffers: MultiBufferSource,
        pose: Matrix4f,
        list: NameplateDrawList,
        index: Int,
        programs: NameplatePrograms,
        program: Program,
        words: Int,
        depthOnly: Boolean,
        x: Float,
        y: Float,
        z: Float,
        light: Int,
        alpha: Int,
    ) {
        if (used >= MAX_DRAWS) return
        if (used == slots.size) {
            val slot = Slot()
            slots += slot
            types += MaterialType("polyplus_nameplate_material_${slots.size}", slot)
        }
        val slot = slots[used]
        val type = types[used]
        used++
        slot.program = program
        slot.programs = programs
        slot.depthOnly = depthOnly
        if (slot.words.size < words) slot.words = IntArray(words)
        System.arraycopy(list.materialWords, list.materialWord(index, MATERIAL_UNIFORM_AT), slot.words, 0, words)
        sink.buffer = buffers.getBuffer(type)
        sink.pose = pose
        sink.z = z
        sink.light = light
        sink.color = (alpha shl 24) or 0xFFFFFF
        NameplateMaterialQuads.emit(list, index, x, y, sink)
        sink.buffer = null
    }

    private fun setup(slot: Slot) {
        val program = slot.program ?: return
        val instance = program.instance
        //? if >= 1.21.4 {
        RenderSystem.setShader(instance)
        //?} else {
        /^RenderSystem.setShader { instance }
        ^///?}
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer()
        val programs = slot.programs
        for (sampler in program.shader.samplers) RenderSystem.setShaderTexture(sampler.unit, textureId(programs, sampler))
        val loose = program.shader.loose
        val words = slot.words
        for (i in loose.indices) {
            val uniform = loose[i]
            val target = program.targets[i] ?: instance.safeGetUniform(uniform.name).also { program.targets[i] = it }
            val w = uniform.word
            if (uniform.ints) target.set(words[w], words[w + 1], words[w + 2], words[w + 3])
            else target.set(Float.fromBits(words[w]), Float.fromBits(words[w + 1]), Float.fromBits(words[w + 2]), Float.fromBits(words[w + 3]))
        }
        RenderSystem.enableBlend()
        if (program.shader.additive) RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE)
        else RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL_LEQUAL)
        RenderSystem.depthMask(slot.depthOnly)
        if (slot.depthOnly) RenderSystem.colorMask(false, false, false, false)
        RenderSystem.disableCull()
    }

    private fun clear(slot: Slot) {
        if (slot.depthOnly) RenderSystem.colorMask(true, true, true, true)
        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer()
        for (unit in 0 until 12) if (unit != LIGHTMAP_UNIT) RenderSystem.setShaderTexture(unit, 0)
    }

    private fun textureId(programs: NameplatePrograms?, sampler: NameplateSampler): Int {
        val manager = Minecraft.getInstance().textureManager
        return when (sampler.kind) {
            NameplateSampler.KIND_BUNDLE -> (programs?.texture(sampler.index) as? Texture)?.texture?.id ?: 0
            NameplateSampler.KIND_VANILLA -> NameplateVanillaTextures.location(sampler.texture)?.let { manager.getTexture(it).id } ?: 0
            NameplateSampler.KIND_ATLAS -> manager.getTexture(TextureAtlas.LOCATION_BLOCKS).id
            else -> 0
        }
    }
    *///?}

    //? if >= 1.21.10 {
    private fun submit(
        collector: OrderedSubmitNodeCollector,
        pose: PoseStack,
        type: RenderType,
        list: NameplateDrawList,
        index: Int,
        x: Float,
        y: Float,
        z: Float,
        light: Int,
        alpha: Int,
    ) {
        //? if >= 26.3 {
        val slot = lastSlot ?: return
        lastSlot = null
        //?} else {
        /*val slot = slots[type] ?: return
        *///?}
        slot.count = 0
        slot.z = z
        slot.light = light
        slot.color = (alpha shl 24) or 0xFFFFFF
        NameplateMaterialQuads.emit(list, index, x, y, slot)
        if (slot.count > 0) collector.submitCustomGeometry(pose, type, slot)
    }
    //?}

    //? if >= 1.21.5 && < 26.3 {
    /*private fun shaderOf(list: NameplateDrawList, index: Int): NameplateShader? =
        list.materialPrograms[index]?.shader(list.materialWord(index, MATERIAL_INDEX))

    private fun opacity(list: NameplateDrawList, index: Int, alpha: Float): Int =
        ((list.materialWord(index, MATERIAL_OPACITY) * alpha) + 0.5f).toInt().coerceIn(0, 255)
    *///?}

    //? if > 1.8.9 {
    private fun vertexQuad(b: VertexConsumer, m: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, z: Float, color: Int, light: Int) {
        b.addVertex(m, x0, y1, z).setUv(u0, v1).setColor(color).setLight(light)
        b.addVertex(m, x1, y1, z).setUv(u1, v1).setColor(color).setLight(light)
        b.addVertex(m, x1, y0, z).setUv(u1, v0).setColor(color).setLight(light)
        b.addVertex(m, x0, y0, z).setUv(u0, v0).setColor(color).setLight(light)
    }
    //?}

    //? if > 1.8.9 && < 26.3 {
    /*private class Sink : NameplateQuadSink {
        var buffer: VertexConsumer? = null
        var pose: Matrix4f? = null
        var z = 0f
        var light = 0
        var color = -1

        override fun quad(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
            val b = buffer ?: return
            val m = pose ?: return
            vertexQuad(b, m, x0, y0, x1, y1, u0, v0, u1, v1, z, color, light)
        }
    }
    *///?} elif = 1.8.9 {
    /*fun draw(list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, alpha: Float, sneaking: Boolean) {
        val programs = list.materialPrograms[index] ?: return
        val material = list.materialWord(index, MATERIAL_INDEX)
        val program = programs.program(material) as? Program ?: return
        val shader = program.shader
        val opacity = list.materialWord(index, MATERIAL_OPACITY)
        val a = ((opacity * alpha) + 0.5f).toInt().coerceIn(0, 255)
        if (a == 0) return
        val previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val redirect = NameplateShaderPacks.redirecting() && LegacyCeraShaders.managed()
        var narrowed = -1
        GL20.glUseProgram(program.id)
        try {
            if (redirect) narrowed = narrow()
            uniforms(program, list.materialWords, list.materialWord(index, MATERIAL_UNIFORM_AT), redirect)
            bind(programs, shader)
            GlStateManager.disableAlphaTest()
            GlStateManager.enableBlend()
            GlStateManager.enableDepthTest()
            if (shader.depthPrepass && a == 255 && !(redirect && sneaking)) {
                GlStateManager.colorMask(false, false, false, false)
                GlStateManager.depthMask(true)
                quads(list, index, x, y, z, a)
                GlStateManager.colorMask(true, true, true, true)
            }
            GlStateManager.depthMask(redirect && !sneaking && !shader.additive)
            if (shader.additive) GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE)
            else GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            quads(list, index, x, y, z, a)
        } finally {
            unbind(shader)
            if (narrowed >= 0) restoreDrawBuffers(narrowed)
            GL20.glUseProgram(previous)
            if (depthTest) GlStateManager.enableDepthTest() else GlStateManager.disableDepthTest()
            GlStateManager.depthMask(depthMask)
            GlStateManager.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
            GlStateManager.enableAlphaTest()
            GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        }
    }

    private fun narrow(): Int {
        val framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        if (framebuffer == 0) return -1
        var slot = drawBuffers.find(framebuffer)
        val fresh = slot < 0
        if (fresh) {
            GL11.glGetError()
            slot = drawBuffers.claim(framebuffer)
            val at = drawBuffers.offset(slot)
            val count = drawBufferCount
            for (i in 0 until count) drawBuffers.buffers[at + i] = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i)
            drawBuffers.commit(slot, count)
        }
        val target = drawBuffers.target(slot)
        if (target != NameplateDrawBuffers.NONE) GL20.glDrawBuffers(target)
        if (fresh && GL11.glGetError() != GL11.GL_NO_ERROR) {
            if (target != NameplateDrawBuffers.NONE) restoreDrawBuffers(slot)
            NameplateShaderPacks.targetFailed(IllegalStateException("The shader pack's draw buffers could not be narrowed"))
            return -1
        }
        return if (target == NameplateDrawBuffers.NONE) -1 else slot
    }

    private fun restoreDrawBuffers(slot: Int) {
        restoreBuffers.clear()
        restoreBuffers.put(drawBuffers.buffers, drawBuffers.offset(slot), drawBuffers.length(slot))
        restoreBuffers.flip()
        GL20.glDrawBuffers(restoreBuffers)
    }

    private fun quads(list: NameplateDrawList, index: Int, x: Float, y: Float, z: Float, alpha: Int) {
        val tesselator = Tesselator.getInstance()
        sink.z = z.toDouble()
        sink.alpha = alpha
        sink.buffer = tesselator.buffer
        tesselator.buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        NameplateMaterialQuads.emit(list, index, x, y, sink)
        tesselator.end()
    }

    private fun uniforms(program: Program, words: IntArray, at: Int, redirect: Boolean) {
        val loose = program.shader.loose
        for (i in loose.indices) {
            val location = program.loose[i]
            if (location < 0) continue
            val uniform = loose[i]
            val count = minOf(uniform.count, MAX_VECTORS) * 4
            val from = at + uniform.word
            if (uniform.ints) {
                ints.clear()
                for (w in 0 until count) ints.put(words[from + w])
                ints.flip()
                GL20.glUniform4(location, ints)
            } else {
                floats.clear()
                for (w in 0 until count) floats.put(Float.fromBits(words[from + w]))
                floats.flip()
                GL20.glUniform4(location, floats)
            }
        }
        if (program.lit >= 0) GL20.glUniform1i(program.lit, if (lightmapOn()) 1 else 0)
        if (program.fogMode >= 0) GL20.glUniform1i(program.fogMode, if (redirect) 0 else fogMode())
        if (program.screen >= 0) {
            val minecraft = Minecraft.getInstance()
            GL20.glUniform2f(program.screen, minecraft.width.toFloat(), minecraft.height.toFloat())
        }
    }

    private fun bind(programs: NameplatePrograms, shader: NameplateShader) {
        val samplers = shader.samplers
        if (savedTextures.size < samplers.size) savedTextures = IntArray(samplers.size)
        boundSamplers = 0
        for (i in samplers.indices) {
            val sampler = samplers[i]
            GlStateManager.activeTexture(GL13.GL_TEXTURE0)
            val id = when (sampler.kind) {
                NameplateSampler.KIND_BUNDLE -> (programs.texture(sampler.index) as? Texture)?.id ?: 0
                NameplateSampler.KIND_VANILLA -> NameplateVanillaTextures.location(sampler.texture)?.let { glId(it) } ?: 0
                NameplateSampler.KIND_ATLAS -> glId(TextureAtlas.BLOCKS_LOCATION)
                else -> 0
            }
            GlStateManager.activeTexture(GL13.GL_TEXTURE0 + sampler.unit)
            if (sampler.unit == 0) {
                GlStateManager.bindTexture(id)
            } else {
                savedTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
            }
            boundSamplers = i + 1
        }
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
    }

    private fun unbind(shader: NameplateShader) {
        val samplers = shader.samplers
        for (i in boundSamplers - 1 downTo 0) {
            val unit = samplers[i].unit
            if (unit == 0) continue
            GlStateManager.activeTexture(GL13.GL_TEXTURE0 + unit)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTextures[i])
        }
        boundSamplers = 0
    }

    private fun glId(location: Identifier): Int {
        val textures = Minecraft.getInstance().textureManager
        textures.get(location)?.let { return it.glId }
        textures.bind(location)
        return textures.get(location)?.glId ?: 0
    }

    private fun lightmapOn(): Boolean {
        GlStateManager.activeTexture(GL13.GL_TEXTURE0 + LIGHTMAP_UNIT)
        val on = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        return on
    }

    private fun fogMode(): Int {
        if (!GL11.glIsEnabled(GL11.GL_FOG)) return 0
        queries.clear()
        GL11.glGetInteger(GL11.GL_FOG_MODE, queries)
        return when (queries.get(0)) {
            GL_FOG_LINEAR -> 1
            GL_FOG_EXP -> 2
            GL_FOG_EXP2 -> 3
            else -> 0
        }
    }

    private class Sink : NameplateQuadSink {
        var buffer: BufferBuilder? = null
        var z = 0.0
        var alpha = 255

        override fun quad(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
            val b = buffer ?: return
            b.vertex(x0.toDouble(), y0.toDouble(), z).texture(u0.toDouble(), v0.toDouble()).color(255, 255, 255, alpha).nextVertex()
            b.vertex(x0.toDouble(), y1.toDouble(), z).texture(u0.toDouble(), v1.toDouble()).color(255, 255, 255, alpha).nextVertex()
            b.vertex(x1.toDouble(), y1.toDouble(), z).texture(u1.toDouble(), v1.toDouble()).color(255, 255, 255, alpha).nextVertex()
            b.vertex(x1.toDouble(), y0.toDouble(), z).texture(u1.toDouble(), v0.toDouble()).color(255, 255, 255, alpha).nextVertex()
        }
    }
    *///?}
}
