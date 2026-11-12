package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
//? if >= 1.21.5 {
//? if >= 26.3 {
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
//?} elif >= 26.1 {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
*///?} else {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
*///?}
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
//? if >= 26.2 {
import net.minecraft.client.renderer.BindGroupLayouts
//?} else {
/*import com.mojang.blaze3d.shaders.UniformType
*///?}
//? if < 1.21.10 {
/*import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
*///?}
//? if >= 1.21.8 {
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.mixin.client.access.RenderPipelinesAccessor
import java.util.concurrent.ConcurrentHashMap

object LobbyPipelines {
    @Volatile
    var previewing = false

    private val failed = ConcurrentHashMap.newKeySet<RenderPipeline>()

    private fun usable(pipeline: RenderPipeline): Boolean {
        if (pipeline in failed) return false
        if (!RenderSystem.isOnRenderThread()) return true
        val result = runCatching {
            //? if >= 26.3 {
            RenderSystem.getCompiledPipelineNullable(pipeline) != null
            //?} else {
            /*RenderSystem.getDevice().precompilePipeline(pipeline).isValid
            *///?}
        }
        if (result.getOrDefault(false)) return true
        if (failed.add(pipeline)) LOBBY_LOGGER.warn("Lobby pipeline {} failed to compile, falling back to vanilla rendering for it", pipeline.location, result.exceptionOrNull())
        return false
    }

    private fun pick(lobby: RenderPipeline, original: RenderPipeline): RenderPipeline = if (usable(lobby)) lobby else original

    fun compiled(): Boolean = terrain.all(::usable)

    private val SHADER = Identifier.fromNamespaceAndPath("polyplus", "core/lobby_terrain")
    private val HOLOGRAM = Identifier.fromNamespaceAndPath("polyplus", "core/lobby_hologram")

    private fun pipeline(name: String, multiDraw: Boolean, cutout: Float?, translucent: Boolean): RenderPipeline {
        //? if >= 26.3 {
        val snippet = if (multiDraw) RenderPipelinesAccessor.`polyplus$multidrawTerrainSnippet`() else RenderPipelinesAccessor.`polyplus$terrainSnippet`()
        //?} else {
        /*val snippet = RenderPipelinesAccessor.`polyplus$terrainSnippet`()
        *///?}
        val builder = RenderPipeline.builder(snippet)
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/lobby_$name"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
        //? if >= 26.1 {
        builder.withColorTargetState(if (translucent) ColorTargetState(BlendFunction.TRANSLUCENT) else ColorTargetState.DEFAULT)
        //?} else {
        /*if (translucent) builder.withBlend(BlendFunction.TRANSLUCENT)
        *///?}
        //? if < 1.21.8 {
        /*builder.withUniform("GameTime", UniformType.FLOAT).withUniform("LobbyAim", UniformType.FLOAT)
        *///?}
        cutout?.let { builder.withShaderDefine("ALPHA_CUTOUT", it) }
        return builder.build()
    }

    private val solid by lazy { pipeline("solid", false, null, false) }
    private val solidMultiDraw by lazy { pipeline("solid_multidraw", true, null, false) }
    //? if >= 1.21.11 {
    private val cutout by lazy { pipeline("cutout", false, 0.5f, false) }
    //?} else {
    /*private val cutout by lazy { pipeline("cutout", false, 0.1f, false) }
    private val cutoutMipped by lazy { pipeline("cutout_mipped", false, 0.5f, false) }
    *///?}
    private val cutoutMultiDraw by lazy { pipeline("cutout_multidraw", true, 0.5f, false) }
    private val translucent by lazy { pipeline("translucent", false, 0.1f, true) }
    private val translucentMultiDraw by lazy { pipeline("translucent_multidraw", true, 0.1f, true) }
    //? if < 26.1
    /*private val tripwire by lazy { pipeline("tripwire", false, 0.1f, true) }*/

