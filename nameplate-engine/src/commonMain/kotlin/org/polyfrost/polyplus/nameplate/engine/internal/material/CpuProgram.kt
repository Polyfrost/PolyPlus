package org.polyfrost.polyplus.nameplate.engine.internal.material

internal class CpuProgram(
    val registers: DoubleArray,
    private val shared: CpuShared,
    private val prologue: Array<CpuOp>,
    private val body: Array<CpuOp>,
    private val broadcast: IntArray,
    private val frame: Int,
    val x: Int,
    val y: Int,
    val color: IntArray,
    val coverage: Int,
) {
    fun begin(frame: Double) {
        val r = registers
        if (this.frame >= 0) {
            r[this.frame] = frame
        }
        for (op in prologue) {
            op.run(r, 1, shared)
        }
        for (at in broadcast) {
            r.fill(r[at], at + 1, at + LANES)
        }
    }

    fun run(count: Int) {
        val r = registers
        val s = shared
        for (op in body) {
            op.run(r, count, s)
        }
    }

    companion object {
        const val LANES = 64
    }
}

internal class CpuLinker(private val regs: CpuRegs, private val ops: List<CpuOp>, private val shared: CpuShared) {
    private val size = regs.size
    private val slots = IntArray(size) { -1 }
    private val wide = BooleanArray(size)
    private val lastUse = IntArray(size) { NEVER }
    private var next = 0

    fun link(color: IntArray, coverage: Int, x: Int, y: Int, frame: Int): CpuProgram {
        val roots = color + coverage
        val kept = live(roots)
        val prologue = kept.filter { regs.uniform(it.writes[0]) }
        val body = kept.filter { !regs.uniform(it.writes[0]) }
        for ((k, op) in body.withIndex()) {
            for (v in op.reads) {
                wide[v] = true
                lastUse[v] = k
            }
        }
        for (v in roots) {
            wide[v] = true
            lastUse[v] = FOREVER
        }
        for (op in prologue) {
            op.writes.forEach(::fixed)
            op.reads.forEach(::fixed)
        }
        for (op in body) {
            op.reads.filter(regs::uniform).forEach(::fixed)
        }
        roots.filter(regs::uniform).forEach(::fixed)
        val broadcast = (0 until size).filter { v ->
            slots[v] >= 0 && wide[v] && regs.kind(v) != CpuRegs.CONSTANT
        }.map { slots[it] }
        varying(body, x, y)
        val registers = DoubleArray(maxOf(next, 1))
        for (v in 0 until size) {
            if (slots[v] >= 0 && regs.kind(v) == CpuRegs.CONSTANT) {
                registers.fill(regs.value(v), slots[v], slots[v] + if (wide[v]) CpuProgram.LANES else 1)
            }
        }
        for (op in kept) {
            op.at = IntArray(op.reads.size + op.writes.size) { k ->
                slots[if (k < op.reads.size) op.reads[k] else op.writes[k - op.reads.size]]
            }
        }
        return CpuProgram(
            registers,
            shared,
            prologue.toTypedArray(),
            body.toTypedArray(),
            broadcast.toIntArray(),
            slots[frame],
            slots[x],
            slots[y],
            IntArray(color.size) { slots[color[it]] },
            slots[coverage],
        )
    }

    private fun live(roots: IntArray): List<CpuOp> {
        val needed = BooleanArray(size)
        roots.forEach { needed[it] = true }
        val kept = ArrayList<CpuOp>()
        for (op in ops.asReversed()) {
            if (op.writes.any { needed[it] }) {
                kept.add(op)
                op.reads.forEach { needed[it] = true }
            }
        }
        kept.reverse()
        return kept
    }

    private fun fixed(register: Int) {
        if (slots[register] < 0) {
            slots[register] = next
            next += if (wide[register]) CpuProgram.LANES else 1
        }
    }

    private fun varying(body: List<CpuOp>, x: Int, y: Int) {
        val free = ArrayList<Int>()
        val freed = BooleanArray(size)
        fun take(register: Int) {
            if (slots[register] < 0) {
                slots[register] = if (free.isEmpty()) next.also { next += CpuProgram.LANES } else free.removeAt(free.size - 1)
            }
        }
        fun release(register: Int) {
            if (!freed[register] && !regs.uniform(register)) {
                freed[register] = true
                free.add(slots[register])
            }
        }
        for (v in intArrayOf(x, y)) {
            if (lastUse[v] != NEVER) {
                take(v)
            }
        }
        for ((k, op) in body.withIndex()) {
            op.writes.forEach(::take)
            for (v in op.reads) {
                if (lastUse[v] == k) {
                    release(v)
                }
            }
            for (v in op.writes) {
                if (lastUse[v] == NEVER) {
                    release(v)
                }
            }
        }
    }

    private companion object {
        const val NEVER = -1
        const val FOREVER = Int.MAX_VALUE
    }
}
