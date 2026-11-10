package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import kotlin.math.floor

internal object SampleAtKind : NodeKind(
    "SampleAt",
    listOf(
        Socket("offset", Accepts.VEC2, SocketDefault.Value(0.0, 0.0)),
        Socket("frameOffset", Accepts.FLOAT, SocketDefault.Value(0.0)),
    ),
) {
    const val OWN_COST = 2

    override fun read(reader: SettingsReader) {
        reader.settings.source = reader.required("source")
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing {
        val source = node.source?.type ?: return Typing.Error("needs a source")
        return Typings.fixed(sockets, inputs, source)
    }

    override fun own(node: GraphNode): Int = OWN_COST

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        node.source?.let { compiler.sample(it, args, out, node.type.width) }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val name = node.source?.let(context::sub) ?: return "0"
        return "$name(pos + ${args[0]}, frame + ${args[1]}, index)"
    }
}

internal object RepeatKind : NodeKind("Repeat") {
    private val REDUCE = Reduce.entries.map { it.name.lowercase() }

    override fun read(reader: SettingsReader) {
        val settings = reader.settings
        settings.source = reader.required("source")
        if (!reader.present("count")) {
            reader.report("count", "is required", IssueCode.NODE_REQUIRED)
        }
        settings.count = reader.int("count", 1..MaterialLimits.MAX_REPEAT, 1)
        settings.reduce = Reduce.entries[reader.choice("reduce", REDUCE, Reduce.MAX.ordinal)]
    }

    override fun typing(node: GraphNode, inputs: List<SocketType?>): Typing {
        val source = node.source?.type
        return when {
            source == null -> Typing.Error("needs a source")
            source == SocketType.SLOT && node.settings.reduce != Reduce.FIRST -> Typing.Error("slots only reduce with first")
            else -> Typing.Ok(source, emptyList())
        }
    }

    override fun own(node: GraphNode): Int = SampleAtKind.OWN_COST

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? {
        node.source?.let { compiler.repeat(it, node.settings, out, node.type) }
        return null
    }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String = "0"

    override fun statement(context: GlslContext, node: GraphNode, args: List<String>, name: String): String {
        val source = node.source ?: return "${node.type.glsl} $name = ${node.type.glsl}(0);"
        val type = node.type.glsl
        val call = "${context.sub(source)}(pos, frame, float(i))"
        val reduce = node.settings.reduce
        val init = if (node.type == SocketType.SLOT) "-1" else "$type(0.0)"
        val test = when {
            node.type == SocketType.SLOT -> "${name}r >= 0"
            node.type.width == 1 -> "${name}r > 0.0"
            else -> "${name}r.${"xyzw"[node.type.width - 1]} > 0.0"
        }
        val step = when (reduce) {
            Reduce.SUM -> "$name += ${name}r;"
            Reduce.MIN -> "$name = i == 0 ? ${name}r : min($name, ${name}r);"
            Reduce.MAX -> "$name = i == 0 ? ${name}r : max($name, ${name}r);"
            Reduce.FIRST -> "if (!${name}f && $test) {\n        $name = ${name}r;\n        ${name}f = true;\n    }"
        }
        return "$type $name = $init;\n" +
            (if (reduce == Reduce.FIRST) "bool ${name}f = false;\n" else "") +
            "for (int i = 0; i < ${node.settings.count}; i++) {\n" +
            "    $type ${name}r = $call;\n" +
            "    $step\n" +
            "}"
    }
}

internal object SpriteKind : FixedKind(
    "Sprite",
    listOf(
        Socket("at", Accepts.VEC2, SocketDefault.Input("PixelCell")),
        Socket("frame", Accepts.FLOAT, SocketDefault.Value(0.0)),
    ),
    SocketType.SLOT,
    unitCost = 6,
) {
    override fun read(reader: SettingsReader) {
        val id = reader.required("sprite") ?: return
        val sprite = reader.context.sprites(id)
        if (sprite == null) {
            reader.report("sprite", "no usable sprite '$id'", IssueCode.NODE_SPRITE, names = mapOf("sprite" to id))
        }
        reader.settings.sprite = sprite
    }

    override fun cpu(compiler: CpuCompiler, node: GraphNode, args: Array<CpuArg>, out: Int): CpuOp? =
        node.settings.sprite?.let { SpriteLane(args[0][0], args[0][1], args[1][0], it, out) }

    override fun glsl(context: GlslContext, node: GraphNode, args: List<String>): String {
        val sprite = node.settings.sprite ?: return "-1"
        return "${context.sprite(sprite)}(${args[0]}, ${args[1]})"
    }
}

internal object StructureNodes {
    fun kinds(): List<NodeKind> = listOf(
        SampleAtKind,
        RepeatKind,
        EnvInputKind("RepeatIndex", SocketType.FLOAT, { c, env, o -> c.alias(o, env.index) }, "index"),
        SpriteKind,
    )
}

internal class SpriteLane(x: Int, y: Int, frame: Int, private val sprite: MaterialSprite, out: Int) :
    CpuOp(intArrayOf(x, y, frame), intArrayOf(out)) {
    override fun run(registers: DoubleArray, count: Int, shared: CpuShared) {
        val sprite = sprite
        lanes(registers, count, at) { x, y, frame -> sprite.ref(floor(x).toInt(), floor(y).toInt(), floor(frame).toInt()).toDouble() }
    }
}
