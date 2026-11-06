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

object LobbyPipelines {
    @Volatile
    var previewing = false

    private val solidCompiled by lazy {
        runCatching {
            //? if >= 26.3 {
            RenderSystem.getCompiledPipelineNullable(solid) != null
            //?} else {
            /*RenderSystem.getDevice().precompilePipeline(solid).isValid
            *///?}
        }.getOrDefault(false)
    }

    fun compiled(): Boolean = solidCompiled

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
    fun hologram(original: RenderPipeline): RenderPipeline = if (LobbyHologram.drawing && entityFormat(original)) hologram else original

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
        if (original === RenderPipelines.ENTITY_CUTOUT_NO_CULL && (previewing || LobbyScene.terrainActive())) perFaceEntity else original

    @JvmStatic
    fun lightmap(original: RenderPipeline): RenderPipeline = if (LobbyScene.terrainActive()) lightmap else original
    *///?}

    //? if >= 1.21.8 {
    @JvmStatic
    fun select(layer: ChunkSectionLayer, multiDraw: Boolean, original: RenderPipeline): RenderPipeline {
        if (!LobbyScene.terrainActive()) return original
        return when (layer) {
            ChunkSectionLayer.SOLID -> if (multiDraw) solidMultiDraw else solid
            //? if < 1.21.11
            /*ChunkSectionLayer.CUTOUT_MIPPED -> cutoutMipped*/
            ChunkSectionLayer.CUTOUT -> if (multiDraw) cutoutMultiDraw else cutout
            ChunkSectionLayer.TRANSLUCENT -> if (multiDraw) translucentMultiDraw else translucent
            //? if < 26.1
            /*ChunkSectionLayer.TRIPWIRE -> tripwire*/
        }
    }
    //?} else {
    /*@JvmStatic
    fun select(layer: RenderType, original: RenderPipeline): RenderPipeline {
        if (!LobbyScene.terrainActive()) return original
        return when (layer) {
            RenderType.solid() -> solid
            RenderType.cutoutMipped() -> cutoutMipped
            RenderType.cutout() -> cutout
            RenderType.translucent() -> translucent
            RenderType.tripwire() -> tripwire
            else -> original
        }
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

object LobbyPipelines {
    @Volatile
    var previewing = false

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

    fun compiled(): Boolean = Minecraft.getInstance().shaderManager.getProgram(solid) != null

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
        if (LobbyHologram.drawing && original?.vertexFormat() === DefaultVertexFormat.NEW_ENTITY) return hologram
        val lobby = LobbyScene.terrainActive()
        if (original === CoreShaders.RENDERTYPE_ENTITY_CUTOUT_NO_CULL && (lobby || previewing)) return entity
        if (!lobby) return original
        return when {
            original === CoreShaders.RENDERTYPE_SOLID -> solid
            original === CoreShaders.RENDERTYPE_CUTOUT_MIPPED -> cutoutMipped
            original === CoreShaders.RENDERTYPE_CUTOUT -> cutout
            original === CoreShaders.RENDERTYPE_TRANSLUCENT -> translucent
            original === CoreShaders.RENDERTYPE_TRIPWIRE -> tripwire
            original === CoreShaders.LIGHTMAP -> lightmap
            else -> original
        }
    }
}
*///?} else {
/*import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.Identifier

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

    fun compiled(): Boolean = solid != null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            val id = { name: String -> Identifier.fromNamespaceAndPath("polyplus", name) }
            context.register(id("lobby_solid"), DefaultVertexFormat.BLOCK) { solid = it }
            context.register(id("lobby_cutout_mipped"), DefaultVertexFormat.BLOCK) { cutoutMipped = it }
            context.register(id("lobby_cutout"), DefaultVertexFormat.BLOCK) { cutout = it }
            context.register(id("lobby_translucent"), DefaultVertexFormat.BLOCK) { translucent = it }
            context.register(id("lobby_tripwire"), DefaultVertexFormat.BLOCK) { tripwire = it }
            context.register(id("lobby_entity"), DefaultVertexFormat.NEW_ENTITY) { entity = it }
            context.register(id("lobby_hologram"), DefaultVertexFormat.NEW_ENTITY) { hologram = it }
        }
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
