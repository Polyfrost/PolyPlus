package org.polyfrost.polyplus.client.lobby

//? if >= 26.2 {
import com.mojang.blaze3d.systems.RenderSystem
//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.ShaderType
import com.mojang.renderpearl.api.pipeline.UniformType
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderPass
*///?}
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil

object LobbySodiumUniforms {
    const val BLOCK = "PolyLobbyWave"
    private const val NAMESPACE = "polyplus"
    private const val PREFIX = "sodium_lobby/"
    private const val SODIUM = "sodium"
    private const val MAX_BUFFER_FAILURES = 3
    private const val BYTES = 4 * Float.SIZE_BYTES

    private val data = MemoryUtil.memAlloc(BYTES)
    private val CHUNK_FADE = Regex("""\bfadeFactor\s*=\s*([^;]*);""")

    @JvmField
    val LAYOUT: BindGroupLayout = BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build()

    @JvmField
    var bound = false

    @Volatile
    private var hooked = false
    private var buffer: GpuBuffer? = null
    private var bufferFailures = 0
    private var lastSeconds = Float.NaN
    private var lastAim = Float.NaN

    @JvmStatic
    fun lobbyId(id: Identifier): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, PREFIX + id.path)

    @JvmStatic
    fun lobbyPipelineId(id: Identifier): Identifier = Identifier.fromNamespaceAndPath("${NAMESPACE}_$SODIUM", "lobby/" + id.path)

    @JvmStatic
    fun lobbySuffix(suffix: String): String = "${NAMESPACE}_lobby_$suffix"

    @JvmStatic
    fun sodiumId(id: Identifier): Identifier? =
        if (id.namespace == NAMESPACE && id.path.startsWith(PREFIX)) Identifier.fromNamespaceAndPath(SODIUM, id.path.substring(PREFIX.length)) else null

    @JvmStatic
    fun patch(source: String?, type: ShaderType): String? = when {
        source == null -> null
        type == ShaderType.VERTEX -> LobbySodiumWave.patchVertex(source, LobbySodiumWave.Flavour.PIPELINE)
            ?.replace(CHUNK_FADE, "fadeFactor = u_PolyLobbyWave.x >= 0.0 ? 1.0 : (\$1);")
        else -> LobbySodiumWave.patchFragment(source, LobbySodiumWave.Flavour.PIPELINE)
    }

    @JvmStatic
    fun ready(): Boolean = hooked && buffer != null

    @JvmStatic
    fun built(location: Any) {
        LOBBY_LOGGER.info("Built Sodium lobby pipeline {}", location)
    }

    @JvmStatic
    fun upload() {
        val seconds = LobbySodiumWave.seconds()
        val aim = LobbySodiumWave.aim()
        val device = RenderSystem.getDevice()
        val target = buffer ?: runCatching {
            device.createBuffer({ "polyplus sodium lobby wave" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, BYTES.toLong())
        }.onFailure { if (++bufferFailures >= MAX_BUFFER_FAILURES) LobbySodiumWave.fail("wave uniform buffer", it) }.getOrNull()?.also { buffer = it } ?: return
        if (seconds == lastSeconds && aim == lastAim) return
        data.clear()
        data.putFloat(seconds).putFloat(aim).putFloat(0f).putFloat(0f)
        data.flip()
        device.createCommandEncoder().writeToBuffer(target.slice(), data)
        lastSeconds = seconds
        lastAim = aim
    }

    @JvmStatic
    fun bind(pass: RenderPass) {
        if (!hooked) hooked = true
        if (!bound || !LobbySodiumWave.active()) return
        buffer?.let { pass.setUniform(BLOCK, it) }
    }
}
//?}
