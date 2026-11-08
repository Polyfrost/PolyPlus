package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9
import com.mojang.blaze3d.systems.RenderSystem
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
//?} elif >= 1.21.8 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
*///?}
import net.minecraft.client.Minecraft
//? if < 26.3 {
/*import net.minecraft.client.renderer.GameRenderer
*///?}
//? if < 26.3 && >= 1.21.4
/*import com.mojang.blaze3d.resource.GraphicsResourceAllocator*/
//? if < 1.21.4 && > 1.8.9 {
/*import net.minecraft.client.renderer.EffectInstance
import net.minecraft.client.renderer.PostChain
*///?} elif = 1.8.9 {
/*import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.world.World
import net.minecraft.client.render.Effect as EffectInstance
import net.minecraft.client.render.PostChain
import org.lwjgl.opengl.GL11
*///?}
//? if < 1.21.8 && >= 1.21.5 {
/*import com.mojang.blaze3d.systems.RenderPass
*///?} elif = 1.21.4 {
/*import net.minecraft.client.renderer.CompiledShaderProgram
*///?}
//? if < 1.21.8 {
/*import java.nio.ByteBuffer
*///?}
//? if >= 1.21.4
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.resources.Identifier
//? if < 26.3 {
/*import net.minecraft.util.Mth
*///?}
//? if >= 1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes
//?}
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
//? if >= 26.3 || < 1.21.11
import org.joml.Vector3fc
//? if < 1.21.8
/*import org.joml.Vector4f*/
import org.lwjgl.system.MemoryUtil
import org.polyfrost.polyplus.client.lobby.shader.DepthModel
import org.polyfrost.polyplus.client.lobby.shader.LobbyShaderBridge
import org.polyfrost.polyplus.client.lobby.shader.LobbyShaderBridges
//? if >= 1.21.8 || < 1.21.5
import org.polyfrost.polyplus.mixin.client.access.PostChainAccessor
//? if >= 1.21.8 || = 1.21.4
import org.polyfrost.polyplus.mixin.client.access.PostPassAccessor
import kotlin.math.tan

