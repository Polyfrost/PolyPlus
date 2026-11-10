package org.polyfrost.polyplus.molang

internal abstract class MolangOp {
    abstract val cost: Int

    abstract fun eval(runtime: MolangRuntime): Double
}

private fun truth(value: Boolean): Double = if (value) 1.0 else 0.0

internal class ConstOp(val value: Double) : MolangOp() {
    override val cost: Int
        get() = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return value
    }
}

internal class QueryOp(private val slot: Int) : MolangOp() {
    override val cost: Int
        get() = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.queries[slot]
    }
}

internal class ParamOp(private val slot: Int) : MolangOp() {
    override val cost: Int
        get() = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.params[slot]
    }
}

internal class VariableOp(private val slot: Int) : MolangOp() {
    override val cost: Int
        get() = 1

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return runtime.variables[slot]
    }
}

internal abstract class UnaryOp(protected val operand: MolangOp) : MolangOp() {
    override val cost: Int = 1 + operand.cost
}

internal class NegateOp(operand: MolangOp) : UnaryOp(operand) {
    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return -operand.eval(runtime)
    }
}

internal class NotOp(operand: MolangOp) : UnaryOp(operand) {
    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return truth(operand.eval(runtime) == 0.0)
    }
}

internal abstract class BinaryOp(protected val left: MolangOp, protected val right: MolangOp) : MolangOp() {
    override val cost: Int = 1 + left.cost + right.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        val a = left.eval(runtime)
        return apply(a, right.eval(runtime))
    }

    abstract fun apply(a: Double, b: Double): Double
}

internal class AddOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = a + b
}

internal class SubtractOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = a - b
}

internal class MultiplyOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = a * b
}

internal class DivideOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = if (b == 0.0) 0.0 else a / b
}

internal class ModuloOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = MolangMath.mod(a, b)
}

internal class EqualOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a == b)
}

internal class NotEqualOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a != b)
}

internal class LessOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a < b)
}

internal class LessEqualOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a <= b)
}

internal class GreaterOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a > b)
}

internal class GreaterEqualOp(left: MolangOp, right: MolangOp) : BinaryOp(left, right) {
    override fun apply(a: Double, b: Double): Double = truth(a >= b)
}

internal class AndOp(private val left: MolangOp, private val right: MolangOp) : MolangOp() {
    override val cost: Int = 1 + left.cost + right.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return truth(left.eval(runtime) != 0.0 && right.eval(runtime) != 0.0)
    }
}

internal class OrOp(private val left: MolangOp, private val right: MolangOp) : MolangOp() {
    override val cost: Int = 1 + left.cost + right.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        return truth(left.eval(runtime) != 0.0 || right.eval(runtime) != 0.0)
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

internal class AssignOp(private val slot: Int, private val value: MolangOp) : MolangOp() {
    override val cost: Int = 1 + value.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        runtime.variables[slot] = value.eval(runtime)
        return 0.0
    }
}

internal class FloatAssignOp(private val slot: Int, private val value: MolangOp) : MolangOp() {
    override val cost: Int = 1 + value.cost

    override fun eval(runtime: MolangRuntime): Double {
        runtime.steps++
        runtime.variables[slot] = MolangMath.float(value.eval(runtime))
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
