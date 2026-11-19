package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.core.BlockPos
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import org.polyfrost.polyplus.client.gui.onboarding.live.LiveArgb as ARGB
*///?}
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector4f
import org.joml.Vector3f
//? if >= 26.3 {
import com.mojang.renderpearl.api.GpuFormat
//?} elif = 26.2 {
/*import com.mojang.blaze3d.GpuFormat
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.fog.FogRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
//?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}
//? if < 1.21.5
/*import net.minecraft.client.Minecraft*/
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class LiveSpot { STANDING, RIDING_PATH }

internal class LiveSpotPlace(val feet: Vec3, val yaw: Float, val length: Double) {
    fun ahead(distance: Double): Vec3 {
        val rad = Math.toRadians(yaw.toDouble())
        return feet.add(-sin(rad) * distance, 0.0, cos(rad) * distance)
    }
}

internal class LiveCamera(val position: Vec3, val yaw: Float, val pitch: Float, val fov: Float = DEFAULT_FOV) {
    internal fun view(): RenderBlocksView = RenderBlocksView(
        Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
        0f,
        yaw + HALF_TURN,
        pitch,
        fov,
    )

    companion object {
        const val DEFAULT_FOV = 70f
        private const val HALF_TURN = 180f
        private const val PLAYER_CENTER = 1.0

        fun lookingAt(position: Vec3, target: Vec3, fov: Float = DEFAULT_FOV): LiveCamera {
            val d = target.subtract(position)
            val yaw = Math.toDegrees(atan2(-d.x, d.z)).toFloat()
            val pitch = Math.toDegrees(-atan2(d.y, sqrt(d.x * d.x + d.z * d.z))).toFloat()
            return LiveCamera(position, yaw, pitch, fov)
        }

        fun orbit(
            place: LiveSpotPlace,
            angleDeg: Float,
            distance: Double,
            height: Double,
            targetHeight: Double = PLAYER_CENTER,
            fov: Float = DEFAULT_FOV,
        ): LiveCamera {
            val rad = Math.toRadians((place.yaw + angleDeg).toDouble())
            val target = place.feet.add(0.0, targetHeight, 0.0)
            val eye = place.feet.add(-sin(rad) * distance, height, cos(rad) * distance)
            return lookingAt(eye, target, fov)
        }

        fun ride(place: LiveSpotPlace, forward: Double, eyeHeight: Double, pitch: Float = 0f, fov: Float = DEFAULT_FOV): LiveCamera =
            LiveCamera(place.ahead(forward).add(0.0, eyeHeight, 0.0), place.yaw, pitch, fov)
    }
}

//? if >= 1.21.10 {
internal class LiveSceneFrame(
    val collector: SubmitNodeCollector,
    val pose: PoseStack,
    val camera: LiveCamera,
    val cameraState: CameraRenderState,
    private val terrain: LiveSceneTerrain.Built,
) {
//?} else {
/*internal class LiveSceneFrame(
    val buffers: MultiBufferSource,
    val pose: PoseStack,
    val camera: LiveCamera,
    private val terrain: LiveSceneTerrain.Built,
) {
*///?}
    fun relative(pos: Vec3): Vec3 = pos.subtract(camera.position)

    fun light(pos: Vec3): Int = terrain.light(BlockPos.containing(pos))
}

internal class LiveScene(val spot: LiveSpot) : AutoCloseable {
    private var renderer: RenderBlocksRenderer? = null
    private var hud: LiveWorldHud? = null
    //? if >= 26.1 {
    private var fog: FogRenderer? = null
    private val fogData = FogData()
    //?} else {
    /*private var fog: LiveFogBuffer? = null
    *///?}
    private var terrain: LiveSceneTerrain.Built? = null
    private var sceneTarget: TextureTarget? = null
    private var sky: LiveSceneSky? = null
    //? if >= 1.21.10
    private val cameraState = CameraRenderState()

    val ready: Boolean get() = current() != null

    fun place(): LiveSpotPlace = current()?.place ?: LiveSceneFallback.place(spot)

