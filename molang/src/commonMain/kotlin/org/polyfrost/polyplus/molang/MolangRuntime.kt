package org.polyfrost.polyplus.molang

class MolangRuntime(val environment: MolangEnvironment) {
    val queries: DoubleArray = DoubleArray(environment.queryCount)
    val params: DoubleArray = DoubleArray(environment.paramCount)
    internal val variables: DoubleArray = DoubleArray(MolangLimits.MAX_VARIABLES)

    internal var steps: Int = 0
    internal var returned: Boolean = false
    internal var result: Double = 0.0

    internal var fuel: Int = MolangLimits.CPU_BUDGET
        private set

    var failed: Boolean = false
        private set

    fun startFrame(budget: Int = MolangLimits.CPU_BUDGET) {
        fuel = budget
        failed = false
    }

    fun clearVariables() {
        variables.fill(0.0, 0, environment.variableCount)
    }

    fun variable(slot: Int): Double = variables[slot]

    fun setVariable(slot: Int, value: Double) {
        variables[slot] = value
    }

    internal fun setQuery(name: String, value: Double) {
        val slot = environment.querySlot(name)
        if (slot != MolangEnvironment.NONE) {
            queries[slot] = value
        }
    }

    fun setParam(name: String, value: Double) {
        val slot = environment.paramSlot(name)
        if (slot != MolangEnvironment.NONE) {
            params[slot] = value
        }
    }

    internal fun begin() {
        steps = 0
        returned = false
        result = 0.0
    }

    internal fun finish(value: Double): Double {
        if (steps > MolangLimits.MAX_INSTRUCTIONS || steps > fuel) {
            failed = true
            fuel = 0
            return 0.0
        }
        fuel -= steps
        return value
    }
}
