package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.Minecraft
//? if > 1.8.9 {
//? if >= 26.3 {
import com.mojang.blaze3d.ProjectionType
//?}
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.joml.Quaternionf
//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
//?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///?}
//? if >= 26.2 {
import net.minecraft.client.renderer.SubmitNodeStorage
//?}
//? if >= 26.3 {
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.Optional
import java.util.OptionalDouble
//?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.phys.Vec3
//?}
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
*///?}
import kotlin.math.roundToInt

object LobbyHologram {
    const val STEPS = 1023
    private const val SEED_BITS = 10
    private const val SEED_MASK = 15
    private const val PREPASS = 1 shl 14
    private const val ACCENT_SHIFT = 16

    @JvmField
    var drawing = false

    @Volatile
    var accent = 0x1A7CFF

    private fun red() = (accent shr 16) and 0xFF

    private fun green() = (accent shr 8) and 0xFF

    private fun blue() = accent and 0xFF

    //? if > 1.8.9 {
    private fun coded(avatar: LobbyAvatar, prepass: Boolean): Int {
        val low = (avatar.projection * STEPS).roundToInt().coerceIn(0, STEPS) or (seed(avatar) shl SEED_BITS) or if (prepass) PREPASS else 0
        val tint = (red() shr 3 shl 10) or (green() shr 3 shl 5) or (blue() shr 3)
        return low or (tint shl ACCENT_SHIFT)
    }
    //?}

    private fun seed(avatar: LobbyAvatar) = avatar.uuid.hashCode() and SEED_MASK

    fun reset() {
        drawing = false
        //? if >= 1.21.10
        figures = emptyList()
    }

    //? if >= 1.21.10 {
    private class Figure(val avatar: LobbyAvatar, val state: EntityRenderState)

    private var figures = emptyList<Figure>()

    fun extract(avatars: List<LobbyAvatar>, partialTicks: Float) {
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        figures = avatars.filter { it.projecting }.map { avatar ->
            Figure(avatar, dispatcher.extractEntity(avatar, partialTicks).also { it.shadowPieces.clear() })
        }
    }

    private val camera by lazy {
        CameraRenderState().apply {
            orientation = Quaternionf()
            pos = Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = Vec3.ZERO
            *///?}
        }
    }

