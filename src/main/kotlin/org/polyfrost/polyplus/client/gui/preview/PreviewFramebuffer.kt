package org.polyfrost.polyplus.client.gui.preview

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
*///?}
//? if >= 26.3 {
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.Optional
import java.util.OptionalDouble
//?}
//? if >= 26.2 {
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.SubmitNodeStorage
import org.joml.Vector4f
//?}
//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
//?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?}
//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
//? if >= 1.21.4 {
import com.mojang.blaze3d.ProjectionType
//?}
//? if = 26.1 {
/*import org.joml.Matrix4f
*///?}
//? if < 1.21.8 && > 1.8.9 {
/*import org.joml.Matrix4f
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}
//? if >= 1.21.8 && < 26.1 {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
*///?}
//? if < 1.21.5 && > 1.8.9 {
/*import org.polyfrost.oneconfig.internal.ui.RenderTargetFbo
*///?}
//? if = 1.21.1 {
/*import com.mojang.blaze3d.vertex.VertexSorting
*///?}

//? if >= 1.21.10 {
typealias PreviewCollector = SubmitNodeCollector
//?}
//? if < 1.21.10 && > 1.8.9 {
/*typealias PreviewCollector = MultiBufferSource
*///?}

//? if > 1.8.9 {
object PreviewFramebuffer {
    fun interface Pass {
        fun draw(): Boolean
    }

    fun interface Submit {
        fun submit(collector: PreviewCollector)
    }

    class Ortho(label: String, private val depth: Float) {
        //? if >= 26.1 {
        private val buffer by lazy { ProjectionMatrixBuffer(label) }
        //?}
        //? if >= 1.21.8 && < 26.1 {
        /*private val buffer by lazy { CachedOrthoProjectionMatrixBuffer(label, -depth, depth, true) }
        *///?}

        internal fun apply(w: Int, h: Int) {
            //? if >= 26.2 {
            val projection = Projection().apply { setupOrtho(-depth, depth, w.toFloat(), h.toFloat(), true) }
            RenderSystem.setProjectionMatrix(buffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC)
            //?}
            //? if = 26.1 {
            /*val matrix = Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -depth, depth)
            RenderSystem.setProjectionMatrix(buffer.getBuffer(matrix), ProjectionType.ORTHOGRAPHIC)
            *///?}
            //? if >= 1.21.8 && < 26.1 {
            /*RenderSystem.setProjectionMatrix(buffer.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)
            *///?}
            //? if >= 1.21.4 && < 1.21.8 {
            /*RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -depth, depth), ProjectionType.ORTHOGRAPHIC)
            *///?}
            //? if = 1.21.1 {
            /*RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -depth, depth), VertexSorting.ORTHOGRAPHIC_Z)
            *///?}
        }
    }

    //? if >= 26.3 {
    private var passColor: GpuTextureView? = null
    private var passDepth: GpuTextureView? = null
    //?}
    //? if < 1.21.5 {
    /*private var bound: RenderTarget? = null
    *///?}

