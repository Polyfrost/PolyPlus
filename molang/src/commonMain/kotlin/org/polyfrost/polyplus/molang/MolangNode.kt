package org.polyfrost.polyplus.molang

internal sealed interface MolangNode {
    val column: Int

    class Number(override val column: Int, val value: Double) : MolangNode

    class Text(override val column: Int, val value: String) : MolangNode

    class Member(override val column: Int, val root: String, val path: List<String>) : MolangNode {
        val name: String
            get() = (listOf(root) + path).joinToString(".")
    }

    class Call(override val column: Int, val callee: Member, val args: List<MolangNode>) : MolangNode

    class Unary(override val column: Int, val op: TokenKind, val operand: MolangNode) : MolangNode

    class Binary(override val column: Int, val op: TokenKind, val left: MolangNode, val right: MolangNode) : MolangNode

    class Ternary(
        override val column: Int,
        val condition: MolangNode,
        val whenTrue: MolangNode,
        val whenFalse: MolangNode,
    ) : MolangNode

    class Assign(override val column: Int, val target: Member, val value: MolangNode) : MolangNode

    class Return(override val column: Int, val value: MolangNode) : MolangNode

    class Block(override val column: Int, val statements: List<MolangNode>) : MolangNode

    class Conditional(
        override val column: Int,
        val condition: MolangNode,
        val whenTrue: Block,
        val whenFalse: Block?,
    ) : MolangNode

    class Script(val statements: List<MolangNode>, val simple: Boolean) : MolangNode {
        override val column: Int
            get() = 1
    }
}
