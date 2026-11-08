package org.polyfrost.polyplus.client.lobby

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

object LobbyLegacyRender {
    private var program = 0
    var failed = false
        private set
    private var hologram = 0
    private var hologramFailed = false
    private var right = FloatArray(3)
    private var up = FloatArray(3)
    private var forward = FloatArray(3)

    fun pose(look: Vec3Float, across: Vec3Float, above: Vec3Float) {
        forward = look
        right = across
        up = above
    }

    @JvmStatic
    fun bindTerrain() {
        if (failed) return
        if (program == 0) program = runCatching { build("lobby_terrain") }.onFailure {
            failed = true
            LOBBY_LOGGER.warn("Terrain shader unavailable: {}", it.message)
        }.getOrDefault(0)
        if (program == 0) return
        GL20.glUseProgram(program)
        GL20.glUniform1i(location("Sampler0"), 0)
        GL20.glUniform1i(location("Sampler2"), 1)
        GL20.glUniform1f(location("Seconds"), LobbyScene.waveSeconds().toFloat())
        GL20.glUniform1f(location("AimDistance"), LobbyScene.aimDistance())
        GL20.glUniform3f(location("Right"), right[0], right[1], right[2])
        GL20.glUniform3f(location("Up"), up[0], up[1], up[2])
        GL20.glUniform3f(location("Forward"), forward[0], forward[1], forward[2])
    }

    @JvmStatic
    fun unbindTerrain() {
        if (program != 0) GL20.glUseProgram(0)
    }

    fun bindHologram(): Boolean {
        if (hologramFailed) return false
        if (hologram == 0) hologram = runCatching { build("lobby_hologram") }.onFailure {
            hologramFailed = true
            LOBBY_LOGGER.warn("Hologram shader unavailable: {}", it.message)
        }.getOrDefault(0)
        if (hologram == 0) return false
        GL20.glUseProgram(hologram)
        return true
    }

    fun unbindHologram() = GL20.glUseProgram(0)

    fun uniform(name: String) = GL20.glGetUniformLocation(hologram, name)

    @JvmStatic
    fun restoreGuiState(renderer: GameRenderer) {
        val mc = Minecraft.getInstance()
        GlStateManager.viewport(0, 0, mc.width, mc.height)
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.loadIdentity()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.loadIdentity()
        renderer.setupGuiState()
        GlStateManager.disableFog()
        GlStateManager.disableLighting()
        GlStateManager.enableAlphaTest()
        GlStateManager.color4f(1f, 1f, 1f, 1f)
    }

    private fun location(name: String) = GL20.glGetUniformLocation(program, name)

    private fun build(name: String): Int {
        val id = GL20.glCreateProgram()
        GL20.glAttachShader(id, compile(GL20.GL_VERTEX_SHADER, "$name.vsh"))
        GL20.glAttachShader(id, compile(GL20.GL_FRAGMENT_SHADER, "$name.fsh"))
        GL20.glLinkProgram(id)
        check(GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) { GL20.glGetProgramInfoLog(id) }
        return id
    }

    private fun compile(type: Int, name: String): Int {
        val source = checkNotNull(LobbyLegacyRender::class.java.getResourceAsStream("/assets/polyplus/shaders/legacy/$name")).use { it.readBytes().decodeToString() }
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, source)
        GL20.glCompileShader(id)
        check(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) { GL20.glGetShaderInfoLog(id) }
        return id
    }
}

typealias Vec3Float = FloatArray
*///?}
