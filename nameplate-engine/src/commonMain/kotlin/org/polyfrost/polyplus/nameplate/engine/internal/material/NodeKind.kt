package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits

internal abstract class NodeKind(val type: String, val sockets: List<Socket> = emptyList()) {
    open val samples: Int
        get() = 0

    open fun read(reader: SettingsReader) = Unit

    open fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typing.Error("$type has no output")

    fun cost(node: GraphNode): Int = own(node) + MaterialLimits.TONE_COST * node.inputs.count(::readsTone)

    protected open fun own(node: GraphNode): Int = 1

    abstract fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp?

    abstract fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String

    open fun statement(context: GlslContext, node: GraphNode, args: List<String>, name: String): String =
        "${node.type.glsl} $name = ${glsl(context, node, args)};"

    private companion object {
        fun readsTone(input: GraphInput): Boolean = input.node?.type == SocketType.SLOT && input.target == SocketType.VEC4
    }
}

internal object NodeCosts {
    fun own(type: String, variant: Int): Int? {
        val kind = NodeCatalog.kind(type) ?: return null
        val width = SocketType.vector(variant.coerceIn(1, VECTOR))
        val settings = NodeSettings().also { it.octaves = variant.coerceIn(1, MaterialLimits.MAX_OCTAVES) }
        val node = GraphNode(type, kind, settings)
        node.type = width
        node.inputs = kind.sockets.map { GraphInput(null, null).also { input -> input.target = width } }
        return kind.cost(node)
    }

    private const val VECTOR = 4
}

internal abstract class FixedKind(
    type: String,
    sockets: List<Socket>,
    private val output: SocketType,
    private val unitCost: Int = 1,
) : NodeKind(type, sockets) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.fixed(sockets, inputs, output)

    override fun own(node: GraphNode): Int = unitCost
}

internal object Literals {
    fun parse(element: JsonElement): DoubleArray? = when (element) {
        is JsonPrimitive -> number(element)?.let { doubleArrayOf(it) }
        is JsonArray -> {
            val values = element.map { (it as? JsonPrimitive)?.let(::number) }
            if (values.size in 1..VECTOR && values.all { it != null }) DoubleArray(values.size) { values[it] ?: 0.0 } else null
        }
        else -> null
    }

    private fun number(primitive: JsonPrimitive): Double? = primitive.takeIf { !it.isString }?.doubleOrNull
        ?.takeIf { it.isFinite() && it >= -MaterialLimits.MAX_LITERAL && it <= MaterialLimits.MAX_LITERAL }

    private const val VECTOR = 4
}
