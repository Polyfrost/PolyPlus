package org.polyfrost.polyplus.compat.iris

import net.irisshaders.iris.Iris
import net.irisshaders.iris.gl.blending.BlendModeStorage
import net.irisshaders.iris.gl.blending.DepthColorStorage
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.shadows.ShadowRenderingState
import org.polyfrost.polyplus.client.nameplate.material.NameplatePackSurface
//? if >= 1.21.5 {
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer
import net.irisshaders.iris.pipeline.IrisPipelines
import net.irisshaders.iris.pipeline.programs.ExtendedShader
import net.irisshaders.iris.targets.RenderTargets
import net.minecraft.client.renderer.RenderPipelines
import java.lang.reflect.Field
//?}
//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlStateManager
//?} elif >= 1.21.5 {
/*import com.mojang.blaze3d.opengl.GlStateManager
*///?} else {
/*import com.mojang.blaze3d.platform.GlStateManager
import org.lwjgl.opengl.GL11
*///?}

object IrisPackTarget : NameplatePackSurface {
    private const val GL_READ_FRAMEBUFFER = 0x8CA8
    private const val GL_DRAW_FRAMEBUFFER = 0x8CA9
    //? if < 1.21.5 {
    /*private const val GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6
    private const val GL_READ_FRAMEBUFFER_BINDING = 0x8CAA
    *///?}

    private var savedDraw = 0
    private var savedRead = 0

    override fun resolve(): Boolean = Iris.getPipelineManager().pipelineNullable is IrisRenderingPipeline

    override fun active(): Boolean = overriding() != null

    override fun unlockBlend() {
        if (BlendModeStorage.isBlendLocked()) BlendModeStorage.restoreBlend()
    }

    override fun redirect(): Boolean {
        val pipeline = overriding() ?: return false
        DepthColorStorage.unlockDepthColor()
        unlockBlend()
        savedDraw = boundFramebuffer(GL_DRAW_FRAMEBUFFER)
        savedRead = boundFramebuffer(GL_READ_FRAMEBUFFER)
        if (!bindTextTarget(pipeline)) pipeline.bindDefault()
        return true
    }

    override fun restore() {
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, savedDraw)
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, savedRead)
    }

    private fun overriding(): IrisRenderingPipeline? {
        val pipeline = Iris.getPipelineManager().pipelineNullable as? IrisRenderingPipeline ?: return null
        if (!pipeline.shouldOverrideShaders() || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return null
        return pipeline
    }

    //? if >= 1.21.5 {
    private var targetsOf: IrisRenderingPipeline? = null
    private val textTargets = HashMap<Int, GlFramebuffer?>()
    private var textTargetBroken = false
    private val renderTargetsField: Field by lazy { accessible(IrisRenderingPipeline::class.java, "renderTargets") }
    private val beforeTranslucentField: Field by lazy { accessible(ExtendedShader::class.java, "writingToBeforeTranslucent") }
    private val afterTranslucentField: Field by lazy { accessible(ExtendedShader::class.java, "writingToAfterTranslucent") }

    private fun accessible(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).also { it.isAccessible = true }

    private fun bindTextTarget(pipeline: IrisRenderingPipeline): Boolean {
        if (textTargetBroken) return false
        val target = try {
            textTarget(pipeline)
        } catch (e: ReflectiveOperationException) {
            textTargetBroken = true
            null
        } catch (e: RuntimeException) {
            textTargetBroken = true
            null
        } catch (e: LinkageError) {
            textTargetBroken = true
            null
        } ?: return false
        target.bind()
        return true
    }

    private fun textTarget(pipeline: IrisRenderingPipeline): GlFramebuffer? {
        if (targetsOf !== pipeline) {
            targetsOf = pipeline
            textTargets.clear()
        }
        val key = IrisPipelines.getPipeline(pipeline, RenderPipelines.TEXT) ?: return null
        val shader = pipeline.shaderMap.getShader(key) as? ExtendedShader ?: return null
        val field = if (pipeline.isBeforeTranslucent) beforeTranslucentField else afterTranslucentField
        val source = field.get(shader) as? GlFramebuffer ?: return null
        val texture = source.getColorAttachment(0)
        if (textTargets.containsKey(texture)) return textTargets[texture]
        val made = writingTo(renderTargetsField.get(pipeline) as RenderTargets, texture)
        textTargets[texture] = made
        return made
    }

    private fun writingTo(targets: RenderTargets, texture: Int): GlFramebuffer? {
        for (i in 0 until targets.renderTargetCount) {
            val target = targets.get(i) ?: continue
            if (target.mainTexture == texture) return targets.createFramebufferWritingToMain(intArrayOf(i))
            if (target.altTexture == texture) return targets.createFramebufferWritingToAlt(intArrayOf(i))
        }
        return null
    }
    //?} else {
    /*private fun bindTextTarget(pipeline: IrisRenderingPipeline): Boolean = false
    *///?}

    private fun boundFramebuffer(target: Int): Int {
        //? if >= 1.21.5 {
        return GlStateManager.getFrameBuffer(target)
        //?} else {
        /*return GL11.glGetInteger(if (target == GL_DRAW_FRAMEBUFFER) GL_DRAW_FRAMEBUFFER_BINDING else GL_READ_FRAMEBUFFER_BINDING)
        *///?}
    }
}
