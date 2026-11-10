package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.DynamicTexture
import net.minecraft.client.resource.manager.ReloadableResourceManager
import net.minecraft.client.resource.manager.ResourceReloadListener
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GLContext
import org.polyfrost.polyplus.client.nameplate.render.NameplateCanvas
import org.polyfrost.polyplus.client.nameplate.render.NameplateFrame
import org.polyfrost.polyplus.client.nameplate.render.NameplateVertexSink
import org.polyfrost.polyplus.client.nameplate.render.PortraitGuard
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialAssets
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialOp
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.roundToInt

object LegacyMaterialGpu {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    val DIALECT: GlslDialect = GlslDialect.COMPAT_130

    private const val GPU = "GPU"
    private const val FORCED = "forced"
    private const val NO_SHADERS = "no GLSL support"
    private const val SHADER_PACK = "shader pack"
    private const val PENDING = "compile pending"
    private const val FAILED = "compile failed"
    private const val NO_TEXTURES = "textures not ready"
    private const val LIGHTMAP_UNIT = 1
    private const val FIRST_UNIT = 2
    private const val LANES = 4
    private const val OPAQUE = 255
    private const val CHANNEL = 255f
    private const val ALPHA_SHIFT = 24
    private const val RGB = 0xFFFFFF
    private const val GRAY = 0x010101
    private const val INFO_LOG = 32768
    private const val COMPILE_NANOS = 16_000_000L
    private const val SWEEP_NANOS = 1_000_000_000L
    private const val GRACE_NANOS = 30_000_000_000L
    private const val FOG_OFF = 0
    private const val FOG_LINEAR = 1
    private const val FOG_EXP = 2
    private const val FOG_EXP2 = 3
    private const val GL_EXP2 = 0x0801

    private val forceCpu: Boolean = java.lang.Boolean.getBoolean(NameplateCanvas.CPU_PROPERTY)

    class Program internal constructor(
        val key: String,
        internal val id: Int,
        val additive: Boolean,
        private val locations: IntArray,
    ) {
        internal var lastUsed = 0L

        internal fun location(uniform: Int): Int = locations[uniform]
    }

    class Draw internal constructor(val program: Program, internal val words: IntArray, internal val textures: IntArray) {
        val additive: Boolean
            get() = program.additive
    }

    private class Uploaded(val texture: DynamicTexture) {
        var lastUsed = 0L
    }

    private val UNIFORMS = listOf("AreaRect", "BodyRect", "NameplateClock", "Inputs", "AtlasRects", "Tones", "ScreenSize", "NameplateFogMode", "NameplateLit")
    private const val AREA = 0
    private const val BODY = 1
    private const val CLOCK = 2
    private const val INPUTS = 3
    private const val ATLAS = 4
    private const val TONES = 5
    private const val SCREEN = 6
    private const val FOG = 7
    private const val LIT = 8
    private const val VECTORS = 4

    private val programs = HashMap<String, Program>()
    private val requested = HashMap<String, Long>()
    private val failed = HashSet<String>()
    private val uploads = IdentityHashMap<TexturePixels, Uploaded>()
    private val atlasRects = WeakHashMap<MaterialLayer, FloatArray?>()
    private val clips = WeakHashMap<PixelImage, IntArray>()
    private val modes = WeakHashMap<MaterialLayer, String>()
    private val ints: IntBuffer = BufferUtils.createIntBuffer(LANES)
    private var floats: FloatBuffer = BufferUtils.createFloatBuffer(LANES * MaterialLimits.MAX_TEXTURE_SAMPLES)
    private var supported: Boolean? = null
    private var listening = false
    private var lastCompile = 0L
    private var lastSweep = 0L

    fun prepare(bundle: NameplateBundle, op: LayerPainter.Painted, material: MaterialOp, palette: ResolvedPalette): Draw? {
        if (op.opacity <= 0 || !LegacyVertexSink.isRecording || NameplateIris.shadowPass()) {
            return null
        }
        val now = System.nanoTime()
        sweep(now)
        val layer = material.layer
        var program: Program? = null
        var textures: IntArray? = null
        val mode = when {
            forceCpu -> FORCED
            !supported() -> NO_SHADERS
            !NameplateIris.gpuMaterials() -> SHADER_PACK
            else -> {
                val shader = material.shader(DIALECT)
                program = program(shader, material.material, now)
                textures = if (program == null) null else textures(bundle, layer, now)
                when {
                    textures != null -> GPU
                    program != null -> NO_TEXTURES
                    shader.key in failed -> FAILED
                    else -> PENDING
                }
            }
        }
        report(bundle, material, layer, mode)
        if (program == null || textures == null) {
            return null
        }
        program.lastUsed = now
        val words = material.uniforms(palette, atlasRects(layer), DIALECT)
        words[MaterialUniforms.CLOCK] = material.clock
        return Draw(program, words, textures)
    }

