package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import kotlin.math.floor

internal class TextureKind(type: String, private val source: Source) :
    FixedKind(type, listOf(Socket("uv", Accepts.VEC2, SocketDefault.Input("TagUV"))), SocketType.VEC4, unitCost = 16) {
    enum class Source {
        BUNDLE,
        VANILLA,
        ATLAS,
    }

    override val samples: Int
        get() = 1

    override fun read(reader: SettingsReader) {
        val texture = when (source) {
            Source.BUNDLE -> reader.required("texture")?.let { id ->
                if (!reader.context.textures(id)) {
                    val path = "textures/$id.png"
                    reader.report(
                        "texture",
                        "no usable texture '$path'",
                        IssueCode.NODE_TEXTURE,
                        names = mapOf("texture" to id, "path" to path),
                    )
                }
                TextureRef.Bundle(id)
            }
            Source.VANILLA -> reader.required("texture")?.let { name ->
                if (name !in VANILLA) {
                    reader.report("texture", "must be one of ${VANILLA.joinToString()}")
                }
                TextureRef.Vanilla(name)
            }
            Source.ATLAS -> reader.required("sprite")?.let { sprite ->
                if (!ATLAS_SPRITE.matches(sprite)) {
                    reader.report("sprite", "must be a block atlas sprite id such as minecraft:block/stone")
                }
                TextureRef.Atlas(sprite)
            }
        }
        reader.settings.texture = texture
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        TextureLane(args[0][0], args[0][1], node.settings.sampler, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val k = node.settings.sampler
        return if (source == Source.ATLAS) {
            "nAtlas(NameplateSampler$k, AtlasRects[$k], ${args[0]})"
        } else {
            "nSample(NameplateSampler$k, ${args[0]})"
        }
    }

    companion object {
        val VANILLA: List<String> = listOf("end_sky", "end_portal", "glint", "beacon_beam", "creeper_armor", "breeze_wind")
        private val ATLAS_SPRITE = Regex("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}")
    }
}

internal class TextureLane(u: Int, v: Int, private val sampler: Int, out: Int) :
    CpuOp(intArrayOf(u, v), IntArray(SocketType.VEC4.width) { out + it }) {
    override val params: String
        get() = "texture$sampler"

    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val at = at
        val texture = shared.textures[sampler]
        val offset = shared.textureOffsets[sampler]
        if (texture == null) {
            for (c in 0 until SocketType.VEC4.width) {
                registers.fill(0.0, at[2 + c], at[2 + c] + count)
            }
            return
        }
        val width = texture.width
        val height = texture.height
        val pixels = texture.pixels
        val u = at[0]
        val v = at[1]
        for (i in 0 until count) {
            val x = floor(registers[u + i] * width).toInt().mod(width)
            val y = floor(registers[v + i] * height).toInt().mod(height)
            ColorBytes.unpack(pixels[offset + y * width + x], registers, at, 2, i)
        }
    }
}

internal object OutputKind : NodeKind(
    "Output",
    listOf(Socket("color", Accepts.SURFACE), Socket("coverage", Accepts.FLOAT, SocketDefault.Value(1.0))),
) {
    private val BLENDS = GraphBlend.entries.map { it.name.lowercase() }

    override fun read(reader: SettingsReader) {
        reader.settings.blend = GraphBlend.entries[reader.choice("blend", BLENDS, 0)]
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.fixed(sockets, inputs, SocketType.FLOAT)

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? = null

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "0.0"
}
