package org.polyfrost.polyplus.molang

import kotlin.math.floor

sealed class MolangValue {
    abstract val cost: Int

    abstract fun evaluate(runtime: MolangRuntime): Double

    internal fun snapped(runtime: MolangRuntime): Int = snap(evaluate(runtime))

    class Constant(val value: Double) : MolangValue() {
        override val cost: Int
            get() = 0

        override fun evaluate(runtime: MolangRuntime): Double = value
    }

    class Expression(val program: MolangProgram) : MolangValue() {
        override val cost: Int
            get() = program.cost

        override fun evaluate(runtime: MolangRuntime): Double {
            runtime.clearVariables()
            return program.evaluate(runtime)
        }
    }

    companion object {
        private const val HALF = 0.5
        private const val OPAQUE = 0xFF000000.toInt()
        private const val RGB_MAX = 0xFFFFFF.toDouble()

        fun of(source: String, environment: MolangEnvironment, path: String, report: (MolangIssue) -> Unit): MolangValue? {
            val result = MolangCompiler.compile(source, environment, path)
            result.issues.forEach(report)
            val program = result.program ?: return null
            return if (program.isConstant) Constant(program.constant) else Expression(program)
        }

        fun snap(value: Double): Int = if (value.isFinite()) floor(value + HALF).toInt() else 0

        fun argb(value: Double): Int {
            if (!value.isFinite()) return OPAQUE
            return OPAQUE or floor(value.coerceIn(0.0, RGB_MAX)).toInt()
        }
    }
}
