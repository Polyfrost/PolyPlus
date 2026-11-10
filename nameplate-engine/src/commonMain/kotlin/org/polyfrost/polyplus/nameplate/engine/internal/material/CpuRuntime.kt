package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels

internal class CpuArg(private val regs: IntArray) {
    operator fun get(component: Int): Int = regs[component]

    fun take(width: Int): IntArray = regs.copyOf(width)

    companion object {
        val ABSENT: CpuArg = CpuArg(IntArray(0))
    }
}

internal abstract class CpuOp(val reads: IntArray, val writes: IntArray) {
    var at: IntArray = IntArray(0)

    open val params: String?
        get() = null

    abstract fun run(registers: DoubleArray, count: Int, shared: CpuShared)
}

internal class CpuShared(inputs: Int, textures: Int) {
    var areaX0: Double = 0.0
    var areaY0: Double = 0.0
    var areaWidth: Double = 1.0
    var areaHeight: Double = 1.0
    var bodyX0: Double = 0.0
    var bodyY0: Double = 0.0
    var bodyX1: Double = 0.0
    var bodyY1: Double = 0.0
    var fps: Double = 1.0
    var step: Double = 1.0
    var palette: ResolvedPalette = ResolvedPalette.EMPTY
    val inputs: DoubleArray = DoubleArray(inputs)
    val textures: Array<TexturePixels?> = arrayOfNulls(textures)
    val textureOffsets: IntArray = IntArray(textures)
}

internal class CpuEnv(val x: Int, val y: Int, val frame: Int, val index: Int)

internal object CpuDeps {
    const val POSITION = 1
    const val FRAME = 2
    const val INDEX = 4

    private val POSITION_KINDS = setOf("PixelPos", "PixelCell", "TagUV", "ScreenPos", "PixelScreenPos")

    fun of(node: GraphNode, memo: MutableMap<GraphNode, Int>): Int {
        memo[node]?.let { return it }
        var mask = when {
            node.kind.type in POSITION_KINDS -> POSITION
            node.readsFrame -> FRAME
            node.kind.type == "RepeatIndex" -> INDEX
            else -> 0
        }
        for (input in node.inputs) {
            input.node?.let { mask = mask or of(it, memo) }
        }
        node.source?.let { source ->
            val inner = of(source, memo)
            mask = mask or if (node.kind === RepeatKind) inner and INDEX.inv() else inner
        }
        memo[node] = mask
        return mask
    }
}

internal class CpuRegs {
    var size: Int = 0
        private set
    private var kinds = IntArray(INITIAL)
    private var values = DoubleArray(INITIAL)
    private var aliases = IntArray(INITIAL)
    private var uniform = BooleanArray(INITIAL)

    fun allocate(width: Int, kind: Int = FRESH, value: Double = 0.0, uniform: Boolean = false): Int {
        val first = size
        if (size + width > kinds.size) {
            val grown = maxOf(kinds.size * 2, size + width)
            kinds = kinds.copyOf(grown)
            values = values.copyOf(grown)
            aliases = aliases.copyOf(grown)
            this.uniform = this.uniform.copyOf(grown)
        }
        for (v in first until first + width) {
            kinds[v] = kind
            values[v] = value
            aliases[v] = -1
            this.uniform[v] = uniform
        }
        size += width
        return first
    }

    fun kind(register: Int): Int = kinds[register]

    fun value(register: Int): Double = values[register]

    fun uniform(register: Int): Boolean = uniform[register]

    fun written(register: Int, uniform: Boolean) {
        kinds[register] = WRITTEN
        this.uniform[register] = uniform
    }

    fun alias(register: Int, to: Int) {
        aliases[register] = to
        kinds[register] = ALIAS
    }

    fun resolve(register: Int): Int {
        var at = register
        while (aliases[at] >= 0) {
            at = aliases[at]
        }
        return at
    }

    companion object {
        const val FRESH = 0
        const val WRITTEN = 1
        const val ALIAS = 2
        const val CONSTANT = 3
        const val FRAME = 4
        const val POSITION = 5
        private const val INITIAL = 64
    }
}

internal class CpuCompiler(private val shared: CpuShared) {
    private val regs = CpuRegs()
    private val ops = ArrayList<CpuOp>()
    private val memo = HashMap<CpuKey, IntArray>()
    private val tones = HashMap<CpuKey, IntArray>()
    private val deps = HashMap<GraphNode, Int>()
    private val constants = HashMap<Long, Int>()
    private val merged = HashMap<String, CpuOp>()
    private var current = CpuEnv(-1, -1, -1, -1)

    private data class CpuKey(val node: GraphNode, val x: Int, val y: Int, val frame: Int, val index: Int)

    fun compile(output: GraphNode, coverageOnly: Boolean = false): CpuProgram {
        val x = regs.allocate(1, CpuRegs.POSITION)
        val y = regs.allocate(1, CpuRegs.POSITION)
        val frame = regs.allocate(1, CpuRegs.FRAME, uniform = true)
        val top = CpuEnv(x, y, frame, constant(0.0))
        val color = arg(output.inputs[0], top)
        val coverage = arg(output.inputs[1], top)
        val width = output.inputs[0].target?.width ?: 1
        return CpuLinker(regs, ops, shared).link(color.take(if (coverageOnly) 0 else width), coverage[0], x, y, frame)
    }

