package org.polyfrost.polyplus.molang

data class MolangIssue(
    val path: String,
    val message: String,
    val problem: MolangProblem,
    val column: Int = 0,
    val names: Map<String, String> = emptyMap(),
    val values: Map<String, Int> = emptyMap(),
) {
    override fun toString(): String = "$path: $message"
}

internal class MolangSyntaxException(
    message: String,
    val column: Int,
    val problem: MolangProblem,
    val names: Map<String, String> = emptyMap(),
    val values: Map<String, Int> = emptyMap(),
) : RuntimeException(message)
