package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipientsTest {

    private val people = listOf(
        ContactNumber("Becca Maples", "+19725550111", "Mobile"),
        ContactNumber("Pastor Dave", "+14695550133"),
        ContactNumber("Bob", "+12145550122"),
        ContactNumber("Rebecca Stone", "+18175550144"),
    )

    @Test
    fun `names match by word start, best first`() {
        assertEquals(listOf("Becca Maples", "Rebecca Stone"), Recipients.search(people, "bec").map { it.name })
        assertEquals(listOf("Becca Maples"), Recipients.search(people, "map").map { it.name })
        assertEquals(listOf("Pastor Dave"), Recipients.search(people, "dave").map { it.name })
    }

    @Test
    fun `numbers match by digits`() {
        assertEquals(listOf("Bob"), Recipients.search(people, "214-555").map { it.name })
        assertEquals(emptyList<String>(), Recipients.search(people, "55").map { it.name })
    }

    @Test
    fun `empty search lists everyone A to Z`() {
        assertEquals(listOf("Becca Maples", "Bob", "Pastor Dave", "Rebecca Stone"), Recipients.search(people, "").map { it.name })
    }

    @Test
    fun `typed numbers`() {
        assertEquals("(972) 555-0111", Recipients.typedNumber(" (972) 555-0111 "))
        assertEquals("262966", Recipients.typedNumber("262966"))
        assertNull(Recipients.typedNumber("becca"))
        assertNull(Recipients.typedNumber("12"))
    }
}
