package io.github.secnewt.messages.chats

/** Someone in a chat: their number (or a short code or sender name) and, if saved, their name. */
data class Person(val address: String, val name: String? = null) {
    val shownName: String get() = name ?: address
}

/** A file in a picture message that isn't a picture: a PDF, a contact card, audio, video. */
data class Attachment(val uri: String, val name: String, val type: String)

/** An emoji someone put on a message. */
data class Reaction(val emoji: String, val from: Person?)

data class Message(
    val id: Long,
    val body: String,
    val timeMillis: Long,
    /** Who sent it; null when I did. */
    val from: Person? = null,
    val reactions: List<Reaction> = emptyList(),
    /** Pictures in a picture message, as content addresses. */
    val images: List<String> = emptyList(),
    /** Other files in a picture message. */
    val files: List<Attachment> = emptyList(),
    /** A picture or group message that hasn't downloaded yet (tap Download). */
    val waiting: Boolean = false,
) {
    val mine: Boolean get() = from == null
}

data class Chat(
    val threadId: Long,
    val people: List<Person>,
    val lastMessage: String,
    val lastTimeMillis: Long,
    val unread: Int = 0,
    val pinned: Boolean = false,
    /** A name I gave a group chat, like "Family". */
    val name: String? = null,
    /** No notifications from this chat (messages still arrive and count as unread). */
    val muted: Boolean = false,
) {
    val isGroup: Boolean get() = people.size > 1
    val title: String get() = name ?: people.joinToString(", ") { it.shownName }
}

/** The three tabs. */
enum class ChatTab(val label: String) {
    PEOPLE("People"),
    GROUPS("Groups"),
    CODES("Codes & alerts"),
}

object ChatSorting {

    /**
     * Groups are chats with more than one other person. Codes & alerts are senders that can't
     * be a person: short codes (like 72975) and named senders (like "AMAZON"). Everything
     * else, including any saved contact, is People.
     */
    fun tabFor(chat: Chat): ChatTab = when {
        chat.isGroup -> ChatTab.GROUPS
        chat.people.firstOrNull()?.let { it.name == null && isAutomated(it.address) } == true -> ChatTab.CODES
        else -> ChatTab.PEOPLE
    }

    /** A short code (3 to 8 digits) or a sender with letters in its "number". */
    fun isAutomated(address: String): Boolean {
        val trimmed = address.trim()
        if (trimmed.any { it.isLetter() }) return true
        val digits = trimmed.filter { it.isDigit() }
        return digits.length in 3..8 && trimmed.none { it == '+' }
    }

    /** One tab's chats: pinned first, then newest first. */
    fun inTab(chats: List<Chat>, tab: ChatTab): List<Chat> =
        chats.filter { tabFor(it) == tab }.sortedWith(compareByDescending<Chat> { it.pinned }.thenByDescending { it.lastTimeMillis })

    /** Every group with this person in it, for "search by member". */
    fun groupsWith(chats: List<Chat>, query: String): List<Chat> {
        val wanted = query.trim().lowercase()
        if (wanted.isEmpty()) return emptyList()
        return chats.filter { chat ->
            chat.isGroup && chat.people.any { p ->
                p.name?.lowercase()?.contains(wanted) == true || digitsMatch(p.address, wanted)
            }
        }
    }

    private fun digitsMatch(address: String, query: String): Boolean {
        val q = query.filter { it.isDigit() }
        return q.length >= 3 && address.filter { it.isDigit() }.contains(q)
    }
}
