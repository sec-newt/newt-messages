package io.github.secnewt.messages.reactions

import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Person
import io.github.secnewt.messages.chats.Reaction
import org.junit.Assert.assertEquals
import org.junit.Test

class FoldingTest {

    private val becca = Person("+19725550111", "Becca")
    private val sam = Person("+12145550122", "Sam")
    private var next = 0L
    private fun msg(body: String, from: Person? = null) = Message(++next, body, next * 1000, from)

    @Test
    fun `a reaction text becomes an emoji on its message`() {
        val folded = Folding.fold(listOf(msg("Let's do lunch"), msg("Loved “Let's do lunch”", becca), msg("Noon?", becca)))
        assertEquals(listOf("Let's do lunch", "Noon?"), folded.map { it.body })
        assertEquals(listOf(Reaction("❤️", becca)), folded[0].reactions)
    }

    @Test
    fun `several people, and a removal`() {
        val folded = Folding.fold(
            listOf(
                msg("Brisket Saturday"),
                msg("Loved “Brisket Saturday”", becca),
                msg("Liked “Brisket Saturday”", sam),
                msg("Removed a heart from “Brisket Saturday”", becca),
            ),
        )
        assertEquals(1, folded.size)
        assertEquals(listOf(Reaction("👍", sam)), folded[0].reactions)
    }

    @Test
    fun `the same person reacting twice keeps one`() {
        val folded = Folding.fold(listOf(msg("hi"), msg("Loved “hi”", becca), msg("Loved “hi”", becca)))
        assertEquals(listOf(Reaction("❤️", becca)), folded[0].reactions)
    }

    @Test
    fun `a reaction to a message that isn't here shows as a short line`() {
        val folded = Folding.fold(listOf(msg("Liked “something from last year”", becca)))
        assertEquals(listOf("👍 to “something from last year”"), folded.map { it.body })
        // A removal of something not here just disappears.
        assertEquals(emptyList<String>(), Folding.fold(listOf(msg("Removed a like from “gone”", becca))).map { it.body })
    }

    @Test
    fun `my own reactions count too`() {
        val folded = Folding.fold(listOf(msg("Home by 6", becca), msg("Liked “Home by 6”")))
        assertEquals(listOf(Reaction("👍", null)), folded[0].reactions)
    }
}