    fun submit(frame: NameplateFrame, op: LayerPainter.Painted, material: MaterialOp, draw: Draw) {
        val clip = op.clip
        val area = material.area
        val local = if (clip == null) {
            area
        } else {
            PixelRect(maxOf(area.x0, clip.x0), maxOf(area.y0, clip.y0), minOf(area.x1, clip.x1), minOf(area.y1, clip.y1))
        }
        if (local.width <= 0 || local.height <= 0) {
            return
        }
        val screen = if (op.mirrorX) {
            PixelRect(op.x + op.width - local.x1, op.y + local.y0, op.x + op.width - local.x0, op.y + local.y1)
        } else {
            PixelRect(op.x + local.x0, op.y + local.y0, op.x + local.x1, op.y + local.y1)
        }
        val fade = frame.alpha * op.opacity.coerceIn(0, OPAQUE) / CHANNEL
        if (fade <= 0f) {
            return
        }
        val color = PortraitGuard.vertex(vertexColor(draw.additive, fade))
        val rects = material.clip?.let { region -> clips.getOrPut(region) { material.clipRects() } }
        val z = NameplateCanvas.depth(op)
        val mirror = op.mirrorX
        LegacyVertexSink.drawMaterial(draw, frame.x, frame.y) { _, buffer ->
            if (rects == null) {
                quad(buffer, screen, local, mirror, local.x0, local.y0, local.x1, local.y1, z, color)
            } else {
                for (i in rects.indices step MaterialOp.CLIP_STRIDE) {
                    quad(buffer, screen, local, mirror, rects[i], rects[i + 1], rects[i + 2], rects[i + 3], z, color)
                }
            }
        }
    }

