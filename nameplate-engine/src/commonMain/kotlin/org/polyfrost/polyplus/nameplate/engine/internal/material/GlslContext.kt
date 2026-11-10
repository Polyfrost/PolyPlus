package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

internal class GlslContext(private val toneSlots: IntArray) {
    private val functions = ArrayList<String>()
    private val subs = HashMap<GraphNode, String>()
    private val sprites = HashMap<MaterialSprite, String>()
    private val declarations = StringBuilder()

    fun compact(ref: Int): Int = if (ref < 0) -1 else toneBase(ref / Palette.MAX_TONES) + ref % Palette.MAX_TONES

    fun toneBase(slot: Int): Int = toneSlots.indexOf(slot) * Palette.MAX_TONES

    fun name(node: GraphNode): String = "v${node.index}"

    fun sub(source: GraphNode): String = subs[source] ?: run {
        val name = "nSub${subs.size}"
        subs[source] = name
        val body = statements(source, skipRoot = false)
        functions += "${source.type.glsl} $name(vec2 pos, float frame, float index) {\n$body    return ${name(source)};\n}\n"
        name
    }

    fun sprite(sprite: MaterialSprite): String = sprites[sprite] ?: run {
        val name = "nSprite${sprites.size}"
        sprites[sprite] = name
        val cells = sprite.cells.map { compact(it) }
        declarations.append("const int ${name}Cells[${cells.size}] = int[${cells.size}](")
        for ((i, cell) in cells.withIndex()) {
            declarations.append(if (i == 0) "" else if (i % LINE_CELLS == 0) ",\n    " else ", ").append(cell)
        }
        declarations.append(");\n")
        functions += "int $name(vec2 at, float frame) {\n" +
            "    ivec2 c = ivec2(floor(at));\n" +
            "    if (c.x < 0 || c.y < 0 || c.x >= ${sprite.width} || c.y >= ${sprite.height}) {\n" +
            "        return -1;\n" +
            "    }\n" +
            "    int f = nWrap(int(floor(frame)), ${sprite.frames});\n" +
            "    return ${name}Cells[(f * ${sprite.height} + c.y) * ${sprite.width} + c.x];\n" +
            "}\n"
        name
    }

    fun statements(root: GraphNode, skipRoot: Boolean): String {
        val out = StringBuilder()
        for (node in GraphOrder.cone(root)) {
            if (node !== root || !skipRoot) {
                val args = node.inputs.map(::arg)
                out.append(INDENT).append(node.kind.statement(this, node, args, name(node)).replace("\n", "\n$INDENT"))
                    .append('\n')
            }
        }
        return out.toString()
    }

    fun arg(input: GraphInput): String {
        val target = input.target
        val literal = input.literal
        val node = input.node
        return when {
            target == null -> ""
            literal != null -> GlslNumber.vector(literal, target)
            node == null -> ""
            node.type == target -> name(node)
            node.type == SocketType.SLOT -> "nTone(${name(node)})"
            else -> "${target.glsl}(${name(node)})"
        }
    }

    fun declarations(): String = declarations.toString()

    fun functions(): String = functions.joinToString("")

    private companion object {
        const val INDENT = "    "
        const val LINE_CELLS = 24
    }
}

internal object GlslNumber {
    private const val DIGITS = 8
    private const val TEN = 10.0
    private const val MAX_PLAIN = 1.0E7
    private const val LIMIT = 1_000_000_000L
    private const val SCALE = 1.0E8

    fun literal(value: Double): String =
        if (value == floor(value) && abs(value) < MAX_PLAIN) "${value.toLong()}.0" else scientific(value)

    fun vector(values: DoubleArray, target: SocketType): String = when {
        target == SocketType.SLOT -> values[0].toInt().toString()
        values.size == 1 && target.width == 1 -> literal(values[0])
        else -> "${target.glsl}(${values.joinToString(", ") { literal(it) }})"
    }

    private fun scientific(value: Double): String {
        var m = abs(value)
        var exponent = 0
        while (m >= TEN) {
            m /= TEN
            exponent++
        }
        while (m < 1.0) {
            m *= TEN
            exponent--
        }
        var digits = round(m * SCALE).toLong()
        if (digits >= LIMIT) {
            digits /= TEN.toLong()
            exponent++
        }
        val text = digits.toString().padStart(DIGITS + 1, '0')
        val fraction = text.substring(1).trimEnd('0').ifEmpty { "0" }
        return "${if (value < 0.0) "-" else ""}${text[0]}.${fraction}e$exponent"
    }
}
