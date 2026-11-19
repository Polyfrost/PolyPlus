package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
//? if >= 1.21.4
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.data.worldgen.biome.OverworldBiomes
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import org.polyfrost.polyplus.client.gui.onboarding.live.LiveArgb as ARGB
*///?}
import org.joml.Matrix4f
import org.joml.Vector3fc
//? if >= 26.1 {
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.world.level.CardinalLighting
//?} elif >= 1.21.8 {
/*import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer
*///?} else {
/*import org.joml.Vector3f
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}
//? if < 1.21.5
/*import org.lwjgl.opengl.GL11*/
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.attribute.EnvironmentAttributes
//?} else {
/*import net.minecraft.client.renderer.RenderType as RenderTypes
*///?}
//? if = 1.21.11
/*import net.minecraft.world.level.dimension.DimensionType*/
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.SubmitNodeStorage
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
//? if >= 26.3 {
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.Optional
import java.util.OptionalDouble
//?}

//? if >= 1.21.10 {
internal typealias LiveHudSink = SubmitNodeStorage
internal typealias LiveHudCollector = SubmitNodeCollector
//?} else {
/*internal typealias LiveHudSink = MultiBufferSource.BufferSource
internal typealias LiveHudCollector = MultiBufferSource
*///?}

internal class LiveWorldHud(private val label: String) : AutoCloseable {
    //? if >= 26.1 {
    private val projection = Projection()
    private val projectionBuffer = ProjectionMatrixBuffer(label)
    //?} elif >= 1.21.8 {
    /*private val projections = HashMap<Float, CachedPerspectiveProjectionMatrixBuffer>()
    *///?}

    fun backdrop(target: RenderTarget, aspect: Float) {
        val (sky, fog) = skyColors()
        backdrop(target, aspect, sky, fog, 0f, FOV)
    }

    fun backdrop(target: RenderTarget, aspect: Float, sky: Int, fog: Int, pitchDeg: Float, fovDeg: Float) {
        val ground = ARGB.scaleRGB(fog, GROUND_SHADE)
        val tilt = PoseStack()
        tilt.turn(Axis.XP, pitchDeg)
        render(target, aspect, fovDeg) { storage ->
            //? if >= 1.21.10 {
            storage.submitCustomGeometry(tilt, RenderTypes.debugQuads()) { pose, buffer -> gradient(pose.pose(), buffer, sky, fog, ground) }
            //?} else {
            /*gradient(tilt.last().pose(), storage.getBuffer(RenderTypes.debugQuads()), sky, fog, ground)
            *///?}
        }
    }

    private fun gradient(m: Matrix4f, buffer: VertexConsumer, sky: Int, fog: Int, ground: Int) {
        buffer.addVertex(m, -SKY_HALF_WIDTH, SKY_TOP, SKY_DEPTH).setColor(sky)
        buffer.addVertex(m, SKY_HALF_WIDTH, SKY_TOP, SKY_DEPTH).setColor(sky)
        buffer.addVertex(m, SKY_HALF_WIDTH, SKY_HORIZON, SKY_DEPTH).setColor(fog)
        buffer.addVertex(m, -SKY_HALF_WIDTH, SKY_HORIZON, SKY_DEPTH).setColor(fog)
        buffer.addVertex(m, -SKY_HALF_WIDTH, SKY_HORIZON, SKY_DEPTH).setColor(fog)
        buffer.addVertex(m, SKY_HALF_WIDTH, SKY_HORIZON, SKY_DEPTH).setColor(fog)
        buffer.addVertex(m, SKY_HALF_WIDTH, -SKY_TOP, SKY_DEPTH).setColor(ground)
        buffer.addVertex(m, -SKY_HALF_WIDTH, -SKY_TOP, SKY_DEPTH).setColor(ground)
    }

    fun render(
        target: RenderTarget,
        aspect: Float,
        fovDeg: Float = FOV,
        far: Float = FAR,
        clearDepth: Boolean = true,
        submit: (LiveHudSink) -> Unit,
    ) {
        val mc = Minecraft.getInstance()
        //? if >= 1.21.5
        val depthTexture = target.depthTexture ?: return
        //? if >= 1.21.8 {
        val colorView = target.colorTextureView ?: return
        val depthView = target.depthTextureView ?: return
        //?}
        //? if >= 26.2 {
        if (clearDepth) RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 0.0)
        //?} elif >= 1.21.5 {
        /*if (clearDepth) RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(depthTexture, 1.0)
        *///?} else {
        /*target.bindWrite(true)
        RenderSystem.depthMask(true)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        if (clearDepth) liveClear(GL11.GL_DEPTH_BUFFER_BIT)
        *///?}

