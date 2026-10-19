package io.github.secnewt.messages.share

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCountTest {

    @Test
    fun `reactions don't count, picture messages do`() {
        assertEquals(1, UnreadProvider.count(listOf("Sounds good", "Liked “Sounds good”"), 0))
        assertEquals(3, UnreadProvider.count(listOf("hi", "there"), 1))
        assertEquals(0, UnreadProvider.count(emptyList(), 0))
        assertEquals(999, UnreadProvider.count(List(1200) { "x" }, 0))
    }
}
