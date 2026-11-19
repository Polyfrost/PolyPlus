package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 1.21.8 && < 26.1 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.slf4j.LoggerFactory
import java.util.OptionalInt
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer
import net.minecraft.client.renderer.SkyRenderer
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
//? if >= 1.21.11 {
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes
//?} else {
/^import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.util.ARGB
^///?}
//? if >= 1.21.10
import net.minecraft.client.renderer.state.SkyRenderState

internal object LiveSceneGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var failed = false

    private val pipeline: RenderPipeline by lazy {
        val builder = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/live_scene_grade"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/live_scene_grade"))
        //? if >= 1.21.10 {
        builder.withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
        //?} else {
        /^builder.withVertexShader(Identifier.withDefaultNamespace("core/blit_screen"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
        ^///?}
        builder.build()
    }

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val input = source.colorTextureView ?: return false
        val output = destination.colorTextureView ?: return false
        val valid = !failed && runCatching { RenderSystem.getDevice().precompilePipeline(pipeline).isValid }.getOrDefault(false)
        if (!valid) {
            if (!failed) LOG.warn("[live-scene] grade pipeline did not compile, showing the ungraded scene")
            failed = true
            val from = source.colorTexture ?: return false
            val to = destination.colorTexture ?: return false
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from, to, 0, 0, 0, 0, 0, from.getWidth(0), from.getHeight(0))
            return true
        }
        val mood = LobbySky.mood
        val transforms = liveTransform(Matrix4f(), Vector4f(mood.night, mood.exposure, mood.golden, fade), Vector3f())
        RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "polyplus_live_scene_grade" }, output, OptionalInt.empty()).use { pass ->
            pass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform("DynamicTransforms", transforms)
            //? if >= 1.21.11 {
            pass.bindTexture("InSampler", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            //?} else {
            /^pass.bindSampler("InSampler", input)
            ^///?}
            //? if >= 1.21.10 {
            pass.draw(0, GRID_VERTICES)
            //?} else {
            /^val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer())
            pass.setIndexBuffer(indices.getBuffer(QUAD_INDICES), indices.type())
            pass.drawIndexed(0, 0, QUAD_INDICES, 1)
            ^///?}
        }
        return true
    }

    private const val GRID_VERTICES = 3
    private const val QUAD_INDICES = 6
}

internal class LiveSceneSky(private val target: RenderTarget) : AutoCloseable {
    private val mc = Minecraft.getInstance()
    //? if >= 1.21.11 {
    private val sky = SkyRenderer(mc.textureManager, mc.atlasManager)
    //?} else {
    /^private val sky = SkyRenderer()
    ^///?}
    //? if >= 1.21.10
    private val state = SkyRenderState()
    private val projection = CachedPerspectiveProjectionMatrixBuffer("polyplus_live_scene_sky", NEAR, FAR)

    fun owns(other: RenderTarget) = other === target

    fun render(level: ClientLevel, at: Vec3, rotation: Matrix4f, aspect: Float, fov: Float, fog: GpuBufferSlice) {
        //? if >= 1.21.11 {
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
        //?} elif >= 1.21.10 {
        /^sky.extractRenderState(level, 1f, at, state)
        state.shouldRenderDarkDisc = false
        ^///?}

        val savedFog = RenderSystem.getShaderFog()
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(projection.getBuffer(target.width, target.height, fov), ProjectionType.PERSPECTIVE)
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.set(rotation)
        val main = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(target)
        try {
            RenderSystem.setShaderFog(fog)
            val pose = PoseStack()
            //? if >= 1.21.11 {
            sky.renderSkyDisc(state.skyColor)
            sky.renderSunriseAndSunset(pose, state.sunAngle, state.sunriseAndSunsetColor)
            sky.renderSunMoonAndStars(pose, state.sunAngle, state.moonAngle, state.starAngle, state.moonPhase, state.rainBrightness, state.starBrightness)
            //?} elif >= 1.21.10 {
            /^if (state.skyType == DimensionSpecialEffects.SkyType.OVERWORLD) {
                sky.renderSkyDisc(ARGB.redFloat(state.skyColor), ARGB.greenFloat(state.skyColor), ARGB.blueFloat(state.skyColor))
                if (state.isSunriseOrSunset) sky.renderSunriseAndSunset(pose, state.sunAngle, state.sunriseAndSunsetColor)
                sky.renderSunMoonAndStars(pose, state.timeOfDay, state.moonPhase, state.rainBrightness, state.starBrightness)
            }
            ^///?} else {
            /^val effects = level.effects()
            if (effects.skyType() == DimensionSpecialEffects.SkyType.OVERWORLD) {
                val timeOfDay = level.getTimeOfDay(1f)
                val rain = 1f - level.getRainLevel(1f)
                val color = level.getSkyColor(at, 1f)
                val buffers = mc.renderBuffers().bufferSource()
                sky.renderSkyDisc(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color))
                if (effects.isSunriseOrSunset(timeOfDay)) {
                    sky.renderSunriseAndSunset(pose, buffers, level.getSunAngle(1f), effects.getSunriseOrSunsetColor(timeOfDay))
                }
                sky.renderSunMoonAndStars(pose, buffers, timeOfDay, level.moonPhase, rain, level.getStarBrightness(1f) * rain)
                buffers.endBatch()
            }
            ^///?}
        } finally {
            (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(main)
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
            savedFog?.let(RenderSystem::setShaderFog)
        }
    }

    override fun close() {
        sky.close()
        projection.close()
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 1024f
    }
}

//? if >= 1.21.11 {
internal object LiveSceneAtmosphere {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> adjusted(attribute: EnvironmentAttribute<T>, value: T): T =
        runCatching { LobbySky.adjust(attribute, value) as T }.getOrDefault(value)
}
//?}
*///?}
