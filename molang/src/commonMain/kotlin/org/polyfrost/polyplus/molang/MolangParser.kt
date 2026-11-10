package org.polyfrost.polyplus.molang

internal class MolangParser(private val tokens: List<Token>) {
    private var index = 0
    private var nodes = 0
    private var depth = 0

    fun script(): MolangNode.Script {
        val statements = ArrayList<MolangNode>()
        skipSemicolons()
        while (peek().kind != TokenKind.END) {
            statements += statement()
            if (peek().kind != TokenKind.END) {
                expect(TokenKind.SEMICOLON)
            }
            skipSemicolons()
        }
        if (statements.isEmpty()) {
            throw MolangSyntaxException("empty expression", 1, MolangProblem.EMPTY)
        }
        val simple = statements.size == 1 && isExpression(statements[0])
        return MolangNode.Script(statements, simple)
    }

    private fun isExpression(node: MolangNode): Boolean =
        node !is MolangNode.Assign && node !is MolangNode.Return && node !is MolangNode.Block &&
            node !is MolangNode.Conditional

    private fun statement(): MolangNode {
        val token = peek()
        return when {
            token.kind == TokenKind.NAME && token.text == RETURN -> {
                advance()
                counted(MolangNode.Return(token.column, expression()))
            }
            token.kind == TokenKind.OPEN_BRACE -> block()
            isAssignment() -> assignment()
            else -> expressionStatement()
        }
    }

    private fun isAssignment(): Boolean {
        var at = index
        if (tokens[at].kind != TokenKind.NAME) return false
        at++
        while (tokens[at].kind == TokenKind.DOT && tokens[at + 1].kind == TokenKind.NAME) {
            at += 2
        }
        return tokens[at].kind == TokenKind.ASSIGN
    }

    private fun assignment(): MolangNode {
        val target = member()
        val assign = expect(TokenKind.ASSIGN)
        return counted(MolangNode.Assign(assign.column, target, expression()))
    }

    private fun block(): MolangNode.Block = nested {
        val open = expect(TokenKind.OPEN_BRACE)
        val statements = ArrayList<MolangNode>()
        skipSemicolons()
        while (peek().kind != TokenKind.CLOSE_BRACE) {
            if (peek().kind == TokenKind.END) {
                throw unexpected(TokenKind.CLOSE_BRACE)
            }
            statements += statement()
            if (peek().kind != TokenKind.CLOSE_BRACE) {
                expect(TokenKind.SEMICOLON)
            }
            skipSemicolons()
        }
        advance()
        counted(MolangNode.Block(open.column, statements))
    }

    private fun expressionStatement(): MolangNode {
        val condition = or()
        if (peek().kind != TokenKind.QUESTION || peek(1).kind != TokenKind.OPEN_BRACE) return ternaryRest(condition)
        val question = advance()
        val whenTrue = block()
        val whenFalse = if (peek().kind == TokenKind.COLON) {
            advance()
            block()
        } else {
            null
        }
        return counted(MolangNode.Conditional(question.column, condition, whenTrue, whenFalse))
    }

    private fun expression(): MolangNode = nested { ternaryRest(or()) }

    private fun ternaryRest(condition: MolangNode): MolangNode {
        if (peek().kind != TokenKind.QUESTION) return condition
        val question = advance()
        val whenTrue = expression()
        expect(TokenKind.COLON)
        val whenFalse = expression()
        return counted(MolangNode.Ternary(question.column, condition, whenTrue, whenFalse))
    }

    private fun or(): MolangNode = leftAssociative(OR) { and() }

    private fun and(): MolangNode = leftAssociative(AND) { equality() }

    private fun equality(): MolangNode = leftAssociative(EQUALITY) { comparison() }

    private fun comparison(): MolangNode = leftAssociative(COMPARISON) { additive() }

    private fun additive(): MolangNode = leftAssociative(ADDITIVE) { multiplicative() }

    private fun multiplicative(): MolangNode = leftAssociative(MULTIPLICATIVE) { unary() }

    private inline fun leftAssociative(kinds: Set<TokenKind>, operand: () -> MolangNode): MolangNode {
        var left = operand()
        while (peek().kind in kinds) {
            val op = advance()
            left = counted(MolangNode.Binary(op.column, op.kind, left, operand()))
        }
        return left
    }