object LobbySky {
    //? if >= 1.21.8 {
    val POST_EFFECT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "lobby")
    //?} elif >= 1.21.5 {
    /*val POST_EFFECT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "lobby_uniforms")
    *///?} elif >= 1.21.4 {
    /*val POST_EFFECT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "lobby_program")
    *///?}

    //? if >= 1.21.4 {
    @JvmStatic
    fun ownsPostEffect(id: Identifier): Boolean = id == POST_EFFECT
    //?}

    //? if < 1.21.8 {
    /*private val SKY_NAMES = listOf("Forward", "Right", "Up", "Params", "Mood", "Lens", "Wave", "Flags")
    private val GLASS_NAMES = listOf("Glass") + (0 until 12).map { "Panel$it" } + (0 until 3).map { "Radius$it" }
    *///?}
    private const val BLOCK = "LobbySky"
    private const val GLASS_BLOCK = "LobbyGlass"
    private const val BYTES = 8 * 4 * Float.SIZE_BYTES

    var mood = LobbyMood.NEUTRAL

    private val NIGHT_LIGHT = Vector3f(0.5f, 0.66f, 0.86f)
    private val NIGHT_SKY = Vector3f(0.015f, 0.03f, 0.09f)
    private val NIGHT_FOG = Vector3f(0.05f, 0.09f, 0.17f)
    private val DAY_HAZE = Vector3f(0.52f, 0.7f, 0.98f)
    private const val MOONLIGHT = 0.34f
    private const val DEPTH_OF_FIELD = 0.9f
    private const val PACK_DEPTH_OF_FIELD = 0.35f
    private const val INTERIOR_DEPTH_OF_FIELD = 0.45f
    private const val DEPTH_FORWARD = 1
    private const val DEPTH_REVERSE = 2
    private const val DEPTH_UNKNOWN = 3
    private const val RING_IN_POST = 1
    private const val RING_MASKED = 2
    private const val RING_SHIFT = 4
    private const val NEAR = 0.05f
    //? if >= 26.2 {
    private val NATIVE_DEPTH = DepthModel.REVERSE
    //?} else {
    /*private val NATIVE_DEPTH = DepthModel.FORWARD
    *///?}
    //? if = 1.8.9 {
    /*private const val SQRT_TWO = 1.4142135f
    private const val DEPTH_COMPONENT = 0x1902
    private const val DEPTH_COMPONENT24 = 0x81A6
    private var depthTexture = 0
    private var depthSize = 0 to 0
    private var depthFresh = false
    private const val FLAGS_DEPTH_AT = 7 * 4 * Float.SIZE_BYTES + 3 * Float.SIZE_BYTES
    *///?}
    private val data = MemoryUtil.memAlloc(BYTES)
    private val glass = MemoryUtil.memAlloc(LobbyGlass.BLOCK_BYTES)
    private val fogSeen = Vector3f(1f, 1f, 1f)

    private fun skyLight(color: Vector3f): Vector3f = if (mood.interior) color else color.lerp(NIGHT_LIGHT, 0.34f * mood.night)

    private fun sky(color: Vector3f): Vector3f = if (mood.interior) color else color.add(Vector3f(NIGHT_SKY).mul(mood.night))

    private fun fog(color: Vector3f): Vector3f =
        if (mood.interior) color.also { fogSeen.set(it) } else color.lerp(DAY_HAZE, 0.45f * (1f - mood.night) * (1f - mood.golden)).lerp(NIGHT_FOG, 0.7f * mood.night).also { fogSeen.set(it) }

    @JvmStatic
    fun skyLightFactor(value: Float): Float = value.coerceAtLeast(MOONLIGHT * mood.night)

    //? if >= 1.21.11 {
    @JvmStatic
    fun adjust(attribute: EnvironmentAttribute<*>, value: Any?): Any? = when {
        attribute === EnvironmentAttributes.SKY_LIGHT_COLOR -> tint(value, ::skyLight)
        attribute === EnvironmentAttributes.SKY_LIGHT_FACTOR -> skyLightFactor(value as Float)
        attribute === EnvironmentAttributes.SKY_COLOR -> tint(value, ::sky)
        attribute === EnvironmentAttributes.FOG_COLOR -> tint(value, ::fog)
        else -> value
    }
    //?} else {
    /*@JvmStatic
    fun skyLightColor(color: Vector3fc): Vector3fc = skyLight(Vector3f(color))

    @JvmStatic
    fun skyColor(argb: Int): Int = tint(argb, ::sky) as Int

    @JvmStatic
    fun fogColor(argb: Int): Int = tint(argb, ::fog) as Int
    *///?}

    //? if < 1.21.4 {
    /*@JvmStatic
    fun skyColor(color: Vec3): Vec3 {
        val tinted = sky(Vector3f(color.x.toFloat(), color.y.toFloat(), color.z.toFloat()))
        return Vec3(tinted.x.toDouble(), tinted.y.toDouble(), tinted.z.toDouble())
    }

    @JvmStatic
    fun notGamma(channel: Float, color: Vector3f): Float {
        val top = maxOf(color.x, color.y, color.z)
        val inverted = 1f - top
        return channel * ((1f - inverted * inverted * inverted * inverted) / top)
    }
    *///?}

    //? if < 1.21.8 {
    /*@JvmStatic
    fun fogColor(color: Vector4f): Vector4f {
        val tinted = fog(Vector3f(color.x, color.y, color.z))
        return Vector4f(tinted.x, tinted.y, tinted.z, color.w)
    }
    *///?}

    //? if >= 26.3 {
    private inline fun tint(value: Any?, change: (Vector3f) -> Vector3f): Any? = change(Vector3f(value as Vector3fc))
    //?} else {
    /*private inline fun tint(value: Any?, change: (Vector3f) -> Vector3f): Any? {
        val argb = value as Int
        val color = change(Vector3f((argb shr 16 and 0xFF) / 255f, (argb shr 8 and 0xFF) / 255f, (argb and 0xFF) / 255f))
        return (argb and -0x1000000) or (channel(color.x) shl 16) or (channel(color.y) shl 8) or channel(color.z)
    }

    private fun channel(value: Float): Int = Mth.floor(value.coerceIn(0f, 1f) * 255f)
    *///?}

    fun upload(mc: Minecraft, fov: Float, forward: Vec3, right: Vec3, up: Vec3, seconds: Float, focus: Float, mood: LobbyMood) {
        val tanHalf = tan(Math.toRadians(fov / 2.0)).toFloat()
        val aspect = mc.window.width.toFloat() / mc.window.height.coerceAtLeast(1)
        data.clear()
        put(forward, tanHalf)
        put(right, aspect)
        put(up, seconds)
        //? if >= 1.21.8 {
        val depthFar = maxOf(mc.options.effectiveRenderDistance * 64f, mc.options.cloudRange().get() * 16f)
        //?} elif > 1.8.9 {
        /*val depthFar = mc.gameRenderer.depthFar
        *///?} else {
        /*val depthFar = mc.options.viewDistance * 16f * SQRT_TWO
        *///?}
        val bridge = LobbyShaderBridges.current
        val pack = bridge.packActive()
        val model = if (pack) bridge.depthModel() else NATIVE_DEPTH
        if (model == NATIVE_DEPTH || model == DepthModel.UNKNOWN) {
            data.putFloat(1f).putFloat(0f).putFloat(NEAR).putFloat(depthFar)
        } else {
            data.putFloat(1f).putFloat(0f).putFloat(depthFar).putFloat(NEAR)
        }
        data.putFloat(mood.aurora).putFloat(mood.night).putFloat(mood.exposure).putFloat(mood.golden)
        val lens = if (pack && bridge.packDepthOfField()) DEPTH_OF_FIELD * PACK_DEPTH_OF_FIELD else DEPTH_OF_FIELD
        val depthOfField = if (mood.interior) lens * INTERIOR_DEPTH_OF_FIELD else lens
        data.putFloat(focus).putFloat(depthOfField).putFloat(LobbyReveal.shaderMode(LobbyScene.introProgress())).putFloat(LobbyScene.introProgress())
        val wave = LobbyWaveState.vector()
        data.putFloat(wave.x).putFloat(wave.y).putFloat(wave.z).putFloat(wave.w)
        if (pack) {
            data.putFloat(fogSeen.x).putFloat(fogSeen.y).putFloat(fogSeen.z).putFloat(flags(bridge, model))
        } else {
            data.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
        }
        data.flip()
        //? if >= 1.21.8 {
        val chain = mc.shaderManager.getPostChain(POST_EFFECT, LevelTargetBundle.MAIN_TARGETS) ?: return
        val device = RenderSystem.getDevice()
        for (pass in (chain as PostChainAccessor).`polyplus$passes`()) {
            val uniforms = (pass as PostPassAccessor).`polyplus$customUniforms`()
            var buffer = uniforms[BLOCK] ?: continue
            if (buffer.usage() and GpuBuffer.USAGE_COPY_DST == 0) {
                buffer.close()
                buffer = device.createBuffer({ "polyplus lobby sky" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, bufferSize(BYTES))
                uniforms[BLOCK] = buffer
            }
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data)
            data.rewind()
        }
        uploadGlass(chain as PostChainAccessor)
        //?}
    }

    //? if >= 1.21.8 {
    private fun uploadGlass(chain: PostChainAccessor) {
        val device = RenderSystem.getDevice()
        glass.clear()
        LobbyGlass.write(glass)
        glass.flip()
        for (pass in chain.`polyplus$passes`()) {
            val uniforms = (pass as PostPassAccessor).`polyplus$customUniforms`()
            var buffer = uniforms[GLASS_BLOCK] ?: continue
            if (buffer.size() != bufferSize(LobbyGlass.BLOCK_BYTES)) {
                buffer.close()
                buffer = device.createBuffer({ "polyplus lobby glass" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, bufferSize(LobbyGlass.BLOCK_BYTES))
                uniforms[GLASS_BLOCK] = buffer
            }
            device.createCommandEncoder().writeToBuffer(buffer.slice(), glass)
            glass.rewind()
        }
    }
    //?}

    //? if < 1.21.4 {
    /*private var chain: PostChain? = null
    private var chainFailed = false

    @JvmStatic
    fun release() {
        chain?.close()
        chain = null
    }

    private fun loadChain(renderer: GameRenderer): PostChain? {
        if (chainFailed) return null
        val mc = Minecraft.getInstance()
        return runCatching { PostChain(mc.textureManager, mc.resourceManager, LobbyCompat.mainTarget(renderer), Identifier.fromNamespaceAndPath("polyplus", "shaders/post/lobby_chain.json")) }
            .onFailure {
                chainFailed = true
                LOBBY_LOGGER.warn("Lobby post effects disabled, the shader chain failed to load", it)
            }
            .getOrNull()
    }

    fun process(renderer: GameRenderer) {
        val target = LobbyCompat.mainTarget(renderer)
        val loaded = chain ?: (loadChain(renderer) ?: return target.bindWrite(true)).also {
            it.resize(target.width, target.height)
            chain = it
            sized = target.width to target.height
            //? if = 1.8.9
            /*(it as PostChainAccessor).`polyplus$passes`().forEach { pass -> pass.addAuxAsset("DepthSampler", depthTexture, target.width, target.height) }*/
        }
        if (sized != target.width to target.height) {
            loaded.resize(target.width, target.height)
            sized = target.width to target.height
        }
        glass.clear()
        LobbyGlass.write(glass)
        glass.flip()
        //? if = 1.8.9
        /*if (!depthFresh) data.putFloat(FLAGS_DEPTH_AT, DEPTH_UNKNOWN.toFloat())*/
        for (pass in (loaded as PostChainAccessor).`polyplus$passes`()) {
            setAll(pass.effect, SKY_NAMES, data)
            setAll(pass.effect, GLASS_NAMES, glass)
        }
        //? if = 1.8.9 {
        /*GlStateManager.disableCull()
        GlStateManager.matrixMode(GL11.GL_TEXTURE)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        loaded.process(0f)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.enableCull()
        depthFresh = false
        *///?} else {
        loaded.process(0f)
        //?}
        target.bindWrite(true)
    }

    private var sized = 0 to 0

    //? if = 1.8.9 {
    /*@JvmStatic
    fun captureDepth() {
        val target = Minecraft.getInstance().renderTarget
        val width = target.width
        val height = target.height
        val resized = depthSize != width to height
        if (resized) release()
        if (depthTexture == 0) depthTexture = GL11.glGenTextures()
        GlStateManager.bindTexture(depthTexture)
        if (resized) {
            depthSize = width to height
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, DEPTH_COMPONENT24, width, height, 0, DEPTH_COMPONENT, GL11.GL_FLOAT, null as ByteBuffer?)
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
        GlStateManager.bindTexture(0)
        depthFresh = true
    }
    *///?}

    private fun setAll(effect: EffectInstance, names: List<String>, source: ByteBuffer) {
        names.forEachIndexed { index, name ->
            val at = index * 4 * Float.SIZE_BYTES
            effect.getUniform(name)?.set(source.getFloat(at), source.getFloat(at + 4), source.getFloat(at + 8), source.getFloat(at + 12))
        }
    }
    *///?} elif < 26.3 {
    /*fun process(renderer: GameRenderer, resources: GraphicsResourceAllocator) {
        val chain = Minecraft.getInstance().shaderManager.getPostChain(POST_EFFECT, LevelTargetBundle.MAIN_TARGETS) ?: return
        //? if >= 1.21.8 {
        chain.process(LobbyCompat.mainTarget(renderer), resources)
        //?} elif >= 1.21.5 {
        /^glass.clear()
        LobbyGlass.write(glass)
        glass.flip()
        chain.process(LobbyCompat.mainTarget(renderer), resources) { pass ->
            setAll(pass, SKY_NAMES, data)
            setAll(pass, GLASS_NAMES, glass)
        }
        ^///?} else {
        /^glass.clear()
        LobbyGlass.write(glass)
        glass.flip()
        for (pass in (chain as PostChainAccessor).`polyplus$passes`()) {
            val shader = (pass as PostPassAccessor).`polyplus$shader`()
            setAll(shader, SKY_NAMES, data)
            setAll(shader, GLASS_NAMES, glass)
        }
        chain.process(LobbyCompat.mainTarget(renderer), resources)
        LobbyCompat.mainTarget(renderer).bindWrite(true)
        ^///?}
    }
    *///?}

    //? if = 1.8.9 {
    /*private const val BLOCK_FLICKER_BASE = 1.5f
    private const val LIGHT_CELLS = 16
    private const val GRAY_POINT = 0.75f
    private const val GRAY_SHARE = 0.04f
    private const val SKY_DARK_FLOOR = 0.95f
    private const val SKY_DARK_BASE = 0.05f
    private const val MIN_GAMMA = 0.5f

    private fun cellBrightness(level: Int): Float {
        val value = level / (LIGHT_CELLS - 1f)
        return value / (4f - 3f * value)
    }

    fun legacyLightmap(level: World, pixels: IntArray, flicker: Float) {
        val darken = level.calculateAmbientLight(1f)
        val skyFactor = skyLightFactor(darken * SKY_DARK_FLOOR + SKY_DARK_BASE)
        val blockFactor = flicker * 0.1f + BLOCK_FLICKER_BASE
        val skyTint = skyLight(Vector3f(darken, darken, 1f).lerp(Vector3f(1f, 1f, 1f), 0.35f))
        val gamma = maxOf(Minecraft.getInstance().options.gamma, MIN_GAMMA)
        for (sky in 0 until LIGHT_CELLS) {
            for (block in 0 until LIGHT_CELLS) {
                val skyLevel = cellBrightness(sky) * skyFactor
                val blockLevel = cellBrightness(block) * blockFactor
                val color = Vector3f(blockLevel, blockLevel * ((blockLevel * 0.6f + 0.4f) * 0.6f + 0.4f), blockLevel * (blockLevel * blockLevel * 0.6f + 0.4f))
                color.add(Vector3f(skyTint).mul(skyLevel))
                color.lerp(Vector3f(GRAY_POINT, GRAY_POINT, GRAY_POINT), GRAY_SHARE)
                color.min(Vector3f(1f, 1f, 1f))
                val lifted = Vector3f(notGamma(color.x, color), notGamma(color.y, color), notGamma(color.z, color))
                color.lerp(lifted, gamma)
                pixels[sky * LIGHT_CELLS + block] = (-0x1000000) or (channel(color.x) shl 16) or (channel(color.y) shl 8) or channel(color.z)
            }
        }
    }
    *///?}

    //? if = 1.21.4 {
    /*private fun setAll(shader: CompiledShaderProgram, names: List<String>, source: ByteBuffer) {
        names.forEachIndexed { index, name ->
            val at = index * 4 * Float.SIZE_BYTES
            shader.getUniform(name)?.set(source.getFloat(at), source.getFloat(at + 4), source.getFloat(at + 8), source.getFloat(at + 12))
        }
    }

    @JvmStatic
    fun nightUniform(shader: CompiledShaderProgram): CompiledShaderProgram {
        shader.getUniform("LobbyNight")?.set(NIGHT_LIGHT.x, NIGHT_LIGHT.y, NIGHT_LIGHT.z, MOONLIGHT * mood.night)
        return shader
    }
    *///?}

    //? if < 1.21.8 && >= 1.21.5 {
    /*private fun setAll(pass: RenderPass, names: List<String>, source: ByteBuffer) {
        names.forEachIndexed { index, name ->
            val at = index * 4 * Float.SIZE_BYTES
            pass.setUniform(name, source.getFloat(at), source.getFloat(at + 4), source.getFloat(at + 8), source.getFloat(at + 12))
        }
    }

    @JvmStatic
    fun nightUniform(pass: RenderPass) {
        pass.setUniform("LobbyNight", NIGHT_LIGHT.x, NIGHT_LIGHT.y, NIGHT_LIGHT.z, MOONLIGHT * mood.night)
    }
    *///?}

    //? if >= 1.21.11 {
    private fun bufferSize(bytes: Int): Long = bytes.toLong()
    //?} else {
    /*private fun bufferSize(bytes: Int): Int = bytes
    *///?}

    private fun flags(bridge: LobbyShaderBridge, model: DepthModel): Float {
        val depth = when (model) {
            DepthModel.FORWARD -> DEPTH_FORWARD
            DepthModel.REVERSE -> DEPTH_REVERSE
            DepthModel.UNKNOWN -> return DEPTH_UNKNOWN.toFloat()
        }
        val ring = when {
            !bridge.revealInTerrain() -> RING_MASKED
            bridge.ringInPost() -> RING_IN_POST
            else -> 0
        }
        return (depth + ring * RING_SHIFT).toFloat()
    }

    private fun put(vector: Vec3, w: Float) {
        data.putFloat(vector.x.toFloat()).putFloat(vector.y.toFloat()).putFloat(vector.z.toFloat()).putFloat(w)
    }
}
