package org.polyfrost.polyplus.molang

object MolangCompiler {
    fun compile(source: String, environment: MolangEnvironment, path: String): MolangResult {
        val script = try {
            MolangParser(MolangLexer(source, environment.rules.maxSourceChars).tokens(), environment.rules).script()
        } catch (e: MolangSyntaxException) {
            val issue = MolangIssue(path, "${e.message} at column ${e.column}", e.problem, e.column, e.names, e.values)
            return MolangResult(null, listOf(issue))
        }
        val resolver = MolangResolver(environment, path)
        val root = resolver.script(script)
        if (resolver.problems.isNotEmpty()) return MolangResult(null, resolver.problems)
        val limit = environment.rules.maxInstructions
        if (root.cost > limit) {
            val message = "costs up to ${root.cost} instructions; the limit is $limit"
            val values = mapOf("cost" to root.cost, "limit" to limit)
            return MolangResult(null, listOf(MolangIssue(path, message, MolangProblem.COST, values = values)))
        }
        return MolangResult(MolangProgram(source, environment, root, script.simple), emptyList())
    }
}
