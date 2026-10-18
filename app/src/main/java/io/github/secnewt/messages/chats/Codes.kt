package io.github.secnewt.messages.chats

/** Spots a one-time sign-in code in a text, so the notification can offer to copy it. */
object Codes {

    // A code word, then (within a few words) 4 to 8 digits, or the digits first and the word after.
    private val WORD = """(?:code|passcode|pin|otp|verification|verify|one-time|security|login|sign-in)"""
    private val AFTER_WORD = Regex("""(?i)\b$WORD\b[^\d\n]{0,30}?(?<![\d-])(\d{4,8}|\d{3}[- ]\d{3})(?![\d-])""")
    private val BEFORE_WORD = Regex("""(?i)(?<![\d-])(\d{4,8}|\d{3}[- ]\d{3})(?![\d-])[^\d\n]{0,20}?\b(?:is your|is the)\b[^\n]{0,30}?\b$WORD\b""")

    /** The code, digits only ("482913"), or null when the text doesn't look like one. */
    fun find(body: String): String? {
        val match = BEFORE_WORD.find(body) ?: AFTER_WORD.find(body) ?: return null
        val code = match.groupValues[1].filter { it.isDigit() }
        // A 4-digit number like 2026 is more likely a year ("code of conduct 2026") than a code.
        if (code.length == 4 && code.toInt() in 1900..2099) return null
        return code
    }
}
