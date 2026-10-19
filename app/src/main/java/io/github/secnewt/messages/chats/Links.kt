package io.github.secnewt.messages.chats

/** A web address or email address found in a message, and where tapping it goes. */
data class Link(val start: Int, val end: Int, val target: String)

object Links {

    // Web addresses with a scheme or "www.", or a bare name ending in a common top-level domain
    // ("weather.gov/dfw"). Bare names need a known ending so "e.g." or "5.30" never become links.
    private val WEB = Regex(
        """(?i)\b(?:https?://[^\s<>"]+|www\.[^\s<>"]+|[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|org|net|gov|edu|io|me|app|us|co|tv|info|biz|ly|gl|gd|to|church|dev)\b(?:/[^\s<>"]*)?)""",
    )
    private val EMAIL = Regex("""(?i)\b[a-z0-9._%+-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+\b""")

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
        return (emails + webs).sortedBy { it.start }
    }
}