    fun render(target: RenderTarget, aspect: Float, camera: LiveCamera, drawEntities: (LiveSceneFrame) -> Unit): Boolean {
        val built = current() ?: return false
        val gpu = renderer ?: RenderBlocksRenderer("polyplus_live_scene").also { renderer = it }
        val pass = hud ?: LiveWorldHud("polyplus_live_scene_hud").also { hud = it }
        //? if >= 26.1 {
        val fogs = fog ?: createFog().also { fog = it }
        //?} else {
        /*val fogs = fog ?: LiveFogBuffer("polyplus_live_scene").also { fog = it }
        *///?}
        val atmosphere = built.atmosphere(camera.position)
        val scene = sceneTarget(target) ?: return false
        //? if >= 1.21.5 {
        val sceneColor = scene.colorTexture ?: return false
        val sceneDepth = scene.depthTexture ?: return false
        //?}

        //? if >= 26.1 {
        fogs.endFrame()
        fogData.color.set(ARGB.redFloat(atmosphere.fog), ARGB.greenFloat(atmosphere.fog), ARGB.blueFloat(atmosphere.fog), 1f)
        fogData.environmentalStart = Float.MAX_VALUE
        fogData.environmentalEnd = Float.MAX_VALUE
        fogData.renderDistanceStart = FOG_START
        fogData.renderDistanceEnd = FOG_END
        fogData.skyEnd = SKY_END
        fogData.cloudEnd = SKY_END
        fogs.updateBuffer(fogData)
        val fogBuffer = fogs.getBuffer(FogRenderer.FogMode.WORLD)
        //?} else {
        /*val fogColor = Vector4f(ARGB.redFloat(atmosphere.fog), ARGB.greenFloat(atmosphere.fog), ARGB.blueFloat(atmosphere.fog), 1f)
        val fogBuffer = fogs.write(fogColor, FOG_START, FOG_END, SKY_END)
        *///?}
        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            sceneColor,
            Vector4f(fogData.color.x, fogData.color.y, fogData.color.z, 1f),
            sceneDepth,
            0.0,
        )
        //?} elif >= 1.21.5 {
        /*RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(sceneColor, ARGB.opaque(atmosphere.fog), sceneDepth, 1.0)
        *///?} else {
        /*scene.setClearColor(ARGB.redFloat(atmosphere.fog), ARGB.greenFloat(atmosphere.fog), ARGB.blueFloat(atmosphere.fog), 1f)
        //? if >= 1.21.4 {
        scene.clear()
        //?} else {
        /^scene.clear(Minecraft.ON_OSX)
        ^///?}
        *///?}
        val rotation = Matrix4f().rotateX(Math.toRadians(camera.pitch.toDouble()).toFloat())
            .rotateY(Math.toRadians((camera.yaw + HALF_TURN).toDouble()).toFloat())
        //? if > 1.8.9 {
        //? if >= 1.21.4 {
        val skyLevel = built.skyLevel
        //?} else {
        /*val skyLevel = built.skyLevel?.takeIf { Minecraft.getInstance().level === it && Minecraft.getInstance().player != null }
        *///?}
        if (skyLevel != null) {
            val skies = sky?.takeIf { it.owns(scene) } ?: LiveSceneSky(scene).also {
                sky?.close()
                sky = it
            }
            skies.render(skyLevel, camera.position, rotation, aspect, camera.fov, fogBuffer)
        } else {
            pass.backdrop(scene, aspect, atmosphere.sky, atmosphere.fog, camera.pitch, camera.fov)
        }
        //?} else {
        /*pass.backdrop(scene, aspect, atmosphere.sky, atmosphere.fog, camera.pitch, camera.fov)
        *///?}
        //? if >= 1.21.4 {
        val savedFog = RenderSystem.getShaderFog()
        RenderSystem.setShaderFog(fogBuffer)
        //?} else {
        /*val savedFog = LiveFog.current()
        fogBuffer.apply()
        *///?}
        try {
            val drawn = gpu.render(
                scene,
                aspect,
                camera.view(),
                atmosphere.lighting,
                listOf(RenderBlocksDraw(built.mesh, IDENTITY, built.originVector)),
                clearColor = false,
            )
            if (!drawn) return false
            val pose = PoseStack()
            pose.turn(Axis.XP, camera.pitch)
            pose.turn(Axis.YP, camera.yaw + HALF_TURN)
            //? if >= 1.21.10 {
            prepareCamera(camera)
            pass.render(scene, aspect, camera.fov, RenderBlocksRenderer.FAR, clearDepth = false) { storage ->
                drawEntities(LiveSceneFrame(storage, pose, camera, cameraState, built))
            }
            //?} else {
            /*pass.render(scene, aspect, camera.fov, RenderBlocksRenderer.FAR, clearDepth = false) { buffers ->
                drawEntities(LiveSceneFrame(buffers, pose, camera, built))
            }
            *///?}
        } finally {
            //? if >= 1.21.4 {
            savedFog?.let(RenderSystem::setShaderFog)
            //?} else {
            /*savedFog.apply()
            *///?}
        }
        return LiveSceneGrade.apply(scene, target, built.fade(System.nanoTime()))
    }

    private fun sceneTarget(target: RenderTarget): TextureTarget? {
        val width = target.width
        val height = target.height
        if (width <= 0 || height <= 0) return null
        val current = sceneTarget
        if (current != null && current.width == width && current.height == height) return current
        //? if >= 26.3 {
        val next = TextureTarget("polyplus_live_scene", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT)
        //?} elif >= 26.2 {
        /*val next = TextureTarget("polyplus_live_scene", width, height, true, GpuFormat.RGBA8_UNORM)
        *///?} elif >= 1.21.5 {
        /*val next = TextureTarget("polyplus_live_scene", width, height, true)
        *///?} elif >= 1.21.4 {
        /*val next = TextureTarget(width, height, true)
        *///?} else {
        /*val next = TextureTarget(width, height, true, Minecraft.ON_OSX)
        *///?}
        sceneTarget = next
        val oldSky = sky
        sky = null
        if (current != null || oldSky != null) {
            liveRetire {
                oldSky?.close()
                current?.destroyBuffers()
            }
        }
        return next
    }

    //? if >= 1.21.10 {
    private fun prepareCamera(camera: LiveCamera) {
        cameraState.pos = camera.position
        cameraState.blockPos = BlockPos.containing(camera.position)
        //? if >= 26.1 {
        cameraState.xRot = camera.pitch
        cameraState.yRot = camera.yaw
        //?} else {
        /*cameraState.entityPos = camera.position
        *///?}
        cameraState.initialized = true
        //? if >= 26.3
        cameraState.isFirstPerson = false
        cameraState.orientation = Quaternionf().rotationYXZ(
            Math.PI.toFloat() - Math.toRadians(camera.yaw.toDouble()).toFloat(),
            -Math.toRadians(camera.pitch.toDouble()).toFloat(),
            0f,
        )
        //? if >= 26.1 {
        cameraState.hudFov = camera.fov
        cameraState.depthFar = RenderBlocksRenderer.FAR
        //?}
    }
    //?}

    private fun current(): LiveSceneTerrain.Built? {
        val wanted = LiveSceneTerrain.current(spot) ?: return null
        val held = terrain
        if (held === wanted) return wanted
        held?.let(LiveSceneTerrain::release)
        LiveSceneTerrain.retain(wanted)
        terrain = wanted
        return wanted
    }

    override fun close() {
        terrain?.let(LiveSceneTerrain::release)
        terrain = null
        listOfNotNull(renderer, hud, fog, sky).forEach { it.close() }
        sky = null
        sceneTarget?.destroyBuffers()
        sceneTarget = null
        renderer = null
        hud = null
        fog = null
    }

    //? if >= 26.1 {
    private fun createFog(): FogRenderer {
        val saved = RenderSystem.getShaderFog()
        return FogRenderer().also { saved?.let(RenderSystem::setShaderFog) }
    }
    //?}

    private companion object {
        val IDENTITY = Matrix4f()
        const val HALF_TURN = 180f
        const val FOG_START = 30f
        const val FOG_END = 46f
        const val SKY_END = 192f
    }
}