    private val terrain by lazy {
        listOf(
            solid,
            //? if < 1.21.11
            /*cutoutMipped,*/
            cutout,
            translucent,
            //? if < 26.1
            /*tripwire,*/
        )
    }

    private val hologram by lazy {
        val builder = RenderPipeline.builder(RenderPipelinesAccessor.`polyplus$entitySnippet`())
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/lobby_hologram"))
            .withVertexShader(HOLOGRAM)
            .withFragmentShader(HOLOGRAM)
            .withCull(false)
        //? if >= 26.2 {
        builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        //?} else {
        /*builder.withSampler("Sampler1")
        *///?}
        //? if >= 26.1 {
        builder.withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
        //?} else {
        /*builder.withBlend(BlendFunction.TRANSLUCENT)
        *///?}
        //? if >= 1.21.8 && < 26.2 {
        /*builder.withUniform("Globals", UniformType.UNIFORM_BUFFER)
        *///?} elif < 1.21.8 {
        /*builder.withUniform("GameTime", UniformType.FLOAT)
        *///?}
        builder.build()
    }

    //? if >= 26.2 {
    private fun entityFormat(pipeline: RenderPipeline) = pipeline.getVertexFormatBinding(0) === DefaultVertexFormat.ENTITY
    //?} elif >= 26.1 {
    /*private fun entityFormat(pipeline: RenderPipeline) = pipeline.vertexFormat === DefaultVertexFormat.ENTITY
    *///?} else {
    /*private fun entityFormat(pipeline: RenderPipeline) = pipeline.vertexFormat === DefaultVertexFormat.NEW_ENTITY
    *///?}

    @JvmStatic
    fun hologram(original: RenderPipeline): RenderPipeline = if (LobbyHologram.drawing && entityFormat(original) && usable(hologram)) hologram else original

    //? if < 1.21.10 {
    /*private val perFaceEntity by lazy {
        RenderPipeline.builder(RenderPipelinesAccessor.`polyplus$entitySnippet`())
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/lobby_entity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("polyplus", "core/lobby_entity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/lobby_entity"))
            .withSampler("Sampler1")
            .withCull(false)
            .build()
    }

    private val lightmap by lazy {
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/lobby_lightmap"))
            .withVertexShader(Identifier.withDefaultNamespace("core/blit_screen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/lobby_lightmap"))
            //? if >= 1.21.8 {
            .withUniform("LightmapInfo", UniformType.UNIFORM_BUFFER)
            //?} else {
            /^.withUniform("SkyFactor", UniformType.FLOAT)
            .withUniform("BlockFactor", UniformType.FLOAT)
            .withUniform("SkyLightColor", UniformType.VEC3)
            .withUniform("DarknessScale", UniformType.FLOAT)
            .withUniform("DarkenWorldFactor", UniformType.FLOAT)
            .withUniform("BrightnessFactor", UniformType.FLOAT)
            .withUniform("LobbyNight", UniformType.VEC4)
            ^///?}
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    }

    @JvmStatic
    fun entity(original: RenderPipeline): RenderPipeline =
        if (original === RenderPipelines.ENTITY_CUTOUT_NO_CULL && (previewing || LobbyScene.terrainActive())) pick(perFaceEntity, original) else original

    @JvmStatic
    fun lightmap(original: RenderPipeline): RenderPipeline = if (LobbyScene.terrainActive() && LobbySky.mood.realm == LobbyRealm.OVERWORLD) pick(lightmap, original) else original
    *///?}

