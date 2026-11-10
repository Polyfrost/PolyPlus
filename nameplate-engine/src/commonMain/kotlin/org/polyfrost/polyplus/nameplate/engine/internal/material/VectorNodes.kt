package org.polyfrost.polyplus.nameplate.engine.internal.material

internal object CombineKind : NodeKind(
    "Combine",
    listOf(
        Socket("x", Accepts.FLOAT),
        Socket("y", Accepts.FLOAT),
        Socket("z", Accepts.FLOAT, SocketDefault.Absent),
        Socket("w", Accepts.FLOAT, SocketDefault.Absent),
    ),
) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing {
        val width = inputs.indexOfFirst { it == null }.let { if (it < 0) inputs.size else it }
        return if (inputs.drop(width).any { it != null }) {
            Typing.Error("w needs z")
        } else {
            Typings.fixed(sockets, inputs, SocketType.vector(width))
        }
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.alias(out + k, args[k][0])
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        "${node.type.glsl}(${args.take(node.type.width).joinToString(", ")})"
}

internal object ComponentKind : NodeKind("Component", listOf(Socket("v", Accepts.NUMBER))) {
    private val NAMES = listOf("x", "y", "z", "w")

    override fun read(reader: SettingsReader) {
        reader.settings.component = reader.choice("component", NAMES, 0)
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing {
        val typing = Typings.elementwise(sockets, inputs) as? Typing.Ok ?: return Typings.elementwise(sockets, inputs)
        val name = NAMES[node.settings.component]
        return if (node.settings.component >= typing.output.width) {
            Typing.Error("a ${typing.output.label} has no $name")
        } else {
            Typing.Ok(SocketType.FLOAT, typing.targets)
        }
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        compiler.alias(out, args[0][node.settings.component])
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        if (node.inputs[0].target?.width == 1) args[0] else "${args[0]}.${NAMES[node.settings.component]}"
}

internal object SelectKind : NodeKind(
    "Select",
    listOf(Socket("condition", Accepts.FLOAT), Socket("a", Accepts.ANY), Socket("b", Accepts.ANY)),
) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing {
        val a = inputs[1]
        val b = inputs[2]
        val unified = when {
            a == null || b == null -> null
            a == b -> a
            a == SocketType.FLOAT && b != SocketType.SLOT -> b
            b == SocketType.FLOAT && a != SocketType.SLOT -> a
            setOf(a, b) == setOf(SocketType.SLOT, SocketType.VEC4) -> SocketType.VEC4
            else -> null
        }
        return when {
            inputs[0] != SocketType.FLOAT -> Typings.mismatch(sockets[0], inputs[0])
            unified == null -> Typing.Error("a is a ${a?.label} and b is a ${b?.label}; they don't mix")
            else -> Typing.Ok(unified, listOf(SocketType.FLOAT, unified, unified))
        }
    }

    override fun own(node: GraphNode): Int = 1 + node.type.width

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        for (k in 0 until node.type.width) {
            compiler.emit(SelectLane(args[0][0], args[1][k], args[2][k], out + k))
        }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        "(${args[0]} > 0.0 ? ${args[2]} : ${args[1]})"
}

internal object VectorNodes {
    fun kinds(): List<NodeKind> = listOf(CombineKind, ComponentKind, SelectKind)
}
