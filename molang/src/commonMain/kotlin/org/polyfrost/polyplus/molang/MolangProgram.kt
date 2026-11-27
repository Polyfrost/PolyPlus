package org.polyfrost.polyplus.molang

class MolangProgram internal constructor(
    val source: String,
    val environment: MolangEnvironment,
    private val root: MolangOp,
    private val simple: Boolean,
) {
    val cost: Int = root.cost

    val isConstant: Boolean = simple && root is ConstOp

    val constant: Double = if (root is ConstOp) root.value else 0.0

    fun evaluate(runtime: MolangRuntime): Double {
        require(runtime.environment === environment) { "runtime was built for another environment" }
        runtime.begin()
        val value = root.eval(runtime)
        return runtime.finish(if (simple) value else runtime.result)
    }
}
