package io.github.secnewt.messages.mms

import android.telephony.PhoneNumberUtils

/**
 * Who an incoming picture or group message is from and to, minus me: that set of people is
 * the chat it belongs in.
 *
 * Android's own version guessed: when "To" held only one number, it assumed that number was
 * mine and dropped it. Some phones and carriers put one member in "To" and everyone else in
 * "Cc", so a group message from them landed in a smaller group with someone missing. Here,
 * when my number is known only my number is dropped; the old guess is kept only for when it
 * isn't known, since then there's no other way to leave myself out.
 */
object GroupRecipients {

    fun pick(
        from: List<String>,
        to: List<String>,
        cc: List<String>,
        myNumbers: List<String>,
        same: (String, String) -> Boolean = PhoneNumberUtils::compare,
    ): Set<String> {
        val mine = myNumbers.filter { it.any(Char::isDigit) }
        val people = LinkedHashSet<String>()
        people += from
        val others = if (mine.isEmpty()) {
            (if (to.size == 1) emptyList() else to) + cc
        } else {
            (to + cc).filterNot { number -> mine.any { same(number, it) } }
        }
        others.filterTo(people) { number -> people.none { same(number, it) } }
        return people
    }
}