@Composable
internal fun LiveSceneCanvas(
    spot: LiveSpot,
    modifier: Modifier,
    camera: (place: LiveSpotPlace, time: Float) -> LiveCamera,
    drawEntities: (frame: LiveSceneFrame, time: Float) -> Unit,
) {
    val content = remember(spot) { LiveSceneContent(LiveScene(spot)) }
    val slot = remember(content) { RenderBlocksSlot("polyplus_live_scene", content) }
    SideEffect {
        content.camera = camera
        content.drawEntities = drawEntities
    }
    RenderBlocksCanvas(slot, modifier)
}

private class LiveSceneContent(private val scene: LiveScene) : RenderBlocksContent {
    var camera: (LiveSpotPlace, Float) -> LiveCamera = { place, _ -> LiveCamera.orbit(place, 0f, ORBIT_DISTANCE, ORBIT_HEIGHT) }
    var drawEntities: (LiveSceneFrame, Float) -> Unit = { _, _ -> }
    private val startNanos = System.nanoTime()

    override fun render(renderer: RenderBlocksRenderer, target: RenderTarget, width: Int, height: Int, aspect: Float, nowNanos: Long): Boolean {
        val time = ((nowNanos - startNanos) / NANOS_PER_SECOND).toFloat()
        if (!scene.ready) return false
        val view = camera(scene.place(), time)
        return scene.render(target, aspect, view) { drawEntities(it, time) }
    }

    override fun release(): List<AutoCloseable> = listOf(LiveSceneRelease(scene))

    private companion object {
        const val ORBIT_DISTANCE = 3.5
        const val ORBIT_HEIGHT = 1.4
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

private class LiveSceneRelease(private val scene: LiveScene) : AutoCloseable {
    override fun close() = scene.close()
}

internal fun prepareLiveScenes() = LiveSceneTerrain.prepareAll()

internal fun releaseLiveScenes() = LiveSceneTerrain.releaseAll()
//?}
