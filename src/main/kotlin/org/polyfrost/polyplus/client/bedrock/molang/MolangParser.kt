package org.polyfrost.polyplus.client.bedrock.molang

object MolangParser {
    fun parseExpression(input: String): MolangExpr {
        return Parser(input.trim().trimEnd(';')).parseExpression()
    }

    fun parseStatements(input: String): List<MolangStatement> {
        val trimmed = input.trim()
        if (trimmed.isEmpty())
            return emptyList()

        return trimmed.splitToSequence(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { segment ->
                val normalized = segment.removePrefix("return").trim()
                Parser(normalized).parseStatement()
            }
            .toList()
    }

    fun parseStatementBlock(input: String): List<MolangStatement> {
        val trimmed = input.trim()
        if (trimmed.isEmpty())
            return emptyList()

        if (!trimmed.contains(';'))
            return listOf(Parser(trimmed.removePrefix("return").trim()).parseStatement())

        return parseStatements(trimmed)
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parseStatement(): MolangStatement {
            skipWhitespace()

            if (matchIdentifier("return")) {
                skipWhitespace()
                return MolangStatement(null, parseTernary())
            }

            val checkpoint = index
            val member = parseMember()

            skipWhitespace()

            if (peek() == '=') {
                consume('=')
                skipWhitespace()
                return MolangStatement(member, parseTernary())
            }

            index = checkpoint
            return MolangStatement(null, parseTernary())
        }

        fun parseExpression(): MolangExpr = parseTernary()

        private fun parseTernary(): MolangExpr {
            var expr = parseOr()
            skipWhitespace()

            if (peek() == '?') {
                consume('?')
                skipWhitespace()

                val whenTrue = parseTernary()

                skipWhitespace()
                expect(':')
                skipWhitespace()

                val whenFalse = parseTernary()
                expr = MolangExpr.Ternary(expr, whenTrue, whenFalse)
            }

            return expr
        }

        private fun parseOr(): MolangExpr {
            var expr = parseAnd()
            while (true) {
                skipWhitespace()
                if (match("||")) {
                    skipWhitespace()
                    expr = MolangExpr.Binary(MolangExpr.Binary.Op.OR, expr, parseAnd())
                } else {
                    break
                }
            }
            return expr
        }

        private fun parseAnd(): MolangExpr {
            var expr = parseEquality()
            while (true) {
                skipWhitespace()
                if (match("&&")) {
                    skipWhitespace()
                    expr = MolangExpr.Binary(MolangExpr.Binary.Op.AND, expr, parseEquality())
                } else {
                    break
                }
            }
            return expr
        }

        private fun parseEquality(): MolangExpr {
            var expr = parseComparison()
            while (true) {
                skipWhitespace()
                when {
                    match("==") -> {
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.EQ, expr, parseComparison())
                    }
                    match("!=") -> {
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.NEQ, expr, parseComparison())
                    }
                    else -> break
                }
            }
            return expr
        }

