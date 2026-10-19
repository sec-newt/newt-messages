package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSortingTest {

    private val becca = Person("+19725550111", "Becca")
    private val sam = Person("2145550122")
    private fun chat(id: Long, vararg people: Person, time: Long = id, pinned: Boolean = false) =
        Chat(id, people.toList(), "hi", time, pinned = pinned)

    @Test
    fun `chats land in the right tab`() {
        assertEquals(ChatTab.PEOPLE, ChatSorting.tabFor(chat(1, becca)))
        assertEquals(ChatTab.PEOPLE, ChatSorting.tabFor(chat(2, sam)))
        assertEquals(ChatTab.GROUPS, ChatSorting.tabFor(chat(3, becca, sam)))
        assertEquals(ChatTab.CODES, ChatSorting.tabFor(chat(4, Person("72975"))))
        assertEquals(ChatTab.CODES, ChatSorting.tabFor(chat(5, Person("AMAZON"))))
        // A saved contact is a person, even with a short number.
        assertEquals(ChatTab.PEOPLE, ChatSorting.tabFor(chat(6, Person("72975", "Pharmacy"))))
    }

    @Test
    fun `short codes and named senders are automatic, phone numbers aren't`() {
        assertTrue(ChatSorting.isAutomated("262966"))
        assertTrue(ChatSorting.isAutomated("Chase"))
        assertFalse(ChatSorting.isAutomated("+1 (972) 555-0111"))
        assertFalse(ChatSorting.isAutomated("9725550111"))
    }

    @Test
    fun `pinned first, then newest`() {
        val chats = listOf(chat(1, becca, time = 10), chat(2, sam, time = 30), chat(3, Person("4695550133"), time = 20, pinned = true))
        assertEquals(listOf(3L, 2L, 1L), ChatSorting.inTab(chats, ChatTab.PEOPLE).map { it.threadId })
    }

    @Test
    fun `unread chats come right after pinned ones`() {
        val chats = listOf(
            chat(1, becca, time = 30),
            chat(2, sam, time = 10).copy(unread = 2),
            chat(3, Person("4695550133"), time = 5, pinned = true),
        )
        assertEquals(listOf(3L, 2L, 1L), ChatSorting.inTab(chats, ChatTab.PEOPLE).map { it.threadId })
    }

    @Test
    fun `search by member finds every group with them`() {
        val family = chat(1, becca, sam).copy(name = "Family")
        val church = chat(2, becca, Person("4695550133"))
        val solo = chat(3, becca)
        val chats = listOf(family, church, solo)
        assertEquals(listOf(1L, 2L), ChatSorting.groupsWith(chats, "becca").map { it.threadId })
        assertEquals(listOf(1L), ChatSorting.groupsWith(chats, "555-0122").map { it.threadId })
        assertTrue(ChatSorting.groupsWith(chats, "").isEmpty())
    }
}