    private fun unary(): MolangNode {
        val token = peek()
        if (token.kind != TokenKind.BANG && token.kind != TokenKind.MINUS) return primary()
        advance()
        return nested { counted(MolangNode.Unary(token.column, token.kind, unary())) }
    }

    private fun primary(): MolangNode {
        val token = peek()
        return when (token.kind) {
            TokenKind.NUMBER -> counted(MolangNode.Number(advance().column, token.number))
            TokenKind.TEXT -> counted(MolangNode.Text(advance().column, token.text))
            TokenKind.OPEN_PAREN -> {
                advance()
                val inner = expression()
                expect(TokenKind.CLOSE_PAREN)
                inner
            }
            TokenKind.NAME -> memberOrCall()
            else -> throw unexpected(VALUE, token)
        }
    }

    private fun memberOrCall(): MolangNode {
        val member = member()
        if (peek().kind != TokenKind.OPEN_PAREN) return member
        val open = advance()
        val args = ArrayList<MolangNode>()
        if (peek().kind != TokenKind.CLOSE_PAREN) {
            args += expression()
            while (peek().kind == TokenKind.COMMA) {
                advance()
                args += expression()
            }
        }
        expect(TokenKind.CLOSE_PAREN)
        return counted(MolangNode.Call(open.column, member, args))
    }

    private fun member(): MolangNode.Member {
        val root = expect(TokenKind.NAME)
        val path = ArrayList<String>()
        while (peek().kind == TokenKind.DOT) {
            advance()
            path += expect(TokenKind.NAME).text
        }
        return counted(MolangNode.Member(root.column, root.text, path))
    }

    private inline fun <T> nested(body: () -> T): T {
        depth++
        if (depth > MolangLimits.MAX_DEPTH) {
            throw MolangSyntaxException(
                "nested deeper than ${MolangLimits.MAX_DEPTH}",
                peek().column,
                MolangProblem.TOO_DEEP,
                values = mapOf("limit" to MolangLimits.MAX_DEPTH),
            )
        }
        val result = body()
        depth--
        return result
    }

    private fun <T : MolangNode> counted(node: T): T {
        nodes++
        if (nodes > MolangLimits.MAX_NODES) {
            throw MolangSyntaxException(
                "more than ${MolangLimits.MAX_NODES} nodes",
                node.column,
                MolangProblem.TOO_BIG,
                values = mapOf("limit" to MolangLimits.MAX_NODES),
            )
        }
        return node
    }

    private fun skipSemicolons() {
        while (peek().kind == TokenKind.SEMICOLON) {
            advance()
        }
    }

    private fun expect(kind: TokenKind): Token {
        if (peek().kind != kind) {
            throw unexpected(kind)
        }
        return advance()
    }

    private fun unexpected(kind: TokenKind): MolangSyntaxException = unexpected(kind.text, peek())

    private fun unexpected(expected: String, token: Token): MolangSyntaxException {
        val found = describe(token)
        return MolangSyntaxException(
            "expected $expected, found $found",
            token.column,
            MolangProblem.UNEXPECTED,
            mapOf("expected" to expected, "found" to found),
        )
    }

    private fun describe(token: Token): String = when (token.kind) {
        TokenKind.NAME -> "'${token.text}'"
        else -> token.kind.text
    }

    private fun peek(offset: Int = 0): Token = tokens[minOf(index + offset, tokens.size - 1)]

    private fun advance(): Token = tokens[index].also { if (index < tokens.size - 1) index++ }

    private companion object {
        const val RETURN = "return"
        const val VALUE = "a value"

        val OR: Set<TokenKind> = setOf(TokenKind.OR)
        val AND: Set<TokenKind> = setOf(TokenKind.AND)
        val EQUALITY: Set<TokenKind> = setOf(TokenKind.EQUAL, TokenKind.NOT_EQUAL)
        val COMPARISON: Set<TokenKind> =
            setOf(TokenKind.LESS, TokenKind.LESS_EQUAL, TokenKind.GREATER, TokenKind.GREATER_EQUAL)
        val ADDITIVE: Set<TokenKind> = setOf(TokenKind.PLUS, TokenKind.MINUS)
        val MULTIPLICATIVE: Set<TokenKind> = setOf(TokenKind.STAR, TokenKind.SLASH, TokenKind.PERCENT)
    }
}