        private fun parseComparison(): MolangExpr {
            var expr = parseAdditive()
            while (true) {
                skipWhitespace()
                when {
                    match("<=") -> {
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.LTE, expr, parseAdditive())
                    }
                    match(">=") -> {
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.GTE, expr, parseAdditive())
                    }
                    peek() == '<' -> {
                        consume('<')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.LT, expr, parseAdditive())
                    }
                    peek() == '>' -> {
                        consume('>')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.GT, expr, parseAdditive())
                    }
                    else -> break
                }
            }
            return expr
        }

        private fun parseAdditive(): MolangExpr {
            var expr = parseMultiplicative()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> {
                        consume('+')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.ADD, expr, parseMultiplicative())
                    }
                    '-' -> {
                        consume('-')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.SUB, expr, parseMultiplicative())
                    }
                    else -> break
                }
            }
            return expr
        }

        private fun parseMultiplicative(): MolangExpr {
            var expr = parseUnary()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> {
                        consume('*')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.MUL, expr, parseUnary())
                    }
                    '/' -> {
                        consume('/')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.DIV, expr, parseUnary())
                    }
                    '%' -> {
                        consume('%')
                        skipWhitespace()
                        expr = MolangExpr.Binary(MolangExpr.Binary.Op.MOD, expr, parseUnary())
                    }
                    else -> break
                }
            }
            return expr
        }

        private fun parseUnary(): MolangExpr {
            skipWhitespace()
            when (peek()) {
                '!' -> {
                    consume('!')
                    skipWhitespace()
                    return MolangExpr.Unary(MolangExpr.Unary.Op.NOT, parseUnary())
                }
                '-' -> {
                    consume('-')
                    skipWhitespace()
                    return MolangExpr.Unary(MolangExpr.Unary.Op.NEGATE, parseUnary())
                }
            }
            return parsePostfix()
        }

        private fun parsePostfix(): MolangExpr {
            var expr: MolangExpr = parsePrimary()
            while (true) {
                skipWhitespace()
                if (peek() == '(' && expr is MolangExpr.Member) {
                    consume('(')
                    skipWhitespace()
                    val args = mutableListOf<MolangExpr>()
                    if (peek() != ')') {
                        args += parseTernary()
                        skipWhitespace()
                        while (peek() == ',') {
                            consume(',')
                            skipWhitespace()
                            args += parseTernary()
                            skipWhitespace()
                        }
                    }
                    expect(')')
                    expr = MolangExpr.Call(expr, args)
                } else {
                    break
                }
            }
            return expr
        }

        private fun parsePrimary(): MolangExpr {
            skipWhitespace()
            when (val ch = peek()) {
                '(' -> {
                    consume('(')
                    skipWhitespace()
                    val expr = parseTernary()
                    skipWhitespace()
                    expect(')')
                    return expr
                }
                in '0'..'9', '.' -> return MolangExpr.Number(parseNumber())
                in 'a'..'z', in 'A'..'Z', '_' -> return parseMember()
                else -> error("Unexpected character '$ch' at $index in '$source'")
            }
        }

        private fun parseMember(): MolangExpr.Member {
            val root = parseIdentifier()
            val path = mutableListOf<String>()
            skipWhitespace()
            while (peek() == '.') {
                consume('.')
                skipWhitespace()
                path += parseIdentifier()
                skipWhitespace()
            }
            return MolangExpr.Member(root, path)
        }

        private fun parseNumber(): Double {
            val start = index
            if (peek() == '-') {
                consume('-')
            }

            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                index++
            }

            return source.substring(start, index).toDouble()
        }

        private fun parseIdentifier(): String {
            skipWhitespace()
            val start = index
            if (!isIdentifierStart(peek()))
                error("Expected identifier at $index in '$source'")

            index++
            while (index < source.length && isIdentifierPart(source[index])) {
                index++
            }

            return source.substring(start, index)
        }

        private fun matchIdentifier(value: String): Boolean {
            skipWhitespace()
            if (!source.startsWith(value, index))
                return false

            if (index + value.length < source.length && isIdentifierPart(source[index + value.length]))
                return false

            index += value.length
            return true
        }

        private fun match(value: String): Boolean {
            skipWhitespace()

            if (!source.startsWith(value, index))
                return false

            index += value.length
            return true
        }

        private fun expect(ch: Char) {
            skipWhitespace()

            if (peek() != ch) {
                error("Expected '$ch' at $index in '$source'")
            }

            consume(ch)
        }

        private fun peek(): Char = if (index < source.length) source[index] else '\u0000'

        private fun consume(ch: Char) {
            if (peek() != ch) {
                error("Expected '$ch' at $index in '$source'")
            }

            index++
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        private fun isIdentifierStart(ch: Char): Boolean = ch.isLetter() || ch == '_'

        private fun isIdentifierPart(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
    }
}
