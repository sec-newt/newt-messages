package io.github.secnewt.messages.chats

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How messages sit in a chat, like other messaging apps: a "Today" or "Mon, Nov 3" line
 * where the day changes, and messages from one person close together grouped, so the name
 * shows once at the top, the picture once at the bottom, and the time once at the end.
 */
object Timeline {

    /** Messages further apart than this start a new group, with their own time. */
    const val GROUP_GAP_MILLIS = 5 * 60_000L

    /** Where one message sits. [day] is set on the first message of each day. */
    data class Place(
        val day: String?,
        /** The first of a run from one person: their name shows above it. */
        val first: Boolean,
        /** The last of a run: their picture and the time show here. */
        val last: Boolean,
    )

    /** One [Place] per message, oldest first like [messages]. */
    fun place(messages: List<Message>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<Place> =
        messages.mapIndexed { i, message ->
            val before = messages.getOrNull(i - 1)
            val after = messages.getOrNull(i + 1)
            val newDay = before == null || !sameDay(before.timeMillis, message.timeMillis, zone)
            Place(
                day = if (newDay) day(message.timeMillis, now, zone, locale) else null,
                first = before == null || newDay || !together(before, message, zone),
                last = after == null || !together(message, after, zone),
            )
        }

    /** Two messages in a row that belong in one group: same sender, same day, close in time. */
    private fun together(a: Message, b: Message, zone: ZoneId): Boolean =
        sameSender(a, b) && sameDay(a.timeMillis, b.timeMillis, zone) && b.timeMillis - a.timeMillis in 0..GROUP_GAP_MILLIS

    private fun sameSender(a: Message, b: Message): Boolean = when {
        a.mine || b.mine -> a.mine && b.mine
        else -> ChatColors.key(a.from!!.address) == ChatColors.key(b.from!!.address)
    }

    private fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    /** "Today", "Yesterday", "Monday" (this week), "Mon, Nov 3" (this year), or "Nov 3, 2025". */
    fun day(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = today.toEpochDay() - date.toEpochDay()
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days in 2..6 -> date.format(DateTimeFormatter.ofPattern("EEEE", locale))
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
            else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
        }
    }
}
