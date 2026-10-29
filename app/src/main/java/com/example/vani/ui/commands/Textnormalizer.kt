package com.example.vani.ui.commands

/** One place that cleans up what the speech recognizer heard. */
object TextNormalizer {

    private val WAKE_PREFIX =
        Regex("^(?:(?:hey|hi|hello|ok|okay)\\s+)?(?:vani|controller|assistant)\\b\\s*")
    private val POLITE_PREFIX =
        Regex("^(?:please|kindly|can you|could you|would you|will you)\\s+")

    /**
     * "Hey controller, turn on Wi-Fi!"  ->  "turn on wifi"
     * Lowercase, no punctuation, common speech mistakes fixed, wake word removed.
     */
    fun clean(raw: String): String {
        var text = raw.lowercase()
            .replace("%", " percent ")
            .replace("what's app", "whatsapp")
            .replace("wi-fi", "wifi")
        text = text.replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        text = text
            .replace("wi fi", "wifi")
            .replace("what s app", "whatsapp")
            .replace("whats app", "whatsapp")
            .replace("you tube", "youtube")
            .replace("insta gram", "instagram")
            .replace("flash light", "flashlight")
            .replace("blue tooth", "bluetooth")
            .replace("air plane", "airplane")
            .replace("screen shot", "screenshot")
            .replace("spot ify", "spotify")
        text = text.replace(WAKE_PREFIX, "").trim()
        text = text.replace(POLITE_PREFIX, "").replace(Regex("\\s+please$"), "").trim()
        return text
    }

    /** How many single-letter edits turn [a] into [b] (used to forgive small speech mistakes). */
    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val above = row[j]
                row[j] = minOf(row[j] + 1, row[j - 1] + 1, diagonal + if (a[i - 1] == b[j - 1]) 0 else 1)
                diagonal = above
            }
        }
        return row[b.length]
    }
}

/** Turns spoken numbers into digits: "set brightness to fifty percent" -> "... 50 percent". */
object NumberWords {

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    fun toDigits(text: String): String {
        val tokens = text.split(" ")
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val parsed = parseNumber(tokens, i)
            if (parsed != null) {
                out.add(parsed.first)
                i = parsed.second
            } else {
                out.add(tokens[i])
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun isNumberWord(word: String) =
        word in UNITS || word in TENS || word == "hundred" || word == "thousand"

    private fun parseNumber(tokens: List<String>, start: Int): Pair<String, Int>? {
        var index = start
        var total = 0
        var current = 0
        var any = false
        var previous = ""

        while (index < tokens.size) {
            val word = tokens[index]
            when {
                word in UNITS -> { current += UNITS.getValue(word); any = true }
                word in TENS -> { current += TENS.getValue(word); any = true }
                word == "hundred" && any -> { current = (if (current == 0) 1 else current) * 100 }
                word == "thousand" && any -> { total += (if (current == 0) 1 else current) * 1000; current = 0 }
                word == "and" && any && (previous == "hundred" || previous == "thousand") &&
                        index + 1 < tokens.size && isNumberWord(tokens[index + 1]) -> { /* "one hundred and five" */ }
                else -> break
            }
            previous = word
            index++
        }
        if (!any) return null

        var result = (total + current).toString()

        // decimals: "three point five"
        if (index + 1 < tokens.size && tokens[index] == "point" && tokens[index + 1] in UNITS) {
            val digits = StringBuilder()
            var j = index + 1
            while (j < tokens.size && tokens[j] in UNITS && UNITS.getValue(tokens[j]) < 10) {
                digits.append(UNITS.getValue(tokens[j]))
                j++
            }
            if (digits.isNotEmpty()) {
                result = "$result.$digits"
                index = j
            }
        }
        return result to index
    }

    /** "first" -> 1, "3rd" -> 3, "top most" -> 1. Null when it is not a position word. */
    fun ordinal(word: String): Int? = when (word.trim()) {
        "first", "1st", "1", "one", "top", "topmost", "top most" -> 1
        "second", "2nd", "2", "two" -> 2
        "third", "3rd", "3", "three" -> 3
        "fourth", "4th", "4", "four" -> 4
        "fifth", "5th", "5", "five" -> 5
        else -> null
    }
}