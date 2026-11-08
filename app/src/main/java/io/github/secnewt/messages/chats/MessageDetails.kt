package io.github.secnewt.messages.chats

/** One person a message was from or to; [me] when it's this phone's own number. */
data class Addressee(val person: Person, val me: Boolean)

/**
 * What Details shows for one message: who it was really from and to, as stored with it, and
 * when. For a group message this is the full To and Cc list it arrived with, so it shows
 * whether a message belongs in the chat it's in.
 */
data class MessageDetails(
    /** A picture or group message (MMS) rather than a plain text. */
    val picture: Boolean,
    val from: Addressee?,
    val to: List<Addressee>,
    val cc: List<Addressee> = emptyList(),
    /** When the sender's phone sent it, if it says. */
    val sentMillis: Long? = null,
    /** When this phone stored it. */
    val receivedMillis: Long,
) {
    companion object {
        // MMS address kinds, as Android stores them (PduHeaders FROM, TO, CC, BCC).
        const val FROM = 137
        const val TO = 151
        const val CC = 130
        const val BCC = 129

        /** Android writes this in place of my number on messages I send. */
        private const val MY_TOKEN = "insert-address-token"

        /**
         * Sorts a picture message's stored addresses, (number, kind), into from / to / cc,
         * marking mine. Bcc goes with Cc. Each person once per list, in stored order.
         */
        fun sort(
            rows: List<Pair<String, Int>>,
            myNumbers: List<String>,
            same: (String, String) -> Boolean,
            person: (String) -> Person,
        ): Triple<Addressee?, List<Addressee>, List<Addressee>> {
            val mine = myNumbers.filter { it.any(Char::isDigit) }
            fun entry(number: String) = if (number == MY_TOKEN) {
                Addressee(Person(""), me = true)
            } else {
                Addressee(person(number), me = mine.any { same(number, it) })
            }
            fun list(vararg kinds: Int) = rows.filter { it.second in kinds }.map { it.first }
                .fold(mutableListOf<String>()) { out, n -> if (out.none { same(it, n) }) out += n; out }
                .map(::entry)
            return Triple(list(FROM).firstOrNull(), list(TO), list(CC, BCC))
        }
    }
}
