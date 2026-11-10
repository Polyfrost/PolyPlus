package org.polyfrost.polyplus.molang

class MolangEnvironment(
    queries: List<String>,
    params: Collection<String> = emptyList(),
    private val unavailable: Map<String, String> = emptyMap(),
    val floatVariables: Boolean = false,
) {
    private val querySlots: Map<String, Int> = queries.withIndex().associate { (i, name) -> name to i }
    private val paramSlots: Map<String, Int> = params.withIndex().associate { (i, name) -> name to i }
    private val variableSlots = LinkedHashMap<String, Int>()

    internal val queryNames: List<String> = queries.toList()
    val paramNames: List<String> = params.toList()

    internal val queryCount: Int
        get() = queryNames.size

    internal val paramCount: Int
        get() = paramNames.size

    val variableCount: Int
        get() = variableSlots.size

    val variableNames: List<String>
        get() = variableSlots.keys.toList()

    fun querySlot(name: String): Int = if (name in unavailable) NONE else querySlots[name] ?: NONE

    internal fun paramSlot(name: String): Int = paramSlots[name] ?: NONE

    fun variableSlot(name: String): Int = variableSlots[name] ?: NONE

    internal fun queryPlace(name: String): String? = unavailable[name]

    internal fun claimVariable(name: String): Int {
        variableSlots[name]?.let { return it }
        if (variableSlots.size >= MolangLimits.MAX_VARIABLES) return NONE
        val slot = variableSlots.size
        variableSlots[name] = slot
        return slot
    }

    companion object {
        const val NONE = -1
    }
}
