package com.example.vani.ui.commands

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Understands spoken maths: "calculate 25 plus 30", "subtract 30 from 25", "multiply 12 by 8" ... */
object CalculatorParser {

    private val OPERATOR_WORDS = Regex(
        "\\b(?:plus|minus|times|multiply|multiplied|divide|divided|add|subtract|take away|over|percent of)\\b"
    )
    private val LEADING = Regex(
        "^(?:calculate|compute|solve|work out|what is|what s|whats|how much is|how much|tell me)\\s+"
    )
    private const val NUM = "(\\d+(?:\\.\\d+)?)"

    private class Expr(val symbolic: String, val spoken: String)

    /** Returns the answer sentence, or null when [cleanText] is not a maths question. */
    fun tryCalculate(cleanText: String): CommandResult? {
        val text = NumberWords.toDigits(cleanText)
        if (!text.any { it.isDigit() } || !OPERATOR_WORDS.containsMatchIn(text)) return null

        val expression = toExpression(text.replace(LEADING, "").trim()) ?: return null
        val value = try {
            Evaluator(expression.symbolic).evaluate()
        } catch (error: ArithmeticException) {
            return fail("Sorry, I can't divide by zero.")
        } catch (error: Exception) {
            return null
        }
        return ok("${expression.spoken} is ${format(value)}.")
    }

    private fun toExpression(t: String): Expr? {
        Regex("^subtract $NUM from $NUM$").matchEntire(t)?.let {
            val (a, b) = it.destructured
            return Expr("$b - $a", "$b minus $a")
        }
        Regex("^add $NUM (?:and|to|with) $NUM$").matchEntire(t)?.let {
            val (a, b) = it.destructured
            return Expr("$a + $b", "$a plus $b")
        }
        Regex("^multiply $NUM (?:by|and|with) $NUM$").matchEntire(t)?.let {
            val (a, b) = it.destructured
            return Expr("$a * $b", "$a times $b")
        }
        Regex("^divide $NUM by $NUM$").matchEntire(t)?.let {
            val (a, b) = it.destructured
            return Expr("$a / $b", "$a divided by $b")
        }
        Regex("^$NUM percent of $NUM$").matchEntire(t)?.let {
            val (a, b) = it.destructured
            return Expr("$b * $a / 100", "$a percent of $b")
        }

        val spoken = " $t "
            .replace(" take away ", " minus ")
            .replace(" multiplied by ", " times ")
            .replace(" over ", " divided by ")
            .trim()
        val symbolic = " $spoken "
            .replace(" plus ", " + ")
            .replace(" minus ", " - ")
            .replace(" times ", " * ")
            .replace(" divided by ", " / ")
            .trim()

        if (!Regex("^[\\d.\\s+\\-*/()]+$").matches(symbolic)) return null
        if (!Regex("[+\\-*/]").containsMatchIn(symbolic)) return null
        return Expr(symbolic, spoken)
    }

    private fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "not a number"
        val rounded = Math.round(value)
        if (abs(value - rounded) < 1e-9) return rounded.toString()
        return BigDecimal(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Small calculator with the usual order of operations. */
    private class Evaluator(private val s: String) {
        private var pos = 0

        fun evaluate(): Double {
            val value = parseSum()
            skip()
            if (pos != s.length) throw IllegalArgumentException("unexpected text")
            return value
        }

        private fun skip() {
            while (pos < s.length && s[pos] == ' ') pos++
        }

        private fun peek(c: Char): Boolean {
            skip()
            return pos < s.length && s[pos] == c
        }

        private fun parseSum(): Double {
            var value = parseProduct()
            while (true) {
                value = when {
                    peek('+') -> { pos++; value + parseProduct() }
                    peek('-') -> { pos++; value - parseProduct() }
                    else -> return value
                }
            }
        }

        private fun parseProduct(): Double {
            var value = parseFactor()
            while (true) {
                value = when {
                    peek('*') -> { pos++; value * parseFactor() }
                    peek('/') -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("divide by zero")
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skip()
            if (peek('-')) { pos++; return -parseFactor() }
            if (peek('+')) { pos++; return parseFactor() }
            if (peek('(')) {
                pos++
                val value = parseSum()
                if (!peek(')')) throw IllegalArgumentException("missing )")
                pos++
                return value
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("number expected")
            return s.substring(start, pos).toDouble()
        }
    }
}