package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.nameplate.render.NameplateGeometry
import org.polyfrost.polyplus.client.nameplate.render.NameplatePose
import org.polyfrost.polyplus.client.nameplate.render.NameplateVertexSink
import org.polyfrost.polyplus.client.nameplate.render.Z_TOWARD_CAMERA
import org.polyfrost.polyplus.mixin.client.nameplate.BufferBuilderAccessor
import java.nio.ByteOrder

object LegacyVertexSink : NameplateVertexSink {
    const val INTS_PER_VERTEX: Int = 6

    private const val INITIAL_VERTICES = 1024
    private const val INITIAL_QUADS = 256
    private const val QUAD_INTS = INTS_PER_VERTEX * 4
    private const val CHANNEL = 0xFF
    private const val SHIFT_A = 24
    private const val SHIFT_B = 16
    private const val SHIFT_G = 8
    private const val Z = 2
    private const val U = 3
    private const val V = 4
    private const val COLOR = 5
    private const val ALPHA_REF = 0.1f
    private const val DEPTH_ALPHA_REF = 0.5f
    private const val TEXT_GAP_UNITS = 4f
    private const val NO_MATERIAL = -1

    private val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    private val identity = NameplatePose()

    private var data = IntArray(INITIAL_VERTICES * INTS_PER_VERTEX)
    private var out = IntArray(INITIAL_VERTICES * INTS_PER_VERTEX)
    private var size = 0
    private var textures = IntArray(INITIAL_QUADS)
    private var draws = IntArray(INITIAL_QUADS)
    private val materials = ArrayList<LegacyMaterialGpu.Draw>()
    private var order = IntArray(INITIAL_QUADS)
    private var recording = false
    private var offsetX = 0f
    private var offsetY = 0f
    private var x = 0f
    private var y = 0f
    private var z = 0f
    private var u = 0f
    private var v = 0f

    val isRecording: Boolean
        get() = recording

    override fun addVertex(x: Float, y: Float, z: Float): NameplateVertexSink {
        this.x = x + offsetX
        this.y = y + offsetY
        this.z = z
        return this
    }

    override fun setUv(u: Float, v: Float): NameplateVertexSink {
        this.u = u
        this.v = v
        return this
    }

    override fun setLight(light: Int): NameplateVertexSink = this

    override fun setColor(argb: Int): NameplateVertexSink {
        val at = size
        if (at + INTS_PER_VERTEX > data.size) {
            data = data.copyOf(data.size * 2)
        }
        val out = data
        out[at] = x.toRawBits()
        out[at + 1] = y.toRawBits()
        out[at + Z] = z.toRawBits()
        out[at + U] = u.toRawBits()
        out[at + V] = v.toRawBits()
        out[at + COLOR] = packColor(argb)
        size = at + INTS_PER_VERTEX
        return this
    }

    fun record() {
        recording = true
        size = 0
        materials.clear()
    }

    fun stopRecording() {
        recording = false
    }

    fun clear() {
        recording = false
        size = 0
        materials.clear()
    }

    fun draw(texture: Identifier, x: Float, y: Float, renderer: NameplateGeometry) {
        if (recording) {
            val manager = Minecraft.getInstance().textureManager
            manager.bind(texture)
            append(manager.get(texture)?.glId ?: return, NO_MATERIAL, x, y, renderer)
            return
        }
        drawNow(x, y, renderer) { Minecraft.getInstance().textureManager.bind(texture) }
    }

    fun draw(glId: Int, x: Float, y: Float, renderer: NameplateGeometry) {
        if (recording) {
            append(glId, NO_MATERIAL, x, y, renderer)
            return
        }
        drawNow(x, y, renderer) { GlStateManager.bindTexture(glId) }
    }

    fun drawMaterial(draw: LegacyMaterialGpu.Draw, x: Float, y: Float, renderer: NameplateGeometry) {
        if (recording) {
            materials += draw
            append(0, materials.size - 1, x, y, renderer)
            return
        }
        drawNow(x, y, renderer, { LegacyMaterialGpu.end(draw) }) { LegacyMaterialGpu.begin(draw) }
    }

    private inline fun drawNow(x: Float, y: Float, renderer: NameplateGeometry, after: () -> Unit = {}, bind: () -> Unit) {
        collect(x, y, renderer)
        if (size > 0) {
            val direct = LegacyTagBatch.flush()
            bind()
            try {
                flush(data, size, direct)
            } finally {
                after()
            }
        }
    }

