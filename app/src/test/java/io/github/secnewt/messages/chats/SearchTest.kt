package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {

    private val becca = Person("+19725550111", "Becca")
    private val sam = Person("+12145550122")
    private val chats = listOf(
        Chat(1, listOf(becca), "hi", 1),
        Chat(2, listOf(becca, sam), "hey", 2, name = "Family"),
        Chat(3, listOf(sam), "yo", 3),
    )

    @Test
    fun `chats match by title, member name, or number`() {
        assertEquals(listOf(2L), Search.chats(chats, "fam").map { it.threadId })
        assertEquals(listOf(1L, 2L), Search.chats(chats, "becca").map { it.threadId })
        assertEquals(listOf(2L, 3L), Search.chats(chats, "214-555").map { it.threadId })
        assertEquals(emptyList<Long>(), Search.chats(chats, " ").map { it.threadId })
    }

    @Test
    fun `long messages show the part that matched`() {
        val body = "a".repeat(100) + " brisket at six " + "b".repeat(100)
        val shown = Search.excerpt(body, "brisket")
        assertEquals(true, shown.startsWith("…") && shown.endsWith("…") && "brisket" in shown)
        assertEquals("short brisket note", Search.excerpt("short brisket note", "brisket"))
    }
}
