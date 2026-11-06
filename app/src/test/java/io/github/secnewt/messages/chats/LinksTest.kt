package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {

    private fun targets(text: String) = Links.find(text).map { it.target }
    private fun words(text: String) = Links.find(text).map { text.substring(it.start, it.end) }

    @Test
    fun `web addresses become links`() {
        assertEquals(listOf("https://example.com/a?b=1"), targets("see https://example.com/a?b=1"))
        assertEquals(listOf("https://www.weather.gov"), targets("www.weather.gov"))
        assertEquals(listOf("https://weather.gov/dfw"), targets("Check weather.gov/dfw."))
        assertEquals(listOf("weather.gov/dfw"), words("Check weather.gov/dfw."))
    }

    @Test
    fun `sentence punctuation stays out`() {
        assertEquals(listOf("https://ex.com/x"), words("(https://ex.com/x), right?"))
    }

    @Test
    fun `email addresses open mail`() {
        assertEquals(listOf("mailto:pastor@church.org"), targets("email pastor@church.org today"))
    }

    @Test
    fun `ordinary words and numbers aren't links`() {
        assertEquals(emptyList<String>(), targets("e.g. meet at 5.30, ok? Mr.Smith"))
        assertEquals(emptyList<String>(), targets("482913 is your code"))
    }

    @Test
    fun `phone numbers in the usual ways of writing them`() {
        assertEquals(listOf("tel:2145551234"), targets("call me at 214-555-1234 tonight"))
        assertEquals(listOf("tel:2145551234"), targets("(214) 555-1234"))
        assertEquals(listOf("tel:2145551234"), targets("214.555.1234."))
        assertEquals(listOf("tel:2145551234"), targets("2145551234"))
        assertEquals(listOf("tel:+12145551234"), targets("+1 214 555 1234"))
        assertEquals(listOf("tel:12145551234"), targets("1-214-555-1234"))
        assertEquals(listOf("tel:5551234"), targets("it's 555-1234"))
        assertEquals(listOf("tel:+442079460958"), targets("UK office +44 20 7946 0958"))
        assertEquals(listOf("(214) 555-1234"), words("Number: (214) 555-1234, thanks"))
        assertEquals("2145551234", Links.find("214-555-1234").single().number)
    }

    @Test
    fun `codes, prices, dates and times aren't phone numbers`() {
        assertEquals(emptyList<String>(), targets("482913 is your code"))
        assertEquals(emptyList<String>(), targets("total $1,234.56 due 2026-11-06 at 10:30"))
        assertEquals(emptyList<String>(), targets("order 12345678901234567890"))
        assertEquals(emptyList<String>(), targets("v1.2.3 build 5551234"))
        assertEquals(emptyList<String>(), targets("call 12/25/2026"))
    }

    @Test
    fun `numbers inside web addresses stay part of the address`() {
        assertEquals(listOf("https://ex.com/2145551234"), targets("https://ex.com/2145551234"))
        assertEquals(listOf("https://ex.com/a", "tel:2145551234"), targets("https://ex.com/a or 214-555-1234"))
    }

    @Test
    fun `web and email links have no number`() {
        assertEquals(null, Links.find("weather.gov").single().number)
    }
}