    internal fun begin(draw: Draw) {
        val program = draw.program
        GLX.useProgram(program.id)
        val words = draw.words
        vec4s(program.location(AREA), words, MaterialUniforms.AREA, 1)
        vec4s(program.location(BODY), words, MaterialUniforms.BODY, 1)
        val clock = program.location(CLOCK)
        if (clock >= 0) {
            ints.clear()
            ints.put(words, MaterialUniforms.CLOCK, LANES).flip()
            GLX.uniform4(clock, ints)
        }
        vec4s(program.location(INPUTS), words, MaterialUniforms.INPUTS, VECTORS)
        vec4s(program.location(ATLAS), words, MaterialUniforms.ATLAS, VECTORS)
        vec4s(program.location(TONES), words, MaterialUniforms.TONES, (words.size - MaterialUniforms.TONES) / LANES)
        val screen = program.location(SCREEN)
        if (screen >= 0) {
            val minecraft = Minecraft.getInstance()
            floats.clear()
            floats.put(minecraft.width.toFloat()).put(minecraft.height.toFloat()).flip()
            GLX.uniform2(screen, floats)
        }
        val fog = program.location(FOG)
        if (fog >= 0) {
            GLX.uniform1i(fog, fogMode())
        }
        val lit = program.location(LIT)
        if (lit >= 0) {
            GLX.uniform1i(lit, if (lightMapEnabled()) 1 else 0)
        }
        for ((k, texture) in draw.textures.withIndex()) {
            GlStateManager.activeTexture(GLX.GL_TEXTURE0 + FIRST_UNIT + k)
            GlStateManager.bindTexture(texture)
        }
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        if (program.additive) {
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO)
        }
    }

    internal fun end(draw: Draw) {
        GLX.useProgram(0)
        for (k in draw.textures.indices) {
            GlStateManager.activeTexture(GLX.GL_TEXTURE0 + FIRST_UNIT + k)
            GlStateManager.bindTexture(0)
        }
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        if (draw.program.additive) {
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        }
    }

    @JvmStatic
    fun releaseAll() {
        for (program in programs.values) {
            GLX.deleteProgram(program.id)
        }
        programs.clear()
        requested.clear()
        failed.clear()
        for (upload in uploads.values) {
            upload.texture.clearGlId()
        }
        uploads.clear()
        atlasRects.clear()
        modes.clear()
    }

    private fun supported(): Boolean = supported ?: run {
        val caps = GLContext.getCapabilities()
        val found = caps.OpenGL21 || (caps.GL_ARB_shader_objects && caps.GL_ARB_vertex_shader && caps.GL_ARB_fragment_shader)
        if (!found) {
            LOGGER.info("OpenGL shaders are not available; nameplate effects use the CPU fallback")
        }
        supported = found
        found
    }

    private fun program(shader: MaterialShader, material: Material, now: Long): Program? {
        val key = shader.key
        programs[key]?.let { return it }
        if (key in failed) {
            return null
        }
        val since = requested.getOrPut(key) { now }
        if (now - since < COMPILE_NANOS || now - lastCompile < COMPILE_NANOS) {
            return null
        }
        lastCompile = now
        listen()
        val program = compile(shader, material)
        if (program == null) {
            failed += key
            return null
        }
        programs[key] = program
        requested -= key
        return program
    }

    private fun compile(shader: MaterialShader, material: Material): Program? {
        val vertex = stage(GLX.GL_VERTEX_SHADER, shader.vertex, shader.key) ?: return null
        val fragment = stage(GLX.GL_FRAGMENT_SHADER, shader.fragment, shader.key)
        if (fragment == null) {
            GLX.deleteShader(vertex)
            return null
        }
        val id = GLX.createProgram()
        GLX.attachShader(id, vertex)
        GLX.attachShader(id, fragment)
        GLX.linkProgram(id)
        GLX.deleteShader(vertex)
        GLX.deleteShader(fragment)
        if (GLX.getProgram(id, GLX.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.error("Nameplate material program {} failed to link; it uses the CPU fallback: {}", shader.key, GLX.getProgramInfoLog(id, INFO_LOG).trim())
            GLX.deleteProgram(id)
            return null
        }
        GLX.useProgram(id)
        GLX.uniform1i(GLX.getUniformLocation(id, "NameplateLightmap"), LIGHTMAP_UNIT)
        for ((k, sampler) in shader.samplers.withIndex()) {
            GLX.uniform1i(GLX.getUniformLocation(id, sampler), FIRST_UNIT + k)
        }
        GLX.useProgram(0)
        val locations = IntArray(UNIFORMS.size) { GLX.getUniformLocation(id, UNIFORMS[it]) }
        LOGGER.info("Nameplate material program {} compiled for the GPU", shader.key)
        return Program(shader.key, id, material.blend == Material.Blend.ADDITIVE, locations)
    }

    private fun stage(type: Int, source: String, key: String): Int? {
        val bytes = source.toByteArray(Charsets.UTF_8)
        val buffer = BufferUtils.createByteBuffer(bytes.size)
        buffer.put(bytes).flip()
        val id = GLX.createShader(type)
        GLX.shaderSource(id, buffer)
        GLX.compileShader(id)
        if (GLX.getShader(id, GLX.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val stage = if (type == GLX.GL_VERTEX_SHADER) "vertex" else "fragment"
            LOGGER.error("Nameplate material {} shader {} failed to compile; it uses the CPU fallback: {}", stage, key, GLX.getShaderInfoLog(id, INFO_LOG).trim())
            GLX.deleteShader(id)
            return null
        }
        return id
    }

    private fun textures(bundle: NameplateBundle, layer: MaterialLayer, now: Long): IntArray? {
        val refs = layer.material.textures
        if (refs.isEmpty()) {
            return EMPTY
        }
        val ids = IntArray(refs.size)
        for ((k, ref) in refs.withIndex()) {
            ids[k] = when (ref) {
                is TextureRef.Atlas -> Minecraft.getInstance().blocksAtlas?.glId ?: return null
                else -> upload(NameplateMaterialAssets.pixels(bundle, ref) ?: return null, now)
            }
        }
        return ids
    }

    private fun upload(pixels: TexturePixels, now: Long): Int {
        val uploaded = uploads.getOrPut(pixels) {
            val texture = DynamicTexture(pixels.width, pixels.height)
            pixels.frameArgb().copyInto(texture.pixels)
            texture.upload()
            Uploaded(texture)
        }
        uploaded.lastUsed = now
        return uploaded.texture.glId
    }

    private fun atlasRects(layer: MaterialLayer): FloatArray? {
        if (atlasRects.containsKey(layer)) {
            return atlasRects[layer]
        }
        val refs = layer.material.textures
        val rects = if (refs.none { it is TextureRef.Atlas }) {
            null
        } else {
            val atlas = Minecraft.getInstance().blocksAtlas
            val out = FloatArray(MaterialLimits.MAX_TEXTURE_SAMPLES * LANES)
            for ((k, ref) in refs.withIndex()) {
                if (ref !is TextureRef.Atlas || k >= MaterialLimits.MAX_TEXTURE_SAMPLES) continue
                val sprite = atlas.getSprite(NameplateMaterialAssets.atlasSprite(ref.sprite))
                if (sprite === atlas.missingSprite) continue
                val at = k * LANES
                out[at] = sprite.x.toFloat()
                out[at + 1] = sprite.y.toFloat()
                out[at + 2] = sprite.width.toFloat()
                out[at + 3] = sprite.height.toFloat()
            }
            out
        }
        atlasRects[layer] = rects
        return rects
    }

    private fun sweep(now: Long) {
        if (now - lastSweep < SWEEP_NANOS) {
            return
        }
        lastSweep = now
        programs.values.removeAll { program ->
            val idle = now - program.lastUsed >= GRACE_NANOS
            if (idle) {
                GLX.deleteProgram(program.id)
                LOGGER.info("Nameplate material program {} released: no nameplate drew it for a while", program.key)
            }
            idle
        }
        uploads.values.removeAll { upload ->
            val idle = now - upload.lastUsed >= GRACE_NANOS
            if (idle) upload.texture.clearGlId()
            idle
        }
    }

    private fun listen() {
        if (listening) {
            return
        }
        val manager = Minecraft.getInstance().resourceManager as? ReloadableResourceManager ?: return
        manager.addListener(ResourceReloadListener { releaseAll() })
        listening = true
    }

    private fun report(bundle: NameplateBundle, material: MaterialOp, layer: MaterialLayer, mode: String) {
        if (modes[layer] == mode) {
            return
        }
        modes[layer] = mode
        if (mode == GPU) {
            LOGGER.info("Nameplate {} layer {} draws on the GPU", bundle.id, material.layerId)
        } else {
            LOGGER.info("Nameplate {} layer {} draws on the CPU fallback ({})", bundle.id, material.layerId, mode)
        }
    }

    private fun fogMode(): Int {
        if (!GL11.glIsEnabled(GL11.GL_FOG)) {
            return FOG_OFF
        }
        ints.clear()
        GL11.glGetInteger(GL11.GL_FOG_MODE, ints)
        return when (ints.get(0)) {
            GL11.GL_LINEAR -> FOG_LINEAR
            GL11.GL_EXP -> FOG_EXP
            GL_EXP2 -> FOG_EXP2
            else -> FOG_OFF
        }
    }

    private fun lightMapEnabled(): Boolean {
        GlStateManager.activeTexture(GLX.GL_TEXTURE1)
        val enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        return enabled
    }

    private fun vec4s(location: Int, words: IntArray, offset: Int, count: Int) {
        if (location < 0 || count <= 0) {
            return
        }
        val size = count * LANES
        if (floats.capacity() < size) {
            floats = BufferUtils.createFloatBuffer(size)
        }
        floats.clear()
        for (i in offset until offset + size) {
            floats.put(Float.fromBits(words[i]))
        }
        floats.flip()
        GLX.uniform4(location, floats)
    }

    private fun vertexColor(additive: Boolean, fade: Float): Int {
        val channel = (fade * CHANNEL).roundToInt()
        return if (additive) (OPAQUE shl ALPHA_SHIFT) or (channel * GRAY) else (channel shl ALPHA_SHIFT) or RGB
    }

    private fun quad(
        buffer: NameplateVertexSink,
        area: PixelRect,
        local: PixelRect,
        mirrorX: Boolean,
        a0: Int,
        b0: Int,
        a1: Int,
        b1: Int,
        z: Float,
        color: Int,
    ) {
        val x0 = (if (mirrorX) area.x1 - (a1 - local.x0) else area.x0 + (a0 - local.x0)).toFloat()
        val x1 = (if (mirrorX) area.x1 - (a0 - local.x0) else area.x0 + (a1 - local.x0)).toFloat()
        val y0 = (area.y0 + (b0 - local.y0)).toFloat()
        val y1 = (area.y0 + (b1 - local.y0)).toFloat()
        val u0 = (if (mirrorX) a1 else a0).toFloat()
        val u1 = (if (mirrorX) a0 else a1).toFloat()
        val v0 = b0.toFloat()
        val v1 = b1.toFloat()
        buffer.addVertex(x0, y1, z).setUv(u0, v1).setColor(color)
        buffer.addVertex(x1, y1, z).setUv(u1, v1).setColor(color)
        buffer.addVertex(x1, y0, z).setUv(u1, v0).setColor(color)
        buffer.addVertex(x0, y0, z).setUv(u0, v0).setColor(color)
    }

    private val EMPTY = IntArray(0)
}
*///?}
