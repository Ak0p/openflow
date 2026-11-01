package ro.akop.openflow.core.post

/**
 * Cheap, predictable clean-up of raw dictation: filler words, spoken punctuation,
 * sentence capitalisation and a terminal period.
 *
 * Deliberately deterministic and testable — this is the baseline that a real model
 * post-processor will be measured against later.
 */
class RuleBasedTextPostProcessor : TextPostProcessor {

    override suspend fun process(raw: String): String = format(raw)

    fun format(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text

        text = FILLERS.replace(text, " ")
        text = HORIZONTAL_SPACE.replace(text, " ").trim()

        text = NEW_LINE.replace(text, "\n")
        text = QUESTION_MARK.replace(text, "?")
        text = EXCLAMATION_MARK.replace(text, "!")
        text = PERIOD.replace(text, ".")
        text = COMMA.replace(text, ",")

        text = LEADING_PUNCTUATION.replace(text, "")
        text = SPACE_BEFORE_PUNCTUATION.replace(text, "$1")
        text = SPACE_AROUND_NEWLINE.replace(text, "\n")
        text = PUNCTUATE_BEFORE_NEWLINE.replace(text, "$1.\n")

        text = capitalizeSentences(text)
        text = ensureTerminalPunctuation(text)
        return text.trim()
    }

    private fun capitalizeSentences(input: String): String {
        val out = StringBuilder(input.length)
        var capitalizeNext = true
        for (char in input) {
            if (capitalizeNext && char.isLetter()) {
                out.append(char.uppercaseChar())
                capitalizeNext = false
            } else {
                out.append(char)
                if (char == '.' || char == '!' || char == '?' || char == '\n') {
                    capitalizeNext = true
                }
            }
        }
        return out.toString()
    }

    private fun ensureTerminalPunctuation(input: String): String {
        val trimmed = input.trimEnd()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.last().isLetterOrDigit()) "$trimmed." else trimmed
    }

    private companion object {
        private const val IGNORE_CASE = "(?i)"

        private val FILLERS = Regex(
            "$IGNORE_CASE" + "\\b(?:um+|uh+|uhm+|erm+|hm+|mm+)\\b[,.]?" +
                "|\\b(?:ăă+|îî+)\\b[,.]?",
        )

        private val HORIZONTAL_SPACE = Regex("[ \\t]+")

        private val NEW_LINE = Regex(
            "$IGNORE_CASE\\b(?:new line|newline|line break|linie nouă|rând nou)\\b",
        )
        private val QUESTION_MARK = Regex(
            "$IGNORE_CASE\\b(?:question mark|semn de întrebare|semnul întrebării)\\b",
        )
        private val EXCLAMATION_MARK = Regex(
            "$IGNORE_CASE\\b(?:exclamation mark|semnul exclamării)\\b",
        )
        private val PERIOD = Regex("$IGNORE_CASE\\b(?:full stop|period|punct)\\b")
        private val COMMA = Regex("$IGNORE_CASE\\b(?:comma|virgulă)\\b")

        private val LEADING_PUNCTUATION = Regex("^[\\s,.;:!?]+")
        private val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([,.;:!?])")
        private val SPACE_AROUND_NEWLINE = Regex("[ \\t]*\\n[ \\t]*")
        private val PUNCTUATE_BEFORE_NEWLINE = Regex("([\\p{L}\\p{N}])\\n")
    }
}
