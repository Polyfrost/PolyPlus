package org.polyfrost.polyplus.compat.iris

//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlTexture
//?} elif >= 1.21.5 {
/*import com.mojang.blaze3d.opengl.GlTexture
*///?}
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import org.polyfrost.polyplus.client.lobby.LobbyWaveState
import org.polyfrost.polyplus.client.lobby.shader.DepthModel

object IrisDepthModel {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private const val SETTLE_FRAMES = 20
    private const val GIVE_UP_FRAMES = 240
    private const val SKY_ROW = 0.97f
    private const val TERRAIN_ROW = 0.03f
    private const val MIDPOINT = 0.5f

    private class Texels(val sky: Float, val terrain: Float)

    private val prior: DepthModel by lazy { api() ?: versionDefault() }

    @Volatile
    private var probed: DepthModel? = null

    @Volatile
    private var armed = false

    private var frames = 0
    private var framebuffer = 0

    @JvmStatic
    fun depthModel(): DepthModel {
        probed?.let { return it }
        armed = true
        return prior
    }

    @JvmStatic
    fun reset() {
        probed = null
        frames = 0
    }

    @JvmStatic
    fun afterFinalPass() {
        if (!armed) return
        if (!LobbyWaveState.active()) frames = 0 else if (++frames >= SETTLE_FRAMES) probe()
    }

    private fun probe() {
        runCatching { sample() }
            .onSuccess(::judge)
            .onFailure {
                settle(DepthModel.UNKNOWN)
                LOGGER.warn("Iris depth probe failed, lobby post runs without depth effects", it)
            }
    }

    private fun judge(texels: Texels) {
        val model = decide(texels)
        when {
            model != null -> {
                settle(model)
                LOGGER.info("Iris depth probe: sky {} terrain {} -> {} (api {}, default {})", texels.sky, texels.terrain, model, api(), versionDefault())
            }
            frames >= GIVE_UP_FRAMES -> {
                settle(DepthModel.UNKNOWN)
                LOGGER.warn("Iris depth probe undecided: sky {} terrain {}, lobby post runs without depth effects", texels.sky, texels.terrain)
            }
        }
    }

    private fun settle(model: DepthModel) {
        probed = model
        armed = false
        frames = 0
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer)
            framebuffer = 0
        }
    }

    private fun decide(texels: Texels): DepthModel? {
        val sky = vote(texels.sky)
        return if (sky == vote(texels.terrain)) sky else null
    }

    private fun vote(depth: Float): DepthModel = if (depth > MIDPOINT) DepthModel.FORWARD else DepthModel.REVERSE

    private fun sample(): Texels {
        val target = LobbyCompat.mainTarget(Minecraft.getInstance().gameRenderer)
        //? if >= 1.21.5 {
        val texture = (target.depthTexture as? GlTexture)?.glId() ?: error("Main depth is not a GL texture")
        //?} else {
        /*val texture = target.depthTextureId
        *///?}
        if (framebuffer == 0) framebuffer = GL30.glGenFramebuffers()
        val previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val previousPack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)
        val rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH)
        val skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)
        val skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS)
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer)
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0)
            GL11.glReadBuffer(GL11.GL_NONE)
            val status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)
            check(status == GL30.GL_FRAMEBUFFER_COMPLETE) { "Depth probe framebuffer incomplete: $status" }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0)
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0)
            val x = target.width / 2
            return Texels(read(x, (target.height * SKY_ROW).toInt()), read(x, (target.height * TERRAIN_ROW).toInt()))
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength)
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows)
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels)
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead)
        }
    }

    private fun read(x: Int, y: Int): Float = MemoryStack.stackPush().use { stack ->
        val texel = stack.mallocFloat(1)
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, texel)
        texel.get(0)
    }

    private fun api(): DepthModel? = runCatching {
        val type = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
        val reverse = type.getMethod("isReverseZDuringShaders").invoke(type.getMethod("getInstance").invoke(null)) as Boolean
        if (reverse) DepthModel.REVERSE else DepthModel.FORWARD
    }.getOrNull()

    private fun versionDefault(): DepthModel {
        //? if >= 26.2 {
        return DepthModel.REVERSE
        //?} else {
        /*return DepthModel.FORWARD
        *///?}
    }
}
