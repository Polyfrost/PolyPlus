package org.polyfrost.polyplus.client.gui.onboarding.live

//? if = 1.21.5 {
/*import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.client.renderer.FogParameters
import net.minecraft.client.renderer.SkyRenderer
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.slf4j.LoggerFactory
import java.util.OptionalInt

internal object LiveSceneGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var failed = false

    private val pipeline: RenderPipeline by lazy {
        RenderPipeline.builder()
            .withUniform("ColorModulator", UniformType.VEC4)
            .withSampler("InSampler")
            .withLocation(Identifier.fromNamespaceAndPath("polyplus", "pipeline/live_scene_grade"))
            .withVertexShader(Identifier.withDefaultNamespace("core/blit_screen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("polyplus", "core/live_scene_grade"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .build()
    }

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val input = source.colorTexture ?: return false
        val output = destination.colorTexture ?: return false
        val valid = !failed && runCatching { RenderSystem.getDevice().precompilePipeline(pipeline).isValid }.getOrDefault(false)
        if (!valid) {
            if (!failed) LOG.warn("[live-scene] grade pipeline did not compile, showing the ungraded scene")
            failed = true
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(input, output, 0, 0, 0, 0, 0, input.getWidth(0), input.getHeight(0))
            return true
        }
        val mood = LobbySky.mood
        val indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(output, OptionalInt.empty()).use { pass ->
            pass.setPipeline(pipeline)
            pass.bindSampler("InSampler", input)
            pass.setUniform("ColorModulator", mood.night, mood.exposure, mood.golden, fade)
            pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer())
            pass.setIndexBuffer(indices.getBuffer(QUAD_INDICES), indices.type())
            pass.drawIndexed(0, QUAD_INDICES)
        }
        return true
    }

    private const val QUAD_INDICES = 6
}

internal class LiveSceneSky(private val target: RenderTarget) : AutoCloseable {
    private val mc = Minecraft.getInstance()
    private val sky = SkyRenderer()

    fun owns(other: RenderTarget) = other === target

    fun render(level: ClientLevel, at: Vec3, rotation: Matrix4f, aspect: Float, fov: Float, fog: FogParameters) {
        val effects = level.effects()
        if (effects.skyType() != DimensionSpecialEffects.SkyType.OVERWORLD) return
        val savedFog = RenderSystem.getShaderFog()
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(
            Matrix4f().setPerspective(Math.toRadians(fov.toDouble()).toFloat(), aspect, NEAR, FAR),
            ProjectionType.PERSPECTIVE,
        )
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.set(rotation)
        val main = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(target)
        try {
            RenderSystem.setShaderFog(fog)
            val pose = PoseStack()
            val timeOfDay = level.getTimeOfDay(1f)
            val rain = 1f - level.getRainLevel(1f)
            val color = level.getSkyColor(at, 1f)
            val buffers = mc.renderBuffers().bufferSource()
            sky.renderSkyDisc(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color))
            if (effects.isSunriseOrSunset(timeOfDay)) {
                sky.renderSunriseAndSunset(pose, buffers, level.getSunAngle(1f), effects.getSunriseOrSunsetColor(timeOfDay))
            }
            sky.renderSunMoonAndStars(pose, buffers, timeOfDay, level.moonPhase, rain, level.getStarBrightness(1f) * rain, fog)
            buffers.endBatch()
        } finally {
            (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(main)
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
            RenderSystem.setShaderFog(savedFog)
        }
    }

    override fun close() {
        sky.close()
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 1024f
    }
}
*///?}