        RenderSystem.backupProjectionMatrix()
        //? if >= 26.1 {
        projection.setupPerspective(NEAR, far, fovDeg, aspect, 1f)
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE)
        //?} elif >= 1.21.8 {
        /*val projection = projections.getOrPut(far) { CachedPerspectiveProjectionMatrixBuffer(label, NEAR, far) }
        RenderSystem.setProjectionMatrix(projection.getBuffer(target.width, target.height, fovDeg), ProjectionType.PERSPECTIVE)
        *///?} elif >= 1.21.5 {
        /*RenderSystem.setProjectionMatrix(
            Matrix4f().setPerspective(Math.toRadians(fovDeg.toDouble()).toFloat(), aspect, NEAR, far),
            ProjectionType.PERSPECTIVE,
        )
        *///?} else {
        /*liveProjection(Matrix4f().setPerspective(Math.toRadians(fovDeg.toDouble()).toFloat(), aspect, NEAR, far))
        *///?}
        //? if >= 1.21.8 {
        val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
        val hadScissor = scissor.enabled()
        val scX = scissor.x()
        val scY = scissor.y()
        val scW = scissor.width()
        val scH = scissor.height()
        RenderSystem.disableScissorForRenderTypeDraws()
        val savedLights = RenderSystem.getShaderLights()
        //?} elif >= 1.21.5 {
        /*val savedLights = RenderSystem.getShaderLights().map { Vector3f(it) }
        *///?}
        //? if >= 26.2 {
        val lighting = mc.gameRenderer.lighting()
        //?} elif >= 1.21.8 {
        /*val lighting = mc.gameRenderer.lighting
        *///?}
        //? if >= 26.1 {
        if (mc.level == null) lighting.updateLevel(CardinalLighting.Type.DEFAULT)
        //?} elif >= 1.21.11 {
        /*if (mc.level == null) lighting.updateLevel(DimensionType.CardinalLightType.DEFAULT)
        *///?} elif >= 1.21.8 {
        /*if (mc.level == null) lighting.updateLevel(false)
        *///?}
        //? if >= 1.21.8 {
        lighting.setupFor(Lighting.Entry.LEVEL)
        //?} else {
        /*Lighting.setupLevel()
        *///?}
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.identity()
        //? if < 1.21.4
        /*RenderSystem.applyModelViewMatrix()*/
        //? if < 26.3 && >= 1.21.8 {
        /*RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        *///?} elif < 1.21.8 {
        /*val main = mc.mainRenderTarget
        (mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(target)
        *///?}
        try {
            //? if >= 26.3 {
            val storage = SubmitNodeStorage()
            submit(storage)
            mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { prepared ->
                if (prepared.isEmpty()) return
                RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    { label },
                    colorView,
                    Optional.empty(),
                    depthView,
                    OptionalDouble.empty(),
                ).use { pass ->
                    RenderSystem.bindDefaultUniforms(pass)
                    FeatureRenderDispatcher.renderAllFeatures(pass, prepared)
                }
            }
            //?} elif >= 26.2 {
            /*val storage = SubmitNodeStorage()
            submit(storage)
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
            *///?} elif >= 1.21.10 {
            /*val features = mc.gameRenderer.featureRenderDispatcher
            submit(features.submitNodeStorage)
            features.renderAllFeatures()
            mc.renderBuffers().bufferSource().endBatch()
            *///?} else {
            /*val buffers = mc.renderBuffers().bufferSource()
            submit(buffers)
            buffers.endBatch()
            *///?}
        } finally {
            //? if < 26.3 && >= 1.21.8 {
            /*RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            *///?} elif < 1.21.8 {
            /*(mc as MinecraftAccessor).`polyplus$setMainRenderTarget`(main)
            //? if < 1.21.5
            /^main.bindWrite(true)^/
            *///?}
            modelView.popMatrix()
            //? if < 1.21.4
            /*RenderSystem.applyModelViewMatrix()*/
            RenderSystem.restoreProjectionMatrix()
            //? if >= 1.21.8 {
            savedLights?.let(RenderSystem::setShaderLights)
            if (hadScissor) RenderSystem.enableScissorForRenderTypeDraws(scX, scY, scW, scH)
            //?} elif >= 1.21.5 {
            /*RenderSystem.setShaderLights(savedLights[0], savedLights[1])
            *///?}
        }
    }

    override fun close() {
        //? if >= 26.1 {
        projectionBuffer.close()
        //?} elif >= 1.21.8 {
        /*projections.values.forEach { it.close() }
        projections.clear()
        *///?}
    }

    private fun skyColors(): Pair<Int, Int> {
        val mc = Minecraft.getInstance()
        if (mc.level != null) {
            val live = runCatching {
                //? if >= 26.2 {
                val probe = mc.gameRenderer.mainCamera().attributeProbe()
                //?} elif >= 1.21.11 {
                /*val probe = mc.gameRenderer.mainCamera.attributeProbe()
                *///?}
                //? if >= 1.21.11 {
                opaque(probe.getValue(EnvironmentAttributes.SKY_COLOR, 0f)) to opaque(probe.getValue(EnvironmentAttributes.FOG_COLOR, 0f))
                //?} else {
                /*val level = mc.level ?: error("no level")
                LiveSceneTerrain.lobbyAtmosphere(level, mc.gameRenderer.mainCamera.position, 1f).let { it.sky to it.fog }
                *///?}
            }.getOrNull()
            if (live != null) return live
        }
        return ARGB.opaque(OverworldBiomes.calculateSkyColor(OVERWORLD_TEMPERATURE)) to ARGB.opaque(OVERWORLD_FOG)
    }

    //? if >= 26.3 {
    private fun opaque(color: Vector3fc): Int = ARGB.colorFromFloat(1f, color.x(), color.y(), color.z())
    //?} elif >= 1.21.11 {
    /*private fun opaque(color: Int): Int = ARGB.opaque(color)
    *///?}

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
        const val FOV = 70f
        const val SKY_HALF_WIDTH = 60f
        const val SKY_TOP = 30f
        const val SKY_HORIZON = -1f
        const val SKY_DEPTH = -10f
        const val GROUND_SHADE = 0.72f
        const val OVERWORLD_TEMPERATURE = 0.8f
        const val OVERWORLD_FOG = -4138753
    }
}
//?}
