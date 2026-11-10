package org.polyfrost.polyplus.molang

internal enum class TokenKind(val text: String) {
    NUMBER("a number"),
    NAME("a name"),
    TEXT("a string"),
    DOT("'.'"),
    COMMA("','"),
    SEMICOLON("';'"),
    OPEN_PAREN("'('"),
    CLOSE_PAREN("')'"),
    OPEN_BRACE("'{'"),
    CLOSE_BRACE("'}'"),
    PLUS("'+'"),
    MINUS("'-'"),
    STAR("'*'"),
    SLASH("'/'"),
    PERCENT("'%'"),
    BANG("'!'"),
    EQUAL("'=='"),
    NOT_EQUAL("'!='"),
    LESS("'<'"),
    LESS_EQUAL("'<='"),
    GREATER("'>'"),
    GREATER_EQUAL("'>='"),
    AND("'&&'"),
    OR("'||'"),
    QUESTION("'?'"),
    COLON("':'"),
    ASSIGN("'='"),
    END("the end"),
}

internal class Token(val kind: TokenKind, val column: Int, val text: String = "", val number: Double = 0.0)

internal class MolangLexer(private val source: String) {
    private var index = 0
    private val tokens = ArrayList<Token>()

    fun tokens(): List<Token> {
        if (source.length > MolangLimits.MAX_SOURCE_CHARS) {
            throw MolangSyntaxException(
                "longer than ${MolangLimits.MAX_SOURCE_CHARS} characters",
                1,
                MolangProblem.TOO_LONG,
                values = mapOf("limit" to MolangLimits.MAX_SOURCE_CHARS),
            )
        }
        while (true) {
            skipWhitespace()
            if (index >= source.length) {
                tokens += Token(TokenKind.END, index + 1)
                return tokens
            }
            tokens += next()
        }
    }

    private fun next(): Token {
        val c = source[index]
        return when {
            c.isAsciiDigit() || (c == '.' && peek(1).isAsciiDigit()) -> number()
            c.isNameStart() -> name()
            c == '\'' -> text()
            else -> symbol(c)
        }
    }

    private fun number(): Token {
        val start = index
        while (index < source.length && source[index].isAsciiDigit()) {
            index++
        }
        if (index < source.length && source[index] == '.') {
            index++
            while (index < source.length && source[index].isAsciiDigit()) {
                index++
            }
        }
        val value = source.substring(start, index).toDouble()
        return Token(TokenKind.NUMBER, start + 1, number = value)
    }

    private fun name(): Token {
        val start = index
        while (index < source.length && source[index].isNamePart()) {
            index++
        }
        return Token(TokenKind.NAME, start + 1, text = source.substring(start, index).lowercase())
    }

    private fun text(): Token {
        val start = index
        val end = source.indexOf('\'', start + 1)
        if (end < 0) {
            throw MolangSyntaxException("unterminated string", start + 1, MolangProblem.UNTERMINATED)
        }
        index = end + 1
        return Token(TokenKind.TEXT, start + 1, text = source.substring(start + 1, end))
    }

    private fun symbol(char: Char): Token {
        val column = index + 1
        val pair = pairOf(char, peek(1))
        if (pair != null) {
            index += 2
            return Token(pair, column)
        }
        val single = singleOf(char)
            ?: throw MolangSyntaxException(
                "unexpected character '$char'",
                column,
                MolangProblem.CHARACTER,
                mapOf("character" to char.toString()),
            )
        index++
        return Token(single, column)
    }

    private fun pairOf(char: Char, next: Char): TokenKind? = when {
        char == '=' && next == '=' -> TokenKind.EQUAL
        char == '!' && next == '=' -> TokenKind.NOT_EQUAL
        char == '<' && next == '=' -> TokenKind.LESS_EQUAL
        char == '>' && next == '=' -> TokenKind.GREATER_EQUAL
        char == '&' && next == '&' -> TokenKind.AND
        char == '|' && next == '|' -> TokenKind.OR
        else -> null
    }

    private fun singleOf(char: Char): TokenKind? = SINGLES[char]

    private fun peek(offset: Int): Char = if (index + offset < source.length) source[index + offset] else '\u0000'

    private fun skipWhitespace() {
        while (index < source.length && source[index] in WHITESPACE) {
            index++
        }
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun Char.isNameStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_'

    private fun Char.isNamePart(): Boolean = isNameStart() || isAsciiDigit()

    private companion object {
        const val WHITESPACE = " \t\n\r"

        val SINGLES: Map<Char, TokenKind> = mapOf(
            '.' to TokenKind.DOT,
            ',' to TokenKind.COMMA,
            ';' to TokenKind.SEMICOLON,
            '(' to TokenKind.OPEN_PAREN,
            ')' to TokenKind.CLOSE_PAREN,
            '{' to TokenKind.OPEN_BRACE,
            '}' to TokenKind.CLOSE_BRACE,
            '+' to TokenKind.PLUS,
            '-' to TokenKind.MINUS,
            '*' to TokenKind.STAR,
            '/' to TokenKind.SLASH,
            '%' to TokenKind.PERCENT,
            '!' to TokenKind.BANG,
            '<' to TokenKind.LESS,
            '>' to TokenKind.GREATER,
            '?' to TokenKind.QUESTION,
            ':' to TokenKind.COLON,
            '=' to TokenKind.ASSIGN,
        )
    }
}
