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
}
