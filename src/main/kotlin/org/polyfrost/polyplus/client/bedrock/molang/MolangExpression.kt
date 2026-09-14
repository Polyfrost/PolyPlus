package org.polyfrost.polyplus.client.bedrock.molang

sealed interface MolangExpr {
    data class Number(val value: Double) : MolangExpr

    data class Member(val root: String, val path: List<String>) : MolangExpr

    data class Call(val callee: Member, val args: List<MolangExpr>) : MolangExpr

    data class Unary(val op: Op, val operand: MolangExpr) : MolangExpr {
        enum class Op { NEGATE, NOT }
    }

    data class Binary(val op: Op, val left: MolangExpr, val right: MolangExpr) : MolangExpr {
        enum class Op {
            ADD, SUB, MUL, DIV, MOD,
            EQ, NEQ, LT, LTE, GT, GTE,
            AND, OR,
        }
    }

    data class Ternary(val condition: MolangExpr, val whenTrue: MolangExpr, val whenFalse: MolangExpr) : MolangExpr
}

data class MolangStatement(
    val target: MolangExpr.Member?,
    val expression: MolangExpr,
)
