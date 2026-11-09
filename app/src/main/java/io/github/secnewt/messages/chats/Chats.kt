package io.github.secnewt.messages.chats

/** Someone in a chat: their number (or a short code or sender name) and, if saved, their name. */
data class Person(
    val address: String,
    val name: String? = null,
    /** Their contact photo (a small one), if they're a contact with a photo. */
    val photo: String? = null,
    /** Their contact, for View contact / Edit contact. */
    val contact: String? = null,
) {
    val shownName: String get() = name ?: PhoneFormat.pretty(address)

    /** What picks their color when nobody chose one: unchanged by how the number is shown. */
    val colorName: String get() = name ?: address
}

/** Phone numbers the way people write them. */
object PhoneFormat {
    /**
     * "(214) 555-0155" for a US number, with or without its +1, so it fits on one line and
     * reads the way it's said. Numbers without +1 only on a US phone. Short codes, named senders and other countries' numbers are
     * left as they came.
     */
    fun pretty(address: String, country: String = java.util.Locale.getDefault().country): String {
        val trimmed = address.trim()
        if (trimmed.any { it.isLetter() }) return trimmed
        if (trimmed.any { !it.isDigit() && it !in "+-(). " }) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        // Without a +1, a number is only taken as American on an American phone.
        val us = country.equals("US", ignoreCase = true) || country.isEmpty()
        val national = when {
            digits.length == 11 && trimmed.startsWith("+1") -> digits.substring(1)
            us && digits.length == 10 && !trimmed.startsWith("+") -> digits
            us && digits.length == 11 && digits.startsWith("1") && !trimmed.startsWith("+") -> digits.substring(1)
            else -> return trimmed
        }
        return "(${national.substring(0, 3)}) ${national.substring(3, 6)}-${national.substring(6)}"
    }
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
    /** For my own messages: still going, sent, delivered, or failed (tap Try again). */
    val status: SendStatus = SendStatus.NONE,
) {
    val mine: Boolean get() = from == null
}

/** Where a message I sent has got to. Received messages are [NONE]. */
enum class SendStatus(val label: String) {
    NONE(""),
    SENDING("Sending…"),
    SENT("Sent"),
    DELIVERED("Delivered"),
    FAILED("Not sent"),
    /** Sent, but the carrier reported it never reached them. */
    NOT_DELIVERED("Not delivered"),
    ;

    /** Something went wrong: shown loudly, with Try again. */
    val failed: Boolean get() = this == FAILED || this == NOT_DELIVERED

    companion object {
        /** Past this, a message still "sending" is stuck (the app was closed mid-send): show it as failed. */
        const val STUCK_AFTER_MILLIS = 10 * 60_000L

        /**
         * A text's status from how Android filed it: its box (TYPE) and delivery report
         * (STATUS: -1 none, 0 delivered, 32 pending, 64 or more failed).
         */
        fun ofSms(type: Int, deliveryStatus: Int, timeMillis: Long, now: Long): SendStatus = when (type) {
            2 -> when { deliveryStatus == 0 -> DELIVERED; deliveryStatus >= 64 -> NOT_DELIVERED; else -> SENT } // Sent
            4, 6 -> if (now - timeMillis > STUCK_AFTER_MILLIS) FAILED else SENDING // Outbox, Queued
            5 -> FAILED
            else -> NONE
        }

        /** A picture or group message's status from its box: 2 Sent, 4 Outbox, 5 Failed. */
        fun ofMms(box: Int, timeMillis: Long, now: Long): SendStatus = when (box) {
            2 -> SENT
            4 -> if (now - timeMillis > STUCK_AFTER_MILLIS) FAILED else SENDING
            5 -> FAILED
            else -> NONE
        }
    }
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
    /** Each person's color here, by [ChatColors.key]; no two the same while there are enough. */
    val colors: Map<String, PersonHue> = ChatColors.assign(people, emptyMap()),
    /** Words I typed here and haven't sent yet. */
    val draft: String? = null,
    /** The newest message here is mine and didn't go (shown as "Not sent" in the list). */
    val failed: Boolean = false,
) {
    val isGroup: Boolean get() = people.size > 1
    val title: String get() = name ?: people.joinToString(", ") { it.shownName }

    /** Someone's color in this chat (someone no longer in it gets the color their name picks). */
    fun hue(person: Person): PersonHue = colors[ChatColors.key(person.address)] ?: PersonHue.fromName(person.colorName)
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

    /** One tab's chats: pinned first, then unread ones, then newest first. */
    fun inTab(chats: List<Chat>, tab: ChatTab): List<Chat> =
        chats.filter { tabFor(it) == tab }.sortedWith(
            compareByDescending<Chat> { it.pinned }.thenByDescending { it.unread > 0 }.thenByDescending { it.lastTimeMillis },
        )

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
