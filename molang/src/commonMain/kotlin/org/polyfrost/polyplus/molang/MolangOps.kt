package org.polyfrost.polyplus.molang

internal abstract class MolangOp {
    abstract val cost: Int

    abstract fun eval(runtime: MolangRuntime): Double
}

private fun truth(value: Boolean): Double = if (value) 1.0 else 0.0

internal class ConstOp(val value: Double) : MolangOp() {
    override val cost: Int = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return value
    }
}

internal class QueryOp(private val slot: Int) : MolangOp() {
    override val cost: Int = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.queries[slot]
    }
}

internal class ParamOp(private val slot: Int) : MolangOp() {
    override val cost: Int = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.params[slot]
    }
}

internal class VariableOp(private val slot: Int) : MolangOp() {
    override val cost: Int = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.variables[slot]
    }
}

internal class UnaryOp(private val operand: MolangOp, private val negate: Boolean) : MolangOp() {
    override val cost: Int = 1 + operand.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val value = operand.eval(runtime)
        return if (negate) -value else truth(value == 0.0)
    }
}

internal fun interface BinaryFunction {
    fun apply(a: Double, b: Double): Double
}

internal val BINARY_FUNCTIONS: Map<TokenKind, BinaryFunction> = mapOf(
    TokenKind.PLUS to BinaryFunction { a, b -> a + b },
    TokenKind.MINUS to BinaryFunction { a, b -> a - b },
    TokenKind.STAR to BinaryFunction { a, b -> a * b },
    TokenKind.SLASH to BinaryFunction { a, b -> if (b == 0.0) 0.0 else a / b },
    TokenKind.PERCENT to BinaryFunction { a, b -> MolangMath.mod(a, b) },
    TokenKind.EQUAL to BinaryFunction { a, b -> truth(a == b) },
    TokenKind.NOT_EQUAL to BinaryFunction { a, b -> truth(a != b) },
    TokenKind.LESS to BinaryFunction { a, b -> truth(a < b) },
    TokenKind.LESS_EQUAL to BinaryFunction { a, b -> truth(a <= b) },
    TokenKind.GREATER to BinaryFunction { a, b -> truth(a > b) },
    TokenKind.GREATER_EQUAL to BinaryFunction { a, b -> truth(a >= b) },
)

internal class BinaryOp(
    private val left: MolangOp,
    private val right: MolangOp,
    private val function: BinaryFunction,
) : MolangOp() {
    override val cost: Int = 1 + left.cost + right.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val a = left.eval(runtime)
        return function.apply(a, right.eval(runtime))
    }
}

internal class LogicOp(
    private val left: MolangOp,
    private val right: MolangOp,
    private val isAnd: Boolean,
) : MolangOp() {
    override val cost: Int = 1 + left.cost + right.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val first = left.eval(runtime) != 0.0
        return truth(if (isAnd) first && right.eval(runtime) != 0.0 else first || right.eval(runtime) != 0.0)
    }
}

internal class TernaryOp(
    private val condition: MolangOp,
    private val whenTrue: MolangOp,
    private val whenFalse: MolangOp,
) : MolangOp() {
    override val cost: Int = 1 + condition.cost + maxOf(whenTrue.cost, whenFalse.cost)

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return if (condition.eval(runtime) != 0.0) whenTrue.eval(runtime) else whenFalse.eval(runtime)
    }
}

internal class CallOp(private val function: MolangFunction, private val args: Array<MolangOp>) : MolangOp() {
    override val cost: Int = function.cost + args.sumOf { it.cost }

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps += function.cost
        val count = args.size
        val a = if (count > 0) args[0].eval(runtime) else 0.0
        val b = if (count > 1) args[1].eval(runtime) else 0.0
        val c = if (count > 2) args[2].eval(runtime) else 0.0
        val d = if (count > MolangLimits.MAX_ARGS - 1) args[MolangLimits.MAX_ARGS - 1].eval(runtime) else 0.0
        return function.body.apply(a, b, c, d, count)
    }
}

internal class AssignOp(
    private val slot: Int,
    private val value: MolangOp,
    private val floatPrecision: Boolean,
) : MolangOp() {
    override val cost: Int = 1 + value.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val assigned = value.eval(runtime)
        runtime.variables[slot] = if (floatPrecision) MolangMath.float(assigned) else assigned
        return 0.0
    }
}

internal class ReturnOp(private val value: MolangOp) : MolangOp() {
    override val cost: Int = 1 + value.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        runtime.result = value.eval(runtime)
        runtime.returned = true
        return 0.0
    }
}

internal class SequenceOp(private val statements: Array<MolangOp>) : MolangOp() {
    override val cost: Int = 1 + statements.sumOf { it.cost }

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        for (statement in statements) {
            statement.eval(runtime)
            if (runtime.returned) break
        }
        return 0.0
    }
}

internal class ConditionalOp(
    private val condition: MolangOp,
    private val whenTrue: MolangOp,
    private val whenFalse: MolangOp?,
) : MolangOp() {
    override val cost: Int = 1 + condition.cost + maxOf(whenTrue.cost, whenFalse?.cost ?: 0)

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val branch = if (condition.eval(runtime) != 0.0) whenTrue else whenFalse
        if (branch != null) {
            branch.eval(runtime)
        }
        return 0.0
    }
}
