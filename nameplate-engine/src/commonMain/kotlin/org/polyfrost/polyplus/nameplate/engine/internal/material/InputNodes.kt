package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.molang.MolangIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.internal.issue.values
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal class EnvInputKind(
    type: String,
    private val output: SocketType,
    private val read: (CpuCompiler, CpuEnv, Int) -> Unit,
    private val code: String,
    private val unitCost: Int = 1,
) : NodeKind(type) {
    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typing.Ok(output, emptyList())

    override fun own(node: GraphNode): Int = unitCost

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        read(compiler, compiler.env(), out)
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = code
}

internal object SlotKind : NodeKind("Slot") {
    override fun read(reader: SettingsReader) = reader.slot("slot")

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typing.Ok(SocketType.SLOT, emptyList())

    override fun own(node: GraphNode): Int = 0

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        compiler.constant(out, doubleArrayOf(ref(node).toDouble()))
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = context.compact(ref(node)).toString()

    private fun ref(node: GraphNode): Int = node.settings.slot * Palette.MAX_TONES + node.settings.tone
}

internal object ConstantKind : NodeKind("Constant") {
    override fun read(reader: SettingsReader) {
        reader.values("value")?.let { reader.settings.value = it }
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing =
        Typing.Ok(SocketType.vector(node.settings.value.size), emptyList())

    override fun own(node: GraphNode): Int = 0

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        compiler.constant(out, node.settings.value)
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String =
        GlslNumber.vector(node.settings.value, node.type)
}

internal object ParamKind : NodeKind("Param") {
    override fun read(reader: SettingsReader) {
        val name = reader.required("name") ?: return
        if (name !in reader.context.params) {
            reader.report("name", "no param '$name' in the manifest", IssueCode.NODE_PARAM, names = mapOf("param" to name))
        }
        reader.settings.name = name
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typing.Ok(SocketType.FLOAT, emptyList())

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp = InputNodes.input(node, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = InputNodes.uniform(node)
}

internal object ExprKind : NodeKind("Expr") {
    override fun read(reader: SettingsReader) {
        val source = reader.required("expr") ?: return
        val molang = reader.context.molang
        val messages = ArrayList<String>()
        val problems = ArrayList<MolangIssue>()
        val expression = when {
            source.length > MaterialLimits.MAX_EXPRESSION_CHARS -> {
                messages += "longer than ${MaterialLimits.MAX_EXPRESSION_CHARS} characters"
                null
            }
            molang == null -> {
                messages += "Molang is not available in this engine build"
                null
            }
            else -> molang.compile(source, problems)
        }
        messages.forEach { reader.report("expr", it) }
        problems.forEach { reader.report("expr", it.message, IssueCode.of(it.problem), it.values(), it.names) }
        if (expression == null && messages.isEmpty() && problems.isEmpty()) {
            reader.report("expr", "is not a valid expression")
        }
        reader.settings.name = source
        reader.settings.expression = expression
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typing.Ok(SocketType.FLOAT, emptyList())

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp = InputNodes.input(node, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = InputNodes.uniform(node)
}

internal object BodyEdgeKind : NodeKind("BodyEdge", listOf(Socket("p", Accepts.VEC2, SocketDefault.Input("PixelPos")))) {
    private const val OWN_COST = 6
    private const val OUTER = "outer"
    private val EDGES = listOf("fill", OUTER)

    override fun read(reader: SettingsReader) {
        val edge = reader.text("edge") ?: return
        if (edge in EDGES) {
            reader.settings.edge = edge
        } else {
            reader.report("edge", "must be one of ${EDGES.joinToString()}", IssueCode.NODE_SETTING)
        }
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing = Typings.fixed(sockets, inputs, SocketType.FLOAT)

    override fun own(node: GraphNode): Int = OWN_COST

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp =
        BodyEdgeLane(args[0][0], args[0][1], node.settings.input, node.settings.edge == OUTER, out)

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val base = node.settings.input
        val radii = (0 until MaterialInput.BodyEdgeInput.RINGS).joinToString(", ", "vec4(", ")") { InputNodes.lane(base + it) }
        val size = "(BodyRect.zw - BodyRect.xy)"
        if (node.settings.edge != OUTER) return "nBodyEdge(${args[0]}, $size, $radii)"
        val rings = InputNodes.lane(base + MaterialInput.BodyEdgeInput.RINGS)
        return "nBodyEdge(${args[0]} + $rings, $size + 2.0 * $rings, $radii + $rings)"
    }
}

internal object InputNodes {
    private const val LANES = 4
    private const val POSITION_COST = 3

    fun input(node: GraphNode, out: Int): CpuOp = SharedLane(SharedLane.INPUT, node.settings.input, out, 1)

    fun uniform(node: GraphNode): String = lane(node.settings.input)

    fun lane(index: Int): String = "Inputs[${index / LANES}][${index % LANES}]"

    fun kinds(): List<NodeKind> = listOf(
        EnvInputKind("PixelPos", SocketType.VEC2, { c, env, o ->
            c.alias(o, env.x)
            c.alias(o + 1, env.y)
        }, "pos"),
        EnvInputKind("PixelCell", SocketType.VEC2, { c, env, o ->
            c.emit(FloorLane(env.x, o))
            c.emit(FloorLane(env.y, o + 1))
        }, "floor(pos)"),
        EnvInputKind("TagUV", SocketType.VEC2, ::tagUv, "nTagUv(pos)", POSITION_COST),
        EnvInputKind("AreaSize", SocketType.VEC2, { c, _, o ->
            c.emit(SharedLane(SharedLane.AREA, 0, o, SocketType.VEC2.width))
        }, "(AreaRect.zw - AreaRect.xy)"),
        EnvInputKind("BodyRect", SocketType.VEC4, { c, _, o ->
            c.emit(SharedLane(SharedLane.BODY, 0, o, SocketType.VEC4.width))
        }, "BodyRect"),
        EnvInputKind("Frame", SocketType.FLOAT, { c, env, o -> c.alias(o, env.frame) }, "frame"),
        EnvInputKind("SteppedTime", SocketType.FLOAT, { c, env, o -> c.emit(SecondsLane(env.frame, o)) }, "nSeconds(frame)"),
        EnvInputKind("ScreenPos", SocketType.VEC2, ::tagUv, "nScreen(pos)", POSITION_COST),
        EnvInputKind("PixelScreenPos", SocketType.VEC2, { c, env, o ->
            c.emit(PixelScreenLane(env.x, o, vertical = false))
            c.emit(PixelScreenLane(env.y, o + 1, vertical = true))
        }, "nScreen(floor(pos) + 0.5)", POSITION_COST),
        SlotKind,
        ConstantKind,
        ParamKind,
        ExprKind,
        BodyEdgeKind,
    )

    private fun tagUv(compiler: CpuCompiler, env: CpuEnv, out: Int) {
        compiler.emit(TagLane(env.x, out, vertical = false))
        compiler.emit(TagLane(env.y, out + 1, vertical = true))
    }
}
