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
//?} else {
/*import net.minecraft.world.entity.Entity
*///?}
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.entity.Entity
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

    @JvmField
    var extracting = false

    @Volatile
    var accent = 0x1A7CFF

    private fun red(tint: Int) = (tint shr 16) and 0xFF

    private fun green(tint: Int) = (tint shr 8) and 0xFF

    private fun blue(tint: Int) = tint and 0xFF

    //? if > 1.8.9 {
    private fun coded(avatar: LobbyAvatar, prepass: Boolean): Int = code(avatar.projection, seed(avatar), prepass, accent)

    private fun code(progress: Double, seed: Int, prepass: Boolean, tint: Int): Int {
        val low = (progress * STEPS).roundToInt().coerceIn(0, STEPS) or (seed shl SEED_BITS) or if (prepass) PREPASS else 0
        val packed = (red(tint) shr 3 shl 10) or (green(tint) shr 3 shl 5) or (blue(tint) shr 3)
        return low or (packed shl ACCENT_SHIFT)
    }
    //?}

    private fun seed(avatar: LobbyAvatar) = avatar.uuid.hashCode() and SEED_MASK

    fun reset() {
        drawing = false
        extracting = false
        //? if >= 1.21.10
        figures.clear()
    }

    //? if >= 1.21.10 {
    private class Figure(val avatar: LobbyAvatar, val state: EntityRenderState)

    private val figures = ArrayList<Figure>()

    fun extract(avatars: List<LobbyAvatar>, partialTicks: Float) {
        figures.clear()
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        extracting = true
        try {
            for (avatar in avatars) {
                if (!avatar.projecting) continue
                figures += Figure(avatar, dispatcher.extractEntity(avatar, partialTicks).also { it.shadowPieces.clear() })
            }
        } finally {
            extracting = false
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

    private val pose = PoseStack()

    //? if >= 26.2
    private val storage by lazy { SubmitNodeStorage() }

    private fun figure(mc: Minecraft, figure: Figure, prepass: Boolean) = submit(mc, figure.state, coded(figure.avatar, prepass))

    private fun submit(mc: Minecraft, state: EntityRenderState, light: Int) {
        state.lightCoords = light
        //? if >= 26.3 {
        val target = LobbyCompat.mainTarget(mc.gameRenderer)
        val color = target.colorTextureView ?: return
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, storage)
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
        /*mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, storage)
        mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
        *///?} else {
        /*val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        *///?}
    }
    //?} elif > 1.8.9 {
    /*private val pose = PoseStack()

    private fun figure(mc: Minecraft, avatar: LobbyAvatar, prepass: Boolean, partialTicks: Float) = render(mc, avatar, coded(avatar, prepass), partialTicks)

    private fun render(mc: Minecraft, entity: Entity, light: Int, partialTicks: Float) {
        val dispatcher = mc.entityRenderDispatcher
        val buffers = mc.renderBuffers().bufferSource()
        dispatcher.setRenderShadow(false)
        //? if >= 1.21.4 {
        dispatcher.render(entity, 0.0, 0.0, 0.0, partialTicks, pose, buffers, light)
        //?} else {
        /^dispatcher.render(entity, 0.0, 0.0, 0.0, entity.yRot, partialTicks, pose, buffers, light)
        ^///?}
        buffers.endBatch()
        dispatcher.setRenderShadow(true)
    }
    *///?}

    //? if >= 26.3 {
    private val projection by lazy { ProjectionMatrixBuffer("polyplus_hologram") }
    //?}

    //? if > 1.8.9 {
    private val view = Matrix4f()

    private val facing = Quaternionf()

    private inline fun placed(renderer: GameRenderer, x: Double, y: Double, z: Double, draw: () -> Unit) {
        val origin = LobbyCompat.cameraPosition(renderer)
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.mul(view.rotation(LobbyCompat.camera(renderer).rotation().conjugate(facing)))
        stack.translate((x - origin.x).toFloat(), (y - origin.y).toFloat(), (z - origin.z).toFloat())
        //? if < 1.21.4
        /*RenderSystem.applyModelViewMatrix()*/
        try {
            draw()
        } finally {
            stack.popMatrix()
            //? if < 1.21.4
            /*RenderSystem.applyModelViewMatrix()*/
        }
    }

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
        //? if >= 26.3 {
        val scene = LobbyCompat.renderState(renderer).levelRenderState.cameraRenderState
        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(projection.getBuffer(scene.projectionMatrix), ProjectionType.PERSPECTIVE)
        //?}
        drawing = true
        try {
            for (figure in visible) {
                //? if >= 1.21.10 {
                val feet = figure.avatar.position()
                placed(renderer, feet.x, feet.y, feet.z) {
                    figure(mc, figure, true)
                    figure(mc, figure, false)
                }
                //?} else {
                /*val feet = figure.position()
                placed(renderer, feet.x, feet.y, feet.z) {
                    figure(mc, figure, true, partialTicks)
                    figure(mc, figure, false, partialTicks)
                }
                *///?}
            }
        } finally {
            drawing = false
            //? if >= 26.3 {
            RenderSystem.restoreProjectionMatrix()
            //?}
        }
    }

    //? if >= 1.21.10 {
    fun drawGhost(renderer: GameRenderer, state: EntityRenderState, x: Double, y: Double, z: Double, progress: Double, tint: Int) {
        if (!LobbyPipelines.hologramUsable()) return
        val mc = Minecraft.getInstance()
        drawing = true
        try {
            placed(renderer, x, y, z) {
                submit(mc, state, code(progress, 0, true, tint))
                submit(mc, state, code(progress, 0, false, tint))
            }
        } finally {
            drawing = false
        }
    }
    //?} else {
    /*fun drawGhost(renderer: GameRenderer, entity: Entity, progress: Double, tint: Int, partialTicks: Float) {
        if (!LobbyPipelines.hologramUsable()) return
        val mc = Minecraft.getInstance()
        drawing = true
        try {
            placed(renderer, entity.x, entity.y, entity.z) {
                render(mc, entity, code(progress, 0, true, tint), partialTicks)
                render(mc, entity, code(progress, 0, false, tint), partialTicks)
            }
        } finally {
            drawing = false
        }
    }
    *///?}
    //?}

    //? if = 1.8.9 {
    /*private val modelView = BufferUtils.createFloatBuffer(16)

    private val m = FloatArray(16)

    private fun program(name: String) = LobbyLegacyRender.uniform(name)

    fun draw(avatars: List<LobbyAvatar>, partialTicks: Float, seconds: Float) {
        val visible = avatars.filter { it.projecting }
        if (visible.isEmpty() || !begin(seconds, 1f - LobbySky.mood.night, accent)) return
        try {
            for (avatar in visible) figure(avatar, avatar.projection.toFloat(), seed(avatar) / SEED_MASK.toFloat(), partialTicks)
        } finally {
            end()
        }
    }

    fun drawGhost(entity: Entity, progress: Double, tint: Int, partialTicks: Float, seconds: Float) {
        if (!begin(seconds, 1f, tint)) return
        try {
            figure(entity, progress.toFloat(), 0f, partialTicks)
        } finally {
            end()
        }
    }

    private fun begin(seconds: Float, daylight: Float, tint: Int): Boolean {
        if (!LobbyLegacyRender.bindHologram()) return false
        modelView.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
        for (i in m.indices) m[i] = modelView.get(i)
        GL20.glUniform1i(program("Sampler0"), 0)
        GL20.glUniform1f(program("Seconds"), seconds)
        GL20.glUniform1f(program("Daylight"), daylight)
        GL20.glUniform3f(program("Accent"), red(tint) / 255f, green(tint) / 255f, blue(tint) / 255f)
        GL20.glUniform3f(program("UpEye"), m[4], m[5], m[6])
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableAlphaTest()
        GlStateManager.disableCull()
        GlStateManager.depthMask(true)
        GlStateManager.enableDepthTest()
        Minecraft.getInstance().entityRenderDispatcher.setRenderShadow(false)
        drawing = true
        return true
    }

    private fun figure(entity: Entity, progress: Float, seed: Float, partialTicks: Float) {
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val x = (entity.x - dispatcher.cameraX).toFloat()
        val y = (entity.y - dispatcher.cameraY).toFloat()
        val z = (entity.z - dispatcher.cameraZ).toFloat()
        GL20.glUniform3f(program("FeetEye"), m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14])
        GL20.glUniform1f(program("Progress"), progress)
        GL20.glUniform1f(program("Seed"), seed)
        for (prepass in PASSES) {
            GL20.glUniform1f(program("Prepass"), prepass)
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GlStateManager.disableCull()
            dispatcher.render(entity, partialTicks)
        }
    }

    private fun end() {
        drawing = false
        Minecraft.getInstance().entityRenderDispatcher.setRenderShadow(true)
        LobbyLegacyRender.unbindHologram()
        GlStateManager.disableBlend()
        GlStateManager.enableAlphaTest()
        GlStateManager.enableCull()
    }

    private val PASSES = floatArrayOf(1f, 0f)
    *///?}
}
