package org.polyfrost.polyplus.molang

class MolangRules(
    val lenient: Boolean = false,
    val maxSourceChars: Int = MolangLimits.MAX_SOURCE_CHARS,
    val maxNodes: Int = MolangLimits.MAX_NODES,
    val maxDepth: Int = MolangLimits.MAX_DEPTH,
    val maxInstructions: Int = MolangLimits.MAX_INSTRUCTIONS,
    val maxVariables: Int = MolangLimits.MAX_VARIABLES,
    val cpuBudget: Int = MolangLimits.CPU_BUDGET,
) {
    companion object {
        val STRICT: MolangRules = MolangRules()

        val BEDROCK: MolangRules = MolangRules(
            lenient = true,
            maxSourceChars = 65_536,
            maxNodes = 16_384,
            maxDepth = 256,
            maxInstructions = 200_000,
            maxVariables = 1_024,
            cpuBudget = 200_000,
        )
    }
}