    //? if >= 1.21.8 {
    fun render(fbo: RenderTarget, w: Int, h: Int, ortho: Ortho, pass: Pass): Boolean {
        val colorTex = fbo.colorTexture ?: return false
        val depthTex = fbo.depthTexture ?: return false
        val colorView = fbo.colorTextureView ?: return false
        val depthView = fbo.depthTextureView ?: return false
        val savedLights = RenderSystem.getShaderLights()
        val savedFog = RenderSystem.getShaderFog()

        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, Vector4f(0f, 0f, 0f, 0f), depthTex, 0.0)
        //?} else {
        /*RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, 0x00000000, depthTex, 1.0)
        *///?}
        RenderSystem.backupProjectionMatrix()
        ortho.apply(w, h)
        //? if >= 26.3 {
        passColor = colorView
        passDepth = depthView
        //?} else {
        /*RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        *///?}
        //? if >= 26.1 {
        val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
        val hadScissor = scissor.enabled()
        val scX = scissor.x()
        val scY = scissor.y()
        val scW = scissor.width()
        val scH = scissor.height()
        //?}
        RenderSystem.disableScissorForRenderTypeDraws()
        //? if >= 26.2 {
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.identity()
        //?}
        return try {
            pass.draw()
        } finally {
            //? if >= 26.2 {
            modelViewStack.popMatrix()
            //?}
            //? if >= 26.3 {
            passColor = null
            passDepth = null
            //?} else {
            /*RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            *///?}
            RenderSystem.restoreProjectionMatrix()
            savedLights?.let { RenderSystem.setShaderLights(it) }
            savedFog?.let { RenderSystem.setShaderFog(it) }
            //? if >= 26.1 {
            if (hadScissor) RenderSystem.enableScissorForRenderTypeDraws(scX, scY, scW, scH)
            //?}
        }
    }
    //?}

    //? if < 1.21.8 {
    /*fun render(fbo: RenderTarget, w: Int, h: Int, ortho: Ortho, pass: Pass): Boolean {
        val mc = Minecraft.getInstance()
        //? if >= 1.21.5 {
        val colorTex = fbo.colorTexture ?: return false
        val depthTex = fbo.depthTexture ?: return false
        //?}
        //? if < 1.21.5 {
        /*// live previews render right before the buffer swap, which presents nothing on macOS unless framebuffer 0 is still bound
        val boundTarget = RenderTargetFbo.saveBoundTarget()
        *///?}
        //? if >= 1.21.5 {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTex, 0, depthTex, 1.0)
        //?} elif = 1.21.4 {
        /^fbo.setClearColor(0f, 0f, 0f, 0f)
        fbo.clear()
        fbo.bindWrite(true)
        ^///?} else {
        /^fbo.setClearColor(0f, 0f, 0f, 0f)
        fbo.clear(Minecraft.ON_OSX)
        fbo.bindWrite(true)
        ^///?}
        RenderSystem.backupProjectionMatrix()
        ortho.apply(w, h)
        val realMainTarget = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(fbo)
        //? if < 1.21.5
        //bound = fbo
        return try {
            pass.draw()
        } finally {
            //? if < 1.21.5
            //bound = null
            (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(realMainTarget)
            RenderSystem.restoreProjectionMatrix()
            //? if < 1.21.5
            //RenderTargetFbo.restoreBoundTarget(boundTarget)
        }
    }
    *///?}

    fun collect(submit: Submit) {
        val mc = Minecraft.getInstance()
        //? if >= 26.3 {
        val storage = SubmitNodeStorage()
        submit.submit(storage)
        val colorView = passColor ?: return
        mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { frame ->
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "polyplus_preview" },
                colorView,
                Optional.empty(),
                passDepth,
                OptionalDouble.empty(),
            ).use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                FeatureRenderDispatcher.renderAllFeatures(pass, frame)
            }
        }
        //?}
        //? if = 26.2 {
        /*val storage = SubmitNodeStorage()
        submit.submit(storage)
        mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
        *///?}
        //? if >= 1.21.10 && < 26.2 {
        /*val features = mc.gameRenderer.featureRenderDispatcher
        submit.submit(features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        *///?}
        //? if < 1.21.10 {
        /*val buffers = mc.renderBuffers().bufferSource()
        submit.submit(buffers)
        //? if < 1.21.5
        //bound?.bindWrite(true)
        buffers.endBatch()
        *///?}
    }
}
//?} else {
/*object PreviewFramebuffer {
    fun interface Pass {
        fun draw(): Boolean
    }

    class Ortho(@Suppress("UNUSED_PARAMETER") label: String, internal val depth: Float)

    fun render(fbo: RenderTarget, w: Int, h: Int, ortho: Ortho, pass: Pass): Boolean {
        if (!GLX.useFbo()) return false
        var drawn = false
        withSavedFramebuffer {
            resyncGlState()
            fbo.setClearColor(0f, 0f, 0f, 0f)
            fbo.clear()
            fbo.bindWrite(true)
            withOrtho(w, h, ortho.depth) { drawn = pass.draw() }
        }
        return drawn
    }

    internal inline fun withSavedFramebuffer(block: () -> Unit) {
        val fb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        try {
            block()
        } finally {
            GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, fb)
            GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        }
    }

    internal fun resyncGlState() {
        GlStateManager.disableDepthTest(); GlStateManager.enableDepthTest()
        GlStateManager.depthFunc(GL11.GL_ALWAYS); GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.depthMask(false); GlStateManager.depthMask(true)
        GlStateManager.colorMask(false, false, false, false); GlStateManager.colorMask(true, true, true, true)
        GlStateManager.enableCull(); GlStateManager.disableCull()
        GlStateManager.enableBlend(); GlStateManager.disableBlend()
        GlStateManager.enableLighting(); GlStateManager.disableLighting()
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    private inline fun withOrtho(w: Int, h: Int, depth: Float, block: () -> Unit) {
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, w.toDouble(), h.toDouble(), 0.0, (CAMERA_Z - depth).toDouble(), (CAMERA_Z + depth).toDouble())
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.translatef(0f, 0f, -CAMERA_Z)
        try {
            block()
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
        }
    }

    private const val CAMERA_Z = 2000f
}
*///?}
