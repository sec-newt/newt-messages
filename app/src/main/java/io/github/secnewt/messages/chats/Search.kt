package io.github.secnewt.messages.chats

/** A message that matched a search, and the chat it's in. */
data class SearchHit(val threadId: Long, val body: String, val timeMillis: Long)

object Search {

    /** Chats whose title, or any member's name or number, matches. */
    fun chats(chats: List<Chat>, query: String): List<Chat> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        return chats.filter { chat ->
            chat.title.lowercase().contains(q) ||
                chat.people.any { p -> p.name?.lowercase()?.contains(q) == true || (digits.length >= 3 && p.address.filter(Char::isDigit).contains(digits)) }
        }
    }

    /** The part of [body] around the first match, so long messages show why they matched. */
    fun excerpt(body: String, query: String, radius: Int = 40): String {
        val at = body.lowercase().indexOf(query.trim().lowercase())
        if (at < 0 || body.length <= radius * 2) return body
        val start = (at - radius).coerceAtLeast(0)
        val end = (at + query.length + radius).coerceAtMost(body.length)
        return (if (start > 0) "…" else "") + body.substring(start, end).trim() + (if (end < body.length) "…" else "")
    }
}