    fun emit(op: CpuOp) {
        val key = op.params?.let { params -> params + op.reads.joinToString(",", ":") }
        val same = key?.let(merged::get)
        if (same != null) {
            for (k in op.writes.indices) {
                regs.alias(op.writes[k], same.writes[k])
            }
            return
        }
        if (key != null) {
            merged[key] = op
        }
        val uniform = op.reads.all(regs::uniform)
        for (w in op.writes) {
            regs.written(w, uniform)
        }
        ops.add(op)
    }

    fun alias(out: Int, to: Int) = regs.alias(out, regs.resolve(to))

    fun constant(out: Int, values: DoubleArray) {
        for (k in values.indices) {
            regs.alias(out + k, constant(values[k]))
        }
    }

    fun env(): CpuEnv = current

    fun sample(source: GraphNode, args: Array<CpuArg>, out: Int, width: Int) {
        val e = current
        val moved = CpuEnv(shift(e.x, args[0][0]), shift(e.y, args[0][1]), shift(e.frame, args[1][0]), e.index)
        val value = value(source, moved)
        for (c in 0 until width) {
            alias(out + c, value[c])
        }
    }

    fun repeat(source: GraphNode, settings: NodeSettings, out: Int, type: SocketType) {
        val e = current
        val width = type.width
        val slot = type == SocketType.SLOT
        var acc = IntArray(width) { constant(if (slot) -1.0 else 0.0) }
        var found = constant(0.0)
        for (i in 0 until settings.count) {
            val value = value(source, CpuEnv(e.x, e.y, e.frame, constant(i.toDouble())))
            acc = when {
                settings.reduce == Reduce.FIRST -> {
                    val next = regs.allocate(width + 1)
                    emit(FirstLane(found, acc, value, slot, next))
                    found = next
                    IntArray(width) { next + 1 + it }
                }
                i == 0 && settings.reduce != Reduce.SUM -> value
                else -> IntArray(width) { c -> reduce(settings.reduce, acc[c], value[c]) }
            }
        }
        for (c in 0 until width) {
            alias(out + c, acc[c])
        }
    }

    private fun reduce(reduce: Reduce, a: Int, b: Int): Int {
        val out = regs.allocate(1)
        val fn = when (reduce) {
            Reduce.MIN -> LaneFn.MIN
            Reduce.MAX -> LaneFn.MAX
            else -> LaneFn.ADD
        }
        emit(MathLane(fn, out, a, b))
        return regs.resolve(out)
    }

    private fun shift(base: Int, by: Int): Int {
        val offset = regs.resolve(by)
        if (regs.kind(offset) == CpuRegs.CONSTANT && regs.value(offset) == 0.0) return base
        val out = regs.allocate(1)
        emit(MathLane(LaneFn.ADD, out, base, offset))
        return regs.resolve(out)
    }

    private fun value(node: GraphNode, at: CpuEnv): IntArray {
        val mask = CpuDeps.of(node, deps)
        val position = mask and CpuDeps.POSITION != 0
        val key = CpuKey(
            node,
            if (position) at.x else -1,
            if (position) at.y else -1,
            if (mask and CpuDeps.FRAME != 0) at.frame else -1,
            if (mask and CpuDeps.INDEX != 0) at.index else -1,
        )
        memo[key]?.let { return it }
        val args = Array(node.inputs.size) { arg(node.inputs[it], at) }
        val width = node.type.width
        val out = regs.allocate(width)
        val saved = current
        current = at
        node.kind.cpu(this, node, args, out)?.let(::emit)
        current = saved
        val result = IntArray(width) { c ->
            if (regs.kind(out + c) == CpuRegs.FRESH) {
                regs.alias(out + c, constant(0.0))
            }
            regs.resolve(out + c)
        }
        memo[key] = result
        return result
    }

    private fun arg(input: GraphInput, at: CpuEnv): CpuArg {
        val target = input.target ?: return CpuArg.ABSENT
        val literal = input.literal
        val node = input.node
        val source = when {
            literal != null -> IntArray(literal.size) { constant(literal[it]) }
            node == null -> return CpuArg.ABSENT
            node.type == SocketType.SLOT && target == SocketType.VEC4 -> tone(node, at)
            else -> value(node, at)
        }
        val spread = source.size < target.width
        return CpuArg(IntArray(target.width) { source[if (spread) 0 else it] })
    }

    private fun tone(node: GraphNode, at: CpuEnv): IntArray {
        val ref = value(node, at)[0]
        val key = CpuKey(node, ref, -1, -1, -1)
        tones[key]?.let { return it }
        val out = regs.allocate(SocketType.VEC4.width)
        emit(ToneLane(ref, out))
        val result = IntArray(SocketType.VEC4.width) { regs.resolve(out + it) }
        tones[key] = result
        return result
    }

    private fun constant(value: Double): Int =
        constants.getOrPut(value.toRawBits()) { regs.allocate(1, CpuRegs.CONSTANT, value, uniform = true) }
}
