package io.github.secnewt.messages.chats

/** A web address, email address or phone number found in a message, and where tapping it goes. */
data class Link(val start: Int, val end: Int, val target: String) {
    /** A phone number, written as dialable digits: tapping it offers call, text, copy, add to contacts. */
    val number: String? get() = target.removePrefix("tel:").takeIf { target.startsWith("tel:") }
}

object Links {

    // Web addresses with a scheme or "www.", or a bare name ending in a common top-level domain
    // ("weather.gov/dfw"). Bare names need a known ending so "e.g." or "5.30" never become links.
    private val WEB = Regex(
        """(?i)\b(?:https?://[^\s<>"]+|www\.[^\s<>"]+|[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|org|net|gov|edu|io|me|app|us|co|tv|info|biz|ly|gl|gd|to|church|dev)\b(?:/[^\s<>"]*)?)""",
    )
    private val EMAIL = Regex("""(?i)\b[a-z0-9._%+-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+\b""")

    // Phone numbers: US-style (214-555-1234, (214) 555-1234, 214.555.1234, 2145551234, with or
    // without +1), local 555-1234 (a dash or dot needed), and international +44 20 7946 0958.
    // Never part of a longer run of digits or letters, so codes, prices and dates stay plain.
    private val PHONE = Regex(
        """(?<![\w+\-./])(?:\+\d{1,3}[\s.-]?\(?\d{1,4}\)?(?:[\s.-]?\d{2,4}){2,4}|(?:1[\s.-]?)?(?:\(\d{3}\)\s?|\d{3}[\s.-]?)\d{3}[\s.-]?\d{4}|\d{3}[.-]\d{4})(?![\w\-]|\.\d)""",
    )

    /** Every link in [text], in order, never overlapping. */
    fun find(text: String): List<Link> {
        val emails = EMAIL.findAll(text).map { Link(it.range.first, it.range.last + 1, "mailto:${it.value}") }.toList()
        val webs = WEB.findAll(text).mapNotNull { match ->
            // A sentence's last period, comma or bracket isn't part of the address.
            val value = match.value.trimEnd('.', ',', '!', '?', ')', ']', ';', ':', '\'', '"')
            val start = match.range.first
            if (emails.any { start >= it.start && start < it.end }) return@mapNotNull null
            val target = if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
            Link(start, start + value.length, target)
        }.toList()
        val taken = emails + webs
        val phones = PHONE.findAll(text).mapNotNull { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length !in 7..15) return@mapNotNull null
            val start = match.range.first
            val end = match.range.last + 1
            if (taken.any { start < it.end && end > it.start }) return@mapNotNull null
            Link(start, end, "tel:" + (if (match.value.startsWith("+")) "+" else "") + digits)
        }.toList()
        return (taken + phones).sortedBy { it.start }
    }
}
