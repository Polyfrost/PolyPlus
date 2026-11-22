package org.polyfrost.polyplus.nameplate.engine.internal.material

internal enum class SocketType(val width: Int, val glsl: String) {
    FLOAT(1, "float"),
    VEC2(2, "vec2"),
    VEC3(3, "vec3"),
    VEC4(4, "vec4"),
    SLOT(1, "int"),
    ;

    val label: String
        get() = name.lowercase()

    companion object {
        fun vector(width: Int): SocketType = entries[width - 1]
    }
}

internal enum class Accepts {
    NUMBER,
    FLOAT,
    VEC2,
    COLOR,
    SURFACE,
    ANY,
}

internal sealed interface SocketDefault {
    data object Required : SocketDefault

    data object Absent : SocketDefault

    class Value(vararg val values: Double) : SocketDefault

    class Input(val type: String) : SocketDefault
}

internal class Socket(val name: String, val accepts: Accepts, val default: SocketDefault = SocketDefault.Required)

internal sealed interface Typing {
    class Ok(val output: SocketType, val targets: List<SocketType?>) : Typing

    class Error(val message: String) : Typing
}

internal object Typings {
    fun target(accepts: Accepts, source: SocketType): SocketType? = when (accepts) {
        Accepts.FLOAT -> source.takeIf { it == SocketType.FLOAT }
        Accepts.VEC2 -> SocketType.VEC2.takeIf { source == SocketType.FLOAT || source == SocketType.VEC2 }
        Accepts.COLOR -> SocketType.VEC4.takeIf { source == SocketType.VEC4 || source == SocketType.SLOT }
        Accepts.SURFACE -> source.takeIf { it == SocketType.VEC4 || it == SocketType.SLOT }
        Accepts.NUMBER -> if (source == SocketType.SLOT) SocketType.VEC4 else source
        Accepts.ANY -> source
    }

    fun fixed(sockets: List<Socket>, inputs: List<SocketType?>, output: SocketType): Typing {
        val bad = inputs.indices.firstOrNull { i -> inputs[i]?.let { target(sockets[i].accepts, it) == null } == true }
        if (bad != null) return mismatch(sockets[bad], inputs[bad])
        return Typing.Ok(output, inputs.mapIndexed { i, source -> source?.let { target(sockets[i].accepts, it) } })
    }

    fun elementwise(sockets: List<Socket>, inputs: List<SocketType?>): Typing {
        val widths = inputs.map { it?.let { source -> target(Accepts.NUMBER, source)?.width } }
        val width = widths.maxOf { it ?: 1 }
        val bad = widths.indices.firstOrNull { widths[it] != null && widths[it] != 1 && widths[it] != width }
        val output = SocketType.vector(width)
        if (bad != null) return Typing.Error("${sockets[bad].name} is a ${inputs[bad]?.label}, but other inputs are ${output.label}")
        return Typing.Ok(output, inputs.map { source -> source?.let { output } })
    }

    fun mismatch(socket: Socket, source: SocketType?): Typing.Error =
        Typing.Error("${socket.name} can't take a ${source?.label}")
}
