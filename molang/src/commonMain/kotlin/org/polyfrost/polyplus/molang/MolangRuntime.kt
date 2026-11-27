package org.polyfrost.polyplus.molang

class MolangRuntime(val environment: MolangEnvironment) {
    val queries: DoubleArray = DoubleArray(environment.queryCount)
    val params: DoubleArray = DoubleArray(environment.paramCount)
    internal val variables: DoubleArray = DoubleArray(environment.rules.maxVariables)

    internal var steps: Int = 0
    internal var returned: Boolean = false
    internal var result: Double = 0.0

    internal var fuel: Int = environment.rules.cpuBudget
        private set

    var failed: Boolean = false
        private set

    fun startFrame(budget: Int = environment.rules.cpuBudget) {
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
        if (steps > environment.rules.maxInstructions || steps > fuel) {
            failed = true
            fuel = 0
            return 0.0
        }
        fuel -= steps
        return value
    }
}