    //? if >= 1.21.8 {
    @JvmStatic
    fun select(layer: ChunkSectionLayer, multiDraw: Boolean, original: RenderPipeline): RenderPipeline {
        if (!LobbyScene.terrainActive()) return original
        val lobby = when (layer) {
            ChunkSectionLayer.SOLID -> if (multiDraw) solidMultiDraw else solid
            //? if < 1.21.11
            /*ChunkSectionLayer.CUTOUT_MIPPED -> cutoutMipped*/
            ChunkSectionLayer.CUTOUT -> if (multiDraw) cutoutMultiDraw else cutout
            ChunkSectionLayer.TRANSLUCENT -> if (multiDraw) translucentMultiDraw else translucent
            //? if < 26.1
            /*ChunkSectionLayer.TRIPWIRE -> tripwire*/
        }
        return pick(lobby, original)
    }
    //?} else {
    /*@JvmStatic
    fun select(layer: RenderType, original: RenderPipeline): RenderPipeline {
        if (!LobbyScene.terrainActive()) return original
        val lobby = when (layer) {
            RenderType.solid() -> solid
            RenderType.cutoutMipped() -> cutoutMipped
            RenderType.cutout() -> cutout
            RenderType.translucent() -> translucent
            RenderType.tripwire() -> tripwire
            else -> return original
        }
        return pick(lobby, original)
    }
    *///?}
}
//?} elif >= 1.21.4 {
/*import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.client.renderer.ShaderDefines
import net.minecraft.client.renderer.ShaderProgram
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

object LobbyPipelines {
    @Volatile
    var previewing = false

    private val failed = ConcurrentHashMap.newKeySet<ShaderProgram>()

    @JvmStatic
    fun ownsProgram(program: ShaderProgram): Boolean = program.configId().namespace == "polyplus"

    private fun usable(program: ShaderProgram): Boolean {
        if (program in failed) return false
        if (!RenderSystem.isOnRenderThread()) return true
        val result = runCatching { Minecraft.getInstance().shaderManager.getProgram(program) != null }
        if (result.getOrDefault(false)) return true
        if (failed.add(program)) LOBBY_LOGGER.warn("Lobby shader {} failed to compile, falling back to vanilla rendering for it", program.configId(), result.exceptionOrNull())
        return false
    }

    private fun pick(lobby: ShaderProgram, original: ShaderProgram?): ShaderProgram? = if (usable(lobby)) lobby else original

    private fun program(name: String, format: VertexFormat) =
        ShaderProgram(Identifier.fromNamespaceAndPath("polyplus", "core/$name"), format, ShaderDefines.EMPTY)

    private val solid = program("lobby_solid", DefaultVertexFormat.BLOCK)
    private val cutoutMipped = program("lobby_cutout_mipped", DefaultVertexFormat.BLOCK)
    private val cutout = program("lobby_cutout", DefaultVertexFormat.BLOCK)
    private val translucent = program("lobby_translucent", DefaultVertexFormat.BLOCK)
    private val tripwire = program("lobby_tripwire", DefaultVertexFormat.BLOCK)
    private val lightmap = program("lobby_lightmap", DefaultVertexFormat.BLIT_SCREEN)
    private val entity = program("lobby_entity", DefaultVertexFormat.NEW_ENTITY)
    private val hologram = program("lobby_hologram", DefaultVertexFormat.NEW_ENTITY)

    fun compiled(): Boolean = listOf(solid, cutoutMipped, cutout, translucent, tripwire).all(::usable)

    @JvmStatic
    fun hologramState() {
        if (!LobbyHologram.drawing) return
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.depthMask(true)
    }

    @JvmStatic
    fun shader(original: ShaderProgram?): ShaderProgram? {
        if (LobbyHologram.drawing && original?.vertexFormat() === DefaultVertexFormat.NEW_ENTITY) return pick(hologram, original)
        val lobby = LobbyScene.terrainActive()
        if (original === CoreShaders.RENDERTYPE_ENTITY_CUTOUT_NO_CULL && (lobby || previewing)) return pick(entity, original)
        if (!lobby) return original
        val swapped = when {
            original === CoreShaders.RENDERTYPE_SOLID -> solid
            original === CoreShaders.RENDERTYPE_CUTOUT_MIPPED -> cutoutMipped
            original === CoreShaders.RENDERTYPE_CUTOUT -> cutout
            original === CoreShaders.RENDERTYPE_TRANSLUCENT -> translucent
            original === CoreShaders.RENDERTYPE_TRIPWIRE -> tripwire
            original === CoreShaders.LIGHTMAP -> if (LobbySky.mood.realm == LobbyRealm.OVERWORLD) lightmap else return original
            else -> return original
        }
        return pick(swapped, original)
    }
}
*///?} else {
/*import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL20
import org.polyfrost.polyplus.compat.Mods

object LobbyPipelines {
    @Volatile
    var previewing = false

    private var solid: ShaderInstance? = null
    private var cutoutMipped: ShaderInstance? = null
    private var cutout: ShaderInstance? = null
    private var translucent: ShaderInstance? = null
    private var tripwire: ShaderInstance? = null
    private var entity: ShaderInstance? = null
    private var hologram: ShaderInstance? = null

    private val failed = mutableSetOf<String>()

    fun compiled(): Boolean = solid != null && cutoutMipped != null && cutout != null && translucent != null && tripwire != null

    fun register() {
        // VulkanMod can't compile these shaders, and the GL link check aborts the JVM without a GL context
        if (Mods.vulkanMod) return
        CoreShaderRegistrationCallback.EVENT.register { context ->
            solid = null
            cutoutMipped = null
            cutout = null
            translucent = null
            tripwire = null
            entity = null
            hologram = null
            val load = { name: String, format: VertexFormat, assign: (ShaderInstance) -> Unit ->
                if (name !in failed) {
                    runCatching {
                        context.register(Identifier.fromNamespaceAndPath("polyplus", name), format) { if (linked(name, it)) assign(it) }
                    }.onFailure { fail(name, it) }
                }
            }
            load("lobby_solid", DefaultVertexFormat.BLOCK) { solid = it }
            load("lobby_cutout_mipped", DefaultVertexFormat.BLOCK) { cutoutMipped = it }
            load("lobby_cutout", DefaultVertexFormat.BLOCK) { cutout = it }
            load("lobby_translucent", DefaultVertexFormat.BLOCK) { translucent = it }
            load("lobby_tripwire", DefaultVertexFormat.BLOCK) { tripwire = it }
            load("lobby_entity", DefaultVertexFormat.NEW_ENTITY) { entity = it }
            load("lobby_hologram", DefaultVertexFormat.NEW_ENTITY) { hologram = it }
        }
    }

    private fun linked(name: String, shader: ShaderInstance): Boolean {
        if (GlStateManager.glGetProgrami(shader.id, GL20.GL_LINK_STATUS) != 0) return true
        fail(name, null)
        return false
    }

    private fun fail(name: String, error: Throwable?) {
        if (failed.add(name)) LOBBY_LOGGER.warn("Lobby shader {} failed to load, falling back to vanilla rendering for it", name, error)
    }

    private fun scene(shader: ShaderInstance?, original: ShaderInstance?) = if (LobbyScene.terrainActive()) shader ?: original else original

    @JvmStatic
    fun solid(original: ShaderInstance?) = scene(solid, original)

    @JvmStatic
    fun cutoutMipped(original: ShaderInstance?) = scene(cutoutMipped, original)

    @JvmStatic
    fun cutout(original: ShaderInstance?) = scene(cutout, original)

    @JvmStatic
    fun translucent(original: ShaderInstance?) = scene(translucent, original)

    @JvmStatic
    fun tripwire(original: ShaderInstance?) = scene(tripwire, original)

    @JvmStatic
    fun entity(original: ShaderInstance?) = when {
        LobbyHologram.drawing -> hologram ?: original
        previewing || LobbyScene.terrainActive() -> entity ?: original
        else -> original
    }

    @JvmStatic
    fun hologram(original: ShaderInstance?) = if (LobbyHologram.drawing) hologram ?: original else original

    @JvmStatic
    fun hologramState() {
        if (!LobbyHologram.drawing) return
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.depthMask(true)
    }
}
*///?}
//?}