    fun drawRecorded(overText: Boolean, depthMask: Boolean) {
        val count = sort(overText)
        if (count == 0) {
            return
        }
        val direct = LegacyTagBatch.flush()
        try {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
            GL11.glPolygonOffset(0f, if (overText) -TEXT_GAP_UNITS else TEXT_GAP_UNITS)
            GlStateManager.depthMask(false)
            emit(count, depthPass = false, direct)
            GlStateManager.colorMask(false, false, false, false)
            GlStateManager.depthMask(true)
            GlStateManager.alphaFunc(GL11.GL_GREATER, DEPTH_ALPHA_REF)
            emit(count, depthPass = true, direct)
        } finally {
            GL11.glPolygonOffset(0f, 0f)
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
            GlStateManager.colorMask(true, true, true, true)
            GlStateManager.alphaFunc(GL11.GL_GREATER, ALPHA_REF)
            GlStateManager.depthMask(depthMask)
        }
    }

    private fun append(glId: Int, material: Int, x: Float, y: Float, renderer: NameplateGeometry) {
        val first = size / QUAD_INTS
        offsetX = x
        offsetY = y
        renderer.render(identity, this)
        size -= size % QUAD_INTS
        val last = size / QUAD_INTS
        if (last > textures.size) {
            textures = textures.copyOf(maxOf(last, textures.size * 2))
            draws = draws.copyOf(textures.size)
        }
        textures.fill(glId, first, last)
        draws.fill(material, first, last)
    }

    private fun toward(quad: Int): Float = Float.fromBits(data[quad * QUAD_INTS + Z]) * Z_TOWARD_CAMERA

    private fun sort(overText: Boolean): Int {
        val quads = size / QUAD_INTS
        if (order.size < quads) {
            order = IntArray(maxOf(quads, order.size * 2))
        }
        var count = 0
        for (q in 0 until quads) {
            if ((toward(q) > 0f) == overText) {
                order[count++] = q
            }
        }
        for (i in 1 until count) {
            val quad = order[i]
            val key = toward(quad)
            var j = i - 1
            while (j >= 0 && toward(order[j]) > key) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = quad
        }
        return count
    }

    private fun emit(count: Int, depthPass: Boolean, direct: Boolean) {
        if (out.size < count * QUAD_INTS) {
            out = IntArray(count * QUAD_INTS)
        }
        var from = 0
        while (from < count) {
            val glId = textures[order[from]]
            val material = draws[order[from]]
            var to = from
            var ints = 0
            while (to < count && textures[order[to]] == glId && draws[order[to]] == material) {
                System.arraycopy(data, order[to] * QUAD_INTS, out, ints, QUAD_INTS)
                ints += QUAD_INTS
                to++
            }
            if (material == NO_MATERIAL) {
                GlStateManager.bindTexture(glId)
                flush(out, ints, direct)
            } else {
                val draw = materials[material]
                if (!depthPass || !draw.additive) {
                    LegacyMaterialGpu.begin(draw)
                    try {
                        flush(out, ints, direct)
                    } finally {
                        LegacyMaterialGpu.end(draw)
                    }
                }
            }
            from = to
        }
    }

    private fun collect(x: Float, y: Float, renderer: NameplateGeometry) {
        offsetX = x
        offsetY = y
        size = 0
        renderer.render(identity, this)
    }

    private fun packColor(argb: Int): Int {
        val a = argb ushr SHIFT_A
        val r = (argb shr SHIFT_B) and CHANNEL
        val g = (argb shr SHIFT_G) and CHANNEL
        val b = argb and CHANNEL
        return if (littleEndian) {
            (a shl SHIFT_A) or (b shl SHIFT_B) or (g shl SHIFT_G) or r
        } else {
            (r shl SHIFT_A) or (g shl SHIFT_B) or (b shl SHIFT_G) or a
        }
    }

    private fun flush(source: IntArray, ints: Int, direct: Boolean) {
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val access = buffer as BufferBuilderAccessor
        val start = access.`polyplus$vertexCount`() * INTS_PER_VERTEX
        access.`polyplus$intBuffer`().position(start)
        access.`polyplus$grow`(ints)
        access.`polyplus$intBuffer`().put(source, 0, ints)
        access.`polyplus$setVertexCount`((start + ints) / INTS_PER_VERTEX)
        if (direct) {
            LegacyTagBatch.draw(buffer)
        } else {
            tesselator.end()
        }
    }
}
*///?}