    private fun figure(mc: Minecraft, figure: Figure, prepass: Boolean) {
        figure.state.lightCoords = coded(figure.avatar, prepass)
        //? if >= 26.3 {
        val storage = SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(figure.state, camera, 0.0, 0.0, 0.0, PoseStack(), storage)
        val target = LobbyCompat.mainTarget(mc.gameRenderer)
        val color = target.colorTextureView ?: return
        mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { frame ->
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "polyplus_hologram" },
                color,
                Optional.empty(),
                target.depthTextureView,
                OptionalDouble.empty(),
            ).use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                FeatureRenderDispatcher.renderAllFeatures(pass, frame)
            }
        }
        //?} elif >= 26.2 {
        /*val storage = SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(figure.state, camera, 0.0, 0.0, 0.0, PoseStack(), storage)
        mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
        *///?} else {
        /*val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(figure.state, camera, 0.0, 0.0, 0.0, PoseStack(), features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        *///?}
    }
    //?} elif > 1.8.9 {
    /*private fun figure(mc: Minecraft, avatar: LobbyAvatar, prepass: Boolean, partialTicks: Float) {
        val dispatcher = mc.entityRenderDispatcher
        val buffers = mc.renderBuffers().bufferSource()
        dispatcher.setRenderShadow(false)
        //? if >= 1.21.4 {
        dispatcher.render(avatar, 0.0, 0.0, 0.0, partialTicks, PoseStack(), buffers, coded(avatar, prepass))
        //?} else {
        /^dispatcher.render(avatar, 0.0, 0.0, 0.0, avatar.yRot, partialTicks, PoseStack(), buffers, coded(avatar, prepass))
        ^///?}
        buffers.endBatch()
        dispatcher.setRenderShadow(true)
    }
    *///?}

    //? if >= 26.3 {
    private val projection by lazy { ProjectionMatrixBuffer("polyplus_hologram") }
    //?}

    //? if > 1.8.9 {
    //? if >= 1.21.10 {
    fun draw(renderer: GameRenderer) {
        val mc = Minecraft.getInstance()
        if (figures.isEmpty()) return
        val visible = figures
    //?} else {
    /*fun draw(renderer: GameRenderer, avatars: List<LobbyAvatar>, partialTicks: Float) {
        val mc = Minecraft.getInstance()
        val visible = avatars.filter { it.projecting }
        if (visible.isEmpty()) return
    *///?}
        val origin = LobbyCompat.cameraPosition(renderer)
        val view = Matrix4f().rotation(LobbyCompat.camera(renderer).rotation().conjugate(Quaternionf()))
        val stack = RenderSystem.getModelViewStack()
        //? if >= 26.3 {
        val scene = LobbyCompat.renderState(renderer).levelRenderState.cameraRenderState
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(projection.getBuffer(scene.projectionMatrix), ProjectionType.PERSPECTIVE)
        //?}
        drawing = true
        try {
            for (figure in visible) {
                //? if >= 1.21.10 {
                val feet = figure.avatar.position().subtract(origin)
                //?} else {
                /*val feet = figure.position().subtract(origin)
                *///?}
                stack.pushMatrix()
                stack.mul(view)
                stack.translate(feet.x.toFloat(), feet.y.toFloat(), feet.z.toFloat())
                //? if < 1.21.4
                /*RenderSystem.applyModelViewMatrix()*/
                try {
                    //? if >= 1.21.10 {
                    figure(mc, figure, true)
                    figure(mc, figure, false)
                    //?} else {
                    /*figure(mc, figure, true, partialTicks)
                    figure(mc, figure, false, partialTicks)
                    *///?}
                } finally {
                    stack.popMatrix()
                    //? if < 1.21.4
                    /*RenderSystem.applyModelViewMatrix()*/
                }
            }
        } finally {
            drawing = false
            //? if >= 26.3 {
            RenderSystem.restoreProjectionMatrix()
            //?}
        }
    }
    //?}

    //? if = 1.8.9 {
    /*private val modelView = BufferUtils.createFloatBuffer(16)

    private fun program(name: String) = LobbyLegacyRender.uniform(name)

    fun draw(avatars: List<LobbyAvatar>, partialTicks: Float, seconds: Float) {
        val visible = avatars.filter { it.projecting }
        if (visible.isEmpty() || !LobbyLegacyRender.bindHologram()) return
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        modelView.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
        val m = FloatArray(16) { modelView.get(it) }
        GL20.glUniform1i(program("Sampler0"), 0)
        GL20.glUniform1f(program("Seconds"), seconds)
        GL20.glUniform1f(program("Daylight"), 1f - LobbySky.mood.night)
        GL20.glUniform3f(program("Accent"), red() / 255f, green() / 255f, blue() / 255f)
        GL20.glUniform3f(program("UpEye"), m[4], m[5], m[6])
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableAlphaTest()
        GlStateManager.disableCull()
        GlStateManager.depthMask(true)
        GlStateManager.enableDepthTest()
        dispatcher.setRenderShadow(false)
        drawing = true
        try {
            for (avatar in visible) {
                val feet = avatar.position().subtract(dispatcher.cameraX, dispatcher.cameraY, dispatcher.cameraZ)
                val x = feet.x.toFloat()
                val y = feet.y.toFloat()
                val z = feet.z.toFloat()
                GL20.glUniform3f(program("FeetEye"), m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14])
                GL20.glUniform1f(program("Progress"), avatar.projection.toFloat())
                GL20.glUniform1f(program("Seed"), seed(avatar) / SEED_MASK.toFloat())
                for (prepass in floatArrayOf(1f, 0f)) {
                    GL20.glUniform1f(program("Prepass"), prepass)
                    GlStateManager.enableBlend()
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    GlStateManager.disableCull()
                    dispatcher.render(avatar, partialTicks)
                }
            }
        } finally {
            drawing = false
            dispatcher.setRenderShadow(true)
            LobbyLegacyRender.unbindHologram()
            GlStateManager.disableBlend()
            GlStateManager.enableAlphaTest()
            GlStateManager.enableCull()
        }
    }
    *///?}
}
