package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewsTest {

    @Test
    fun `an empty summary means look at the newest message`() {
        assertNull(Previews.fromSummary(""))
        assertNull(Previews.fromSummary("  "))
        assertEquals("See you at six", Previews.fromSummary("See you at six"))
    }

    @Test
    fun `a newest text shows its words, even after an earlier picture`() {
        assertEquals("On my way", Previews.of(Message(1, "On my way", 2)))
        // A group text is a picture message with only words in it.
        assertEquals("Lunch?", Previews.of(Message(-5, "Lunch?", 3, images = emptyList())))
    }

    @Test
    fun `a newest picture or file says what it is`() {
        assertEquals("📷 Picture", Previews.of(Message(-1, "", 1, images = listOf("content://p/1"))))
        assertEquals("📷 2 pictures", Previews.of(Message(-1, "", 1, images = listOf("content://p/1", "content://p/2"))))
        assertEquals("📎 menu.pdf", Previews.of(Message(-1, "", 1, files = listOf(Attachment("content://p/3", "menu.pdf", "application/pdf")))))
        assertEquals("Look at this", Previews.of(Message(-1, "Look at this", 1, images = listOf("content://p/1"))))
    }
}
