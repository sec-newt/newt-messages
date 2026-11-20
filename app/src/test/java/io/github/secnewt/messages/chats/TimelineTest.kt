package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TimelineTest {

    private val zone = ZoneId.of("America/Chicago")
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 11, year: Int = 2026) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(9, 12) // Monday, Nov 9 2026
    private val sam = Person("+12145550122", "Sam")
    private val mom = Person("+19725550144", "Mom")

    private fun place(vararg messages: Message) = Timeline.place(messages.toList(), now, zone, Locale.US)

    @Test
    fun `days read the way people say them`() {
        fun day(millis: Long) = Timeline.day(millis, now, zone, Locale.US)
        assertEquals("Today", day(at(9, 1)))
        assertEquals("Yesterday", day(at(8, 23, 59)))
        assertEquals("Thursday", day(at(5, 10)))
        assertEquals("Mon, Nov 2", day(at(2, 10)))
        assertEquals("Mar 4, 2025", day(at(4, 10, month = 3, year = 2025)))
    }

    @Test
    fun `one person's messages close together are one group`() {
        val places = place(
            Message(1, "I'll bring the chairs", at(9, 10), from = sam),
            Message(2, "And the table", at(9, 10, 2), from = sam),
            Message(3, "Thanks!", at(9, 10, 3), from = mom),
        )
        assertEquals(Timeline.Place(day = "Today", first = true, last = false), places[0])
        assertEquals(Timeline.Place(day = null, first = false, last = true), places[1])
        assertEquals(Timeline.Place(day = null, first = true, last = true), places[2])
    }

    @Test
    fun `my messages group together, and never with someone else's`() {
        val places = place(
            Message(1, "On my way", at(9, 9)),
            Message(2, "Parking now", at(9, 9, 4)),
            Message(3, "See you", at(9, 9, 5), from = sam),
        )
        assertEquals(listOf(true, false, true), places.map { it.first })
        assertEquals(listOf(false, true, true), places.map { it.last })
    }

    @Test
    fun `a long pause or a new day starts a new group`() {
        val places = place(
            Message(1, "Late night", at(8, 23, 58), from = sam),
            Message(2, "Morning", at(9, 0, 1), from = sam),
            Message(3, "Later", at(9, 0, 30), from = sam),
        )
        assertEquals(listOf("Yesterday", "Today", null), places.map { it.day })
        assertEquals(listOf(true, true, true), places.map { it.first })
        assertEquals(listOf(true, true, true), places.map { it.last })
    }

    @Test
    fun `the same number written two ways is the same sender`() {
        val places = place(
            Message(1, "a", at(9, 10), from = Person("+12145550122")),
            Message(2, "b", at(9, 10, 1), from = Person("2145550122")),
        )
        assertEquals(listOf(true, false), places.map { it.first })
    }

    @Test
    fun `US numbers are written the usual way, others are left alone`() {
        assertEquals("(214) 555-0155", PhoneFormat.pretty("+12145550155"))
        assertEquals("(214) 555-0155", PhoneFormat.pretty("2145550155"))
        assertEquals("(214) 555-0155", PhoneFormat.pretty("12145550155"))
        assertEquals("(214) 555-0155", PhoneFormat.pretty("(214) 555-0155"))
        assertEquals("262966", PhoneFormat.pretty("262966"))
        assertEquals("AMAZON", PhoneFormat.pretty("AMAZON"))
        assertEquals("+442071234567", PhoneFormat.pretty("+442071234567"))
        assertEquals("me@example.com", PhoneFormat.pretty("me@example.com"))
        // Off a US phone, only a number with +1 is surely American.
        assertEquals("9876543210", PhoneFormat.pretty("9876543210", country = "IN"))
        assertEquals("(214) 555-0155", PhoneFormat.pretty("+12145550155", country = "GB"))
    }

    @Test
    fun `showing numbers nicely doesn't change anyone's color`() {
        val stranger = Person("+12145550155")
        assertEquals("(214) 555-0155", stranger.shownName)
        assertEquals(PersonHue.fromName("+12145550155"), Chat(1, listOf(stranger, sam), "", 0, colors = emptyMap()).hue(stranger))
    }
}
