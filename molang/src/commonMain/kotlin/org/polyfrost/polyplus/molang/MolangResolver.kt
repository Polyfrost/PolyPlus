package org.polyfrost.polyplus.molang

internal class MolangResolver(private val environment: MolangEnvironment) {
    class Problem(
        val message: String,
        val column: Int,
        val problem: MolangProblem,
        val names: Map<String, String> = emptyMap(),
        val values: Map<String, Int> = emptyMap(),
    ) {
        fun issue(path: String): MolangIssue = MolangIssue(path, "$message at column $column", problem, column, names, values)
    }

    val problems: MutableList<Problem> = ArrayList()

    private val folder by lazy { MolangRuntime(environment) }

    fun script(script: MolangNode.Script): MolangOp =
        if (script.simple) expression(script.statements[0]) else sequence(script.statements)

    private fun sequence(statements: List<MolangNode>): MolangOp =
        SequenceOp(statements.mapNotNull { statement(it) }.toTypedArray())

    private fun statement(node: MolangNode): MolangOp? = when (node) {
        is MolangNode.Assign -> assign(node)
        is MolangNode.Return -> ReturnOp(expression(node.value))
        is MolangNode.Block -> sequence(node.statements)
        is MolangNode.Conditional -> conditional(node)
        else -> {
            expression(node)
            null
        }
    }

    private fun conditional(node: MolangNode.Conditional): MolangOp {
        val condition = expression(node.condition)
        val whenTrue = sequence(node.whenTrue.statements)
        val whenFalse = node.whenFalse?.let { sequence(it.statements) }
        if (condition !is ConstOp) return ConditionalOp(condition, whenTrue, whenFalse)
        return if (condition.value != 0.0) whenTrue else whenFalse ?: SequenceOp(emptyArray())
    }

    private fun assign(node: MolangNode.Assign): MolangOp {
        val slot = variable(node.target)
        val value = expression(node.value)
        return when {
            slot == MolangEnvironment.NONE -> value
            environment.floatVariables -> FloatAssignOp(slot, value)
            else -> AssignOp(slot, value)
        }
    }

    private fun expression(node: MolangNode): MolangOp = when (node) {
        is MolangNode.Number -> ConstOp(node.value)
        is MolangNode.Member -> member(node)
        is MolangNode.Call -> call(node)
        is MolangNode.Unary -> unary(node)
        is MolangNode.Binary -> binary(node)
        is MolangNode.Ternary -> ternary(node)
        is MolangNode.Text -> problem("strings are only allowed in q.variant_param", node.column, MolangProblem.STRING)
        else -> problem("expected a value", node.column, MolangProblem.VALUE)
    }

    private fun unary(node: MolangNode.Unary): MolangOp {
        val operand = expression(node.operand)
        val op = if (node.op == TokenKind.MINUS) NegateOp(operand) else NotOp(operand)
        return folded(op, operand)
    }

    private fun binary(node: MolangNode.Binary): MolangOp {
        val left = expression(node.left)
        val right = expression(node.right)
        val op = when (node.op) {
            TokenKind.PLUS -> AddOp(left, right)
            TokenKind.MINUS -> SubtractOp(left, right)
            TokenKind.STAR -> MultiplyOp(left, right)
            TokenKind.SLASH -> DivideOp(left, right)
            TokenKind.PERCENT -> ModuloOp(left, right)
            else -> comparison(node.op, left, right)
        }
        return folded(op, left, right)
    }

    private fun comparison(kind: TokenKind, left: MolangOp, right: MolangOp): MolangOp = when (kind) {
        TokenKind.EQUAL -> EqualOp(left, right)
        TokenKind.NOT_EQUAL -> NotEqualOp(left, right)
        TokenKind.LESS -> LessOp(left, right)
        TokenKind.LESS_EQUAL -> LessEqualOp(left, right)
        TokenKind.GREATER -> GreaterOp(left, right)
        TokenKind.GREATER_EQUAL -> GreaterEqualOp(left, right)
        TokenKind.AND -> AndOp(left, right)
        else -> OrOp(left, right)
    }

    private fun ternary(node: MolangNode.Ternary): MolangOp {
        val condition = expression(node.condition)
        val whenTrue = expression(node.whenTrue)
        val whenFalse = expression(node.whenFalse)
        if (condition !is ConstOp) return TernaryOp(condition, whenTrue, whenFalse)
        return if (condition.value != 0.0) whenTrue else whenFalse
    }

    private fun member(node: MolangNode.Member): MolangOp = when (node.root) {
        in QUERY_ROOTS -> query(node)
        in VARIABLE_ROOTS, in TEMP_ROOTS -> {
            val slot = variable(node)
            if (slot == MolangEnvironment.NONE) ConstOp(0.0) else VariableOp(slot)
        }
        MATH -> problem("${node.name} is a function; call it with ( )", node.column, MolangProblem.UNCALLED, named(node.name))
        else -> problem("unknown name ${node.name}", node.column, MolangProblem.UNKNOWN_NAME, named(node.name))
    }

