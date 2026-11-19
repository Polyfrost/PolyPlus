package org.polyfrost.polyplus.client.gui.onboarding.live

//? if < 1.21.5 && > 1.8.9 {
/*import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.slf4j.LoggerFactory
//? if >= 1.21.4 {
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.client.renderer.FogParameters
import net.minecraft.client.renderer.ShaderDefines
import net.minecraft.client.renderer.ShaderProgram
import net.minecraft.client.renderer.SkyRenderer
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
//?} else {
/^import com.mojang.blaze3d.platform.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
^///?}

//? if >= 1.21.4 {
internal object LiveSceneGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var failed = false

    private val program = ShaderProgram(Identifier.fromNamespaceAndPath("polyplus", "core/live_scene_grade"), DefaultVertexFormat.BLIT_SCREEN, ShaderDefines.EMPTY)

    private fun grade(): LiveShader? = Minecraft.getInstance().shaderManager.getProgram(program)

    private fun copy(): LiveShader? = Minecraft.getInstance().shaderManager.getProgram(CoreShaders.BLIT_SCREEN)

    private val quad: VertexBuffer by lazy { liveQuad() }

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val graded = if (failed) null else runCatching { grade() }.onFailure { LOG.warn("[live-scene] grade shader error", it) }.getOrNull()
        if (graded == null && !failed) {
            LOG.warn("[live-scene] grade shader did not load, showing the ungraded scene")
            failed = true
        }
        val shader = graded ?: copy()
        if (shader == null) {
            destination.bindWrite(true)
            source.blitToScreen(destination.width, destination.height)
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
            return true
        }
        val mood = LobbySky.mood
        if (graded != null) shader.safeGetUniform("ColorModulator").set(mood.night, mood.exposure, mood.golden, fade)
        shader.bindSampler("InSampler", source.colorTextureId)
        destination.bindWrite(true)
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableBlend()
        try {
            quad.bind()
            quad.drawWithShader(Matrix4f(), Matrix4f(), shader)
        } finally {
            VertexBuffer.unbind()
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
        }
        return true
    }
}
//?} else {
/^internal object LiveSceneGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var failed = false
    private var program = 0
    private val quad: VertexBuffer by lazy { liveQuad() }

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val shader = if (failed) 0 else runCatching { program() }.onFailure {
            LOG.warn("[live-scene] grade shader did not load, showing the ungraded scene", it)
            failed = true
        }.getOrDefault(0)
        destination.bindWrite(true)
        if (shader == 0) {
            source.blitToScreen(destination.width, destination.height, true)
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
            return true
        }
        val mood = LobbySky.mood
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableBlend()
        GlStateManager._glUseProgram(shader)
        GL20.glUniform1i(GL20.glGetUniformLocation(shader, "InSampler"), 0)
        GL20.glUniform4f(GL20.glGetUniformLocation(shader, "ColorModulator"), mood.night, mood.exposure, mood.golden, fade)
        RenderSystem.activeTexture(GL13.GL_TEXTURE0)
        RenderSystem.bindTexture(source.colorTextureId)
        try {
            quad.bind()
            quad.draw()
        } finally {
            VertexBuffer.unbind()
            Minecraft.getInstance().gameRenderer.blitShader?.clear()
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
        }
        return true
    }

    private fun program(): Int {
        if (program != 0) return program
        val id = GL20.glCreateProgram()
        GL20.glAttachShader(id, compile(GL20.GL_VERTEX_SHADER, "/assets/minecraft/shaders/core/blit_screen.vsh"))
        GL20.glAttachShader(id, compile(GL20.GL_FRAGMENT_SHADER, "/assets/polyplus/shaders/core/live_scene_grade.fsh"))
        GL20.glBindAttribLocation(id, 0, "Position")
        GL20.glLinkProgram(id)
        check(GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) { GL20.glGetProgramInfoLog(id) }
        program = id
        return id
    }

    private fun compile(type: Int, path: String): Int {
        val source = checkNotNull(LiveSceneGrade::class.java.getResourceAsStream(path)) { path }.use { it.readBytes().decodeToString() }
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, source)
        GL20.glCompileShader(id)
        check(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) { GL20.glGetShaderInfoLog(id) }
        return id
    }
}
^///?}

internal class LiveSceneSky(private val target: RenderTarget) : AutoCloseable {
    private val mc = Minecraft.getInstance()
    //? if >= 1.21.4
    private val sky = SkyRenderer()

    fun owns(other: RenderTarget) = other === target

    //? if >= 1.21.4 {
    fun render(level: ClientLevel, at: Vec3, rotation: Matrix4f, aspect: Float, fov: Float, fog: FogParameters) {
        val effects = level.effects()
        if (effects.skyType() != DimensionSpecialEffects.SkyType.OVERWORLD) return
        val savedFog = RenderSystem.getShaderFog()
        RenderSystem.backupProjectionMatrix()
        liveProjection(Matrix4f().setPerspective(Math.toRadians(fov.toDouble()).toFloat(), aspect, NEAR, FAR))
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.set(rotation)
        val main = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(target)
        target.bindWrite(true)
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
            main.bindWrite(true)
            modelView.popMatrix()
            RenderSystem.restoreProjectionMatrix()
            RenderSystem.setShaderFog(savedFog)
        }
    }
    //?} else {
    /^fun render(level: ClientLevel, at: Vec3, rotation: Matrix4f, aspect: Float, fov: Float, fog: LiveFog) {
        val saved = LiveFog.current()
        val projection = Matrix4f().setPerspective(Math.toRadians(fov.toDouble()).toFloat(), aspect, NEAR, FAR)
        target.bindWrite(true)
        try {
            fog.apply()
            mc.levelRenderer.renderSky(rotation, projection, 1f, mc.gameRenderer.mainCamera, false) { fog.apply() }
        } finally {
            saved.apply()
            mc.mainRenderTarget.bindWrite(true)
        }
    }
    ^///?}

    override fun close() {
        //? if >= 1.21.4
        sky.close()
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 1024f
    }
}
*///?}
