package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 26.1 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.Identifier
import net.minecraft.world.attribute.EnvironmentAttribute
import org.joml.Matrix4f
import org.joml.Vector4f
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.slf4j.LoggerFactory
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.FilterMode
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.FilterMode
import org.joml.Vector3f
*///?}
//? if >= 26.1 {
import com.mojang.blaze3d.ProjectionType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.SkyRenderer
import net.minecraft.client.renderer.state.level.SkyRenderState
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.phys.Vec3
//?}
//? if >= 26.2 {
import net.minecraft.client.renderer.BindGroupLayouts
import java.util.Optional
//?} else {
/*import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import java.util.OptionalInt
*///?}
//? if = 26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology
*///?}
//? if < 26.3 {
/*import com.mojang.blaze3d.vertex.PoseStack
*///?}
//? if < 26.2 {
/*import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}

internal object LiveSceneGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var failed = false

    private val pipeline: RenderPipeline by lazy {
        //? if >= 26.2 {
        RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/live_scene_grade"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/live_scene_grade"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build()
        //?} else {
        /*RenderPipeline.builder()
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/live_scene_grade"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/live_scene_grade"))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build()
        *///?}
    }

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val input = source.colorTextureView ?: return false
        val output = destination.colorTextureView ?: return false
        //? if >= 26.3 {
        val compiled = if (failed) null else runCatching { RenderSystem.getCompiledPipelineNullable(pipeline) }.getOrNull()
        //?} else {
        /*val compiled = if (failed) null else runCatching { pipeline.takeIf { RenderSystem.getDevice().precompilePipeline(it).isValid } }.getOrNull()
        *///?}
        if (compiled == null) {
            if (!failed) LOG.warn("[live-scene] grade pipeline did not compile, showing the ungraded scene")
            failed = true
            val from = source.colorTexture ?: return false
            val to = destination.colorTexture ?: return false
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from, to, 0, 0, 0, 0, 0, from.getWidth(0), from.getHeight(0))
            return true
        }
        val mood = LobbySky.mood
        //? if >= 26.3 {
        val transforms = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(), Vector4f(mood.night, mood.exposure, mood.golden, fade))
        RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "polyplus_live_scene_grade" }, output, Optional.empty()).use { pass ->
            pass.setPipeline(compiled)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", transforms)
            pass.setUniform("InSampler", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            pass.draw(GRID_VERTICES, 1, 0, 0)
        }
        //?} else {
        /*val transforms = RenderSystem.getDynamicUniforms().writeTransform(Matrix4f(), Vector4f(mood.night, mood.exposure, mood.golden, fade), Vector3f(), Matrix4f())
        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "polyplus_live_scene_grade" }, output, Optional.empty()).use { pass ->
        //?} else {
        /^RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "polyplus_live_scene_grade" }, output, OptionalInt.empty()).use { pass ->
        ^///?}
            pass.setPipeline(compiled)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", transforms)
            pass.bindTexture("InSampler", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            //? if >= 26.2 {
            pass.draw(GRID_VERTICES, 1, 0, 0)
            //?} else {
            /^pass.draw(0, GRID_VERTICES)
            ^///?}
        }
        *///?}
        return true
    }

    private const val GRID_VERTICES = 3
}

//? if >= 26.1 {
internal class LiveSceneSky(private val target: RenderTarget) : AutoCloseable {
    private val mc = Minecraft.getInstance()
    //? if >= 26.2 {
    private val sky = SkyRenderer(mc.textureManager, mc.atlasManager, target)
    //?} else {
    /*private val sky = SkyRenderer(mc.textureManager, mc.atlasManager)
    *///?}
    private val state = SkyRenderState()
    private val projection = Projection()
    private val projectionBuffer = ProjectionMatrixBuffer("polyplus_live_scene_sky")

    fun owns(other: RenderTarget) = other === target

    fun render(level: ClientLevel, at: Vec3, rotation: Matrix4f, aspect: Float, fov: Float, fog: GpuBufferSlice) {
        val attributes = level.environmentAttributes()
        state.reset()
        state.skybox = level.dimensionType().skybox()
        state.sunAngle = Math.toRadians(attributes.getValue(EnvironmentAttributes.SUN_ANGLE, at, null).toDouble()).toFloat()
        state.moonAngle = Math.toRadians(attributes.getValue(EnvironmentAttributes.MOON_ANGLE, at, null).toDouble()).toFloat()
        state.starAngle = Math.toRadians(attributes.getValue(EnvironmentAttributes.STAR_ANGLE, at, null).toDouble()).toFloat()
        state.rainBrightness = 1f - level.getRainLevel(1f)
        state.starBrightness = attributes.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, at, null)
        state.sunriseAndSunsetColor = attributes.getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, at, null)
        state.moonPhase = attributes.getValue(EnvironmentAttributes.MOON_PHASE, at, null)
        state.skyColor = LiveSceneAtmosphere.adjusted(EnvironmentAttributes.SKY_COLOR, attributes.getValue(EnvironmentAttributes.SKY_COLOR, at, null))
        state.shouldRenderDarkDisc = false

        projection.setupPerspective(NEAR, FAR, fov, aspect, 1f)
        val savedFog = RenderSystem.getShaderFog()
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE)
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.set(rotation)
        //? if < 26.2 {
        /*val main = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(target)
        *///?}
        try {
            //? if >= 26.3 {
            sky.render(fog, state)
            //?} else {
            /*RenderSystem.setShaderFog(fog)
            val pose = PoseStack()
            sky.renderSkyDisc(state.skyColor)
            sky.renderSunriseAndSunset(pose, state.sunAngle, state.sunriseAndSunsetColor)
            sky.renderSunMoonAndStars(pose, state.sunAngle, state.moonAngle, state.starAngle, state.moonPhase, state.rainBrightness, state.starBrightness)
            *///?}
        } finally {
            //? if < 26.2 {
            /*(mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(main)
            *///?}
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
            savedFog?.let(RenderSystem::setShaderFog)
        }
    }

    override fun close() {
        sky.close()
        projectionBuffer.close()
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 1024f
    }
}
//?}

internal object LiveSceneAtmosphere {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> adjusted(attribute: EnvironmentAttribute<T>, value: T): T =
        runCatching { LobbySky.adjust(attribute, value) as T }.getOrDefault(value)
}
//?}
