package io.github.secnewt.messages.chats

/** A saved contact's number, for picking who to text. */
data class ContactNumber(val name: String, val number: String, val label: String? = null)

/** Finding people to start a chat with. */
object Recipients {

    /**
     * Contacts whose name has a word starting with [query] ("bec" finds "Becca Maples"),
     * or whose number contains its digits (3 or more). Best matches first, then A to Z.
     */
    fun search(contacts: List<ContactNumber>, query: String): List<ContactNumber> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return contacts.sortedBy { it.name.lowercase() }
        val digits = q.filter { it.isDigit() }
        return contacts.mapNotNull { c ->
            val name = c.name.lowercase()
            val score = when {
                name.startsWith(q) -> 0
                name.split(' ', '-', '.').any { it.startsWith(q) } -> 1
                name.contains(q) -> 2
                digits.length >= 3 && c.number.filter { it.isDigit() }.contains(digits) -> 3
                else -> return@mapNotNull null
            }
            score to c
        }.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() })).map { it.second }
    }

    /** What's typed, if it's a phone number to text directly (at least 3 digits, phone characters only). */
    fun typedNumber(query: String): String? {
        val t = query.trim()
        return t.takeIf { it.count(Char::isDigit) >= 3 && it.all { ch -> ch.isDigit() || ch in "+-() ." } }
    }
}