    private fun query(node: MolangNode.Member): MolangOp {
        if (node.path.size != 1) return problem("${node.name} is not a query", node.column, MolangProblem.NOT_QUERY, named(node.name))
        val name = node.path[0]
        val slot = environment.querySlot(name)
        val place = environment.queryPlace(name)
        return when {
            slot != MolangEnvironment.NONE -> QueryOp(slot)
            place != null -> problem(
                "q.$name is only available in $place",
                node.column,
                MolangProblem.QUERY_PLACE,
                mapOf(QUERY to name, "place" to place),
            )
            else -> problem("unknown query q.$name", node.column, MolangProblem.UNKNOWN_QUERY, mapOf(QUERY to name))
        }
    }

    private fun variable(node: MolangNode.Member): Int {
        val prefix = when (node.root) {
            in VARIABLE_ROOTS -> "v."
            in TEMP_ROOTS -> "t."
            else -> {
                problem("only v. and t. variables can be assigned, not ${node.name}", node.column, MolangProblem.ASSIGN, named(node.name))
                return MolangEnvironment.NONE
            }
        }
        if (node.path.isEmpty()) {
            problem("${node.name} needs a variable name", node.column, MolangProblem.VARIABLE_NAME, named(node.name))
            return MolangEnvironment.NONE
        }
        val slot = environment.claimVariable(prefix + node.path.joinToString("."))
        if (slot == MolangEnvironment.NONE) {
            problem(
                "more than ${MolangLimits.MAX_VARIABLES} variables",
                node.column,
                MolangProblem.VARIABLES,
                values = mapOf(LIMIT to MolangLimits.MAX_VARIABLES),
            )
        }
        return slot
    }

    private fun call(node: MolangNode.Call): MolangOp {
        val callee = node.callee
        return when {
            callee.root in QUERY_ROOTS && callee.path == listOf(VARIANT_PARAM) -> param(node)
            callee.root == MATH && callee.path.size == 1 -> function(node, callee.path[0])
            else -> {
                node.args.forEach { if (it !is MolangNode.Text) expression(it) }
                problem("unknown function ${callee.name}", callee.column, MolangProblem.UNKNOWN_FUNCTION, named(callee.name))
            }
        }
    }

    private fun param(node: MolangNode.Call): MolangOp {
        val name = node.args.singleOrNull()
        if (name !is MolangNode.Text) return problem("q.$VARIANT_PARAM takes one 'name'", node.column, MolangProblem.PARAM_CALL)
        val slot = environment.paramSlot(name.value)
        return if (slot == MolangEnvironment.NONE) {
            problem("unknown param '${name.value}'", name.column, MolangProblem.UNKNOWN_PARAM, mapOf("param" to name.value))
        } else {
            ParamOp(slot)
        }
    }

    private fun function(node: MolangNode.Call, name: String): MolangOp {
        val args = node.args.map { expression(it) }
        val function = MolangFunctions.find(name)
            ?: return problem("unknown function math.$name", node.callee.column, MolangProblem.UNKNOWN_MATH, mapOf(FUNCTION to name))
        if (args.size !in function.minArgs..function.maxArgs) {
            val count = if (function.minArgs == function.maxArgs) {
                "${function.minArgs}"
            } else {
                "${function.minArgs} or ${function.maxArgs}"
            }
            return problem(
                "math.$name takes $count arguments, not ${args.size}",
                node.column,
                MolangProblem.ARGUMENTS,
                mapOf(FUNCTION to name),
                mapOf("min" to function.minArgs, "max" to function.maxArgs, "count" to args.size),
            )
        }
        val array = args.toTypedArray()
        return folded(CallOp(function, array), *array)
    }

    private fun folded(op: MolangOp, vararg operands: MolangOp): MolangOp =
        if (operands.all { it is ConstOp }) ConstOp(op.eval(folder)) else op

    private fun problem(
        message: String,
        column: Int,
        problem: MolangProblem,
        names: Map<String, String> = emptyMap(),
        values: Map<String, Int> = emptyMap(),
    ): MolangOp {
        problems += Problem(message, column, problem, names, values)
        return ConstOp(0.0)
    }

    private fun named(name: String): Map<String, String> = mapOf(NAME to name)

    private companion object {
        const val MATH = "math"
        const val VARIANT_PARAM = "variant_param"
        const val NAME = "name"
        const val QUERY = "query"
        const val FUNCTION = "function"
        const val LIMIT = "limit"

        val QUERY_ROOTS: Set<String> = setOf("q", "query")
        val VARIABLE_ROOTS: Set<String> = setOf("v", "variable")
        val TEMP_ROOTS: Set<String> = setOf("t", "temp")
    }
}
