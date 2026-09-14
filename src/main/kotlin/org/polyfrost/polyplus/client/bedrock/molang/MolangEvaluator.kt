package org.polyfrost.polyplus.client.bedrock.molang

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object MolangEvaluator {
    fun execute(statements: List<MolangStatement>, context: MolangContext) {
        for (statement in statements) {
            val value = eval(statement.expression, context).toFloat()
            val target = statement.target ?: continue
            writeMember(target, value, context)
        }
    }

    fun eval(expression: MolangExpr, context: MolangContext): Double {
        return when (expression) {
            is MolangExpr.Number -> expression.value
            is MolangExpr.Member -> readMember(expression, context)
            is MolangExpr.Call -> evalCall(expression, context)
            is MolangExpr.Unary -> when (expression.op) {
                MolangExpr.Unary.Op.NEGATE -> -eval(expression.operand, context)
                MolangExpr.Unary.Op.NOT -> if (eval(expression.operand, context) != 0.0) 0.0 else 1.0
            }
            is MolangExpr.Binary -> evalBinary(expression, context)
            is MolangExpr.Ternary -> {
                if (eval(expression.condition, context) != 0.0) {
                    eval(expression.whenTrue, context)
                } else {
                    eval(expression.whenFalse, context)
                }
            }
        }
    }

    private fun evalBinary(expression: MolangExpr.Binary, context: MolangContext): Double {
        val left = eval(expression.left, context)
        val right = eval(expression.right, context)
        return when (expression.op) {
            MolangExpr.Binary.Op.ADD -> left + right
            MolangExpr.Binary.Op.SUB -> left - right
            MolangExpr.Binary.Op.MUL -> left * right
            MolangExpr.Binary.Op.DIV -> if (right == 0.0) 0.0 else left / right
            MolangExpr.Binary.Op.MOD -> if (right == 0.0) 0.0 else left % right
            MolangExpr.Binary.Op.EQ -> if (left == right) 1.0 else 0.0
            MolangExpr.Binary.Op.NEQ -> if (left != right) 1.0 else 0.0
            MolangExpr.Binary.Op.LT -> if (left < right) 1.0 else 0.0
            MolangExpr.Binary.Op.LTE -> if (left <= right) 1.0 else 0.0
            MolangExpr.Binary.Op.GT -> if (left > right) 1.0 else 0.0
            MolangExpr.Binary.Op.GTE -> if (left >= right) 1.0 else 0.0
            MolangExpr.Binary.Op.AND -> if (left != 0.0 && right != 0.0) 1.0 else 0.0
            MolangExpr.Binary.Op.OR -> if (left != 0.0 || right != 0.0) 1.0 else 0.0
        }
    }

    private fun evalCall(expression: MolangExpr.Call, context: MolangContext): Double {
        val args = expression.args.map { eval(it, context) }

        val name = expression.callee.path.lastOrNull()?.lowercase()
            ?: expression.callee.root.lowercase()

        return when (name) {
            "sin" -> sin(Math.toRadians(args.getOrElse(0) { 0.0 }))
            "cos" -> cos(Math.toRadians(args.getOrElse(0) { 0.0 }))
            "abs" -> abs(args.getOrElse(0) { 0.0 })
            "min" -> min(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 })
            "max" -> max(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 })
            "clamp" -> {
                val value = args.getOrElse(0) { 0.0 }
                val low = args.getOrElse(1) { 0.0 }
                val high = args.getOrElse(2) { 1.0 }
                min(max(value, low), high)
            }
            "lerp" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val alpha = args.getOrElse(2) { 0.0 }
                start + (end - start) * alpha
            }
            "pow" -> args.getOrElse(0) { 0.0 }.pow(args.getOrElse(1) { 1.0 })
            "sqrt" -> sqrt(args.getOrElse(0) { 0.0 })
            "floor" -> floor(args.getOrElse(0) { 0.0 })
            "ceil" -> ceil(args.getOrElse(0) { 0.0 })
            "mod" -> {
                val value = args.getOrElse(0) { 0.0 }
                val divisor = args.getOrElse(1) { 1.0 }
                if (divisor == 0.0) 0.0 else value % divisor
            }
            else -> 0.0
        }

    }

    private fun readMember(member: MolangExpr.Member, context: MolangContext): Double {
        val root = normalizeRoot(member.root)
        val path = member.path

        return when (root) {
            "query", "q" -> context.query(path.joinToString("_"))
            "variable", "v" -> context.getVariable(path.joinToString("."))
            "temp", "t" -> context.getVariable("temp.${path.joinToString(".")}")
            else -> context.getVariable(listOf(root, *path.toTypedArray()).joinToString("."))
        }.toDouble()
    }

    private fun writeMember(member: MolangExpr.Member, value: Float, context: MolangContext) {
        val root = normalizeRoot(member.root)

        val key = when (root) {
            "variable", "v" -> member.path.joinToString(".")
            "temp", "t" -> "temp.${member.path.joinToString(".")}"
            else -> listOf(root, *member.path.toTypedArray()).joinToString(".")
        }

        context.setVariable(key, value)
    }

    private fun normalizeRoot(root: String): String = when (root.lowercase()) {
        "q" -> "query"
        "v" -> "variable"
        "t" -> "temp"
        else -> root.lowercase()
    }
}
