package io.github.secnewt.messages.reactions

import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionsTest {

    @Test
    fun `iPhone tapbacks become emoji`() {
        assertEquals(ParsedReaction("❤️", "Let's do lunch"), Reactions.parse("Loved “Let's do lunch”"))
        assertEquals(ParsedReaction("👍", "ok"), Reactions.parse("Liked “ok”"))
        assertEquals(ParsedReaction("😂", "lol you"), Reactions.parse("Laughed at “lol you”"))
        assertEquals(ParsedReaction("‼️", "Tomorrow!"), Reactions.parse("Emphasized “Tomorrow!”"))
        assertEquals(ParsedReaction("❓", "8pm?"), Reactions.parse("Questioned “8pm?”"))
        assertEquals(ParsedReaction("👎", "Rain"), Reactions.parse("Disliked “Rain”"))
    }

    @Test
    fun `any emoji and removals`() {
        assertEquals(ParsedReaction("🙏", "Prayers for your mom"), Reactions.parse("Reacted 🙏 to “Prayers for your mom”"))
        assertEquals(ParsedReaction("❤️", "Let's do lunch", removed = true), Reactions.parse("Removed a heart from “Let's do lunch”"))
        assertEquals(ParsedReaction("🙏", "x", removed = true), Reactions.parse("Removed 🙏 from “x”"))
    }

    @Test
    fun `straight quotes and multi-line quotes work`() {
        assertEquals(ParsedReaction("👍", "see you"), Reactions.parse("Liked \"see you\""))
        assertEquals(ParsedReaction("❤️", "line one\nline two"), Reactions.parse("Loved “line one\nline two”"))
    }

    @Test
    fun `Google Messages reactions with invisible characters`() {
        val added = " ​👍​ \"Sounds good\"  "
        assertEquals(ParsedReaction("👍", "\"Sounds good\""), Reactions.parse(added))
        val removed = " ‌👍‌ \"Sounds good\"  "
        assertEquals(true, Reactions.parse(removed)?.removed)
    }

    @Test
    fun `ordinary texts are not reactions`() {
        assertNull(Reactions.parse("I loved “Dune” by the way"))
        assertNull(Reactions.parse("Liked it a lot"))
        assertNull(Reactions.parse("Reacted with a sticker to “hi”"))
        assertNull(Reactions.parse(""))
    }

    private val becca = Person("+19725550111", "Becca")
    private fun msg(id: Long, body: String, from: Person? = null) = Message(id, body, id * 1000, from)

    @Test
    fun `finds the newest message the reaction quotes`() {
        val newestFirst = listOf(msg(3, "ok"), msg(2, "Let’s do lunch tomorrow", becca), msg(1, "Let’s do lunch tomorrow"))
        assertEquals(2L, Reactions.target(ParsedReaction("❤️", "Let's do lunch tomorrow"), newestFirst)?.id)
    }

    @Test
    fun `a quote cut short with an ellipsis matches the start`() {
        val long = "Can you pick up the kids at 3 and then grab milk on the way home please"
        val newestFirst = listOf(msg(2, "sure"), msg(1, long))
        assertEquals(1L, Reactions.target(ParsedReaction("👍", "Can you pick up the kids at 3 and then…"), newestFirst)?.id)
    }

    @Test
    fun `reaction texts are never a target, and no match is null`() {
        val newestFirst = listOf(msg(2, "Loved “hi”"), msg(1, "hi"))
        assertEquals(1L, Reactions.target(ParsedReaction("❤️", "hi"), newestFirst)?.id)
        assertNull(Reactions.target(ParsedReaction("❤️", "nope"), newestFirst))
    }

    @Test
    fun `a Google Messages quote finds its message`() {
        val reaction = checkNotNull(Reactions.parse("\u200a\u200b👍\u200b\u200a\"Sounds good\"\u200a\u200a"))
        assertEquals(1L, Reactions.target(reaction, listOf(msg(1, "Sounds good")))?.id)
    }

    @Test
    fun `reacting to a message, taking it back, and long quotes`() {
        val m = msg(1, "See you at the game", becca)
        assertEquals("Loved “See you at the game”", Reactions.reply(m, "❤️"))
        val mineAlready = m.copy(reactions = listOf(io.github.secnewt.messages.chats.Reaction("❤️", null)))
        assertEquals("Removed a heart from “See you at the game”", Reactions.reply(mineAlready, "❤️"))
        val long = msg(2, "x".repeat(300), becca)
        val text = checkNotNull(Reactions.reply(long, "👍"))
        // What we send still finds its message.
        assertEquals(2L, Reactions.target(checkNotNull(Reactions.parse(text)), listOf(long))?.id)
        assertEquals(null, Reactions.reply(msg(3, "", becca), "👍"))
    }

    @Test
    fun `sending uses iPhone wording`() {
        assertEquals("Loved “Let's do lunch”", Reactions.iphoneText("❤️", "Let's do lunch"))
        assertEquals("Reacted 🙏 to “x”", Reactions.iphoneText("🙏", "x"))
        assertEquals("Removed a like from “x”", Reactions.iphoneText("👍", "x", removed = true))
        // What we send, we can read back.
        assertEquals(ParsedReaction("🙏", "x"), Reactions.parse(Reactions.iphoneText("🙏", "x")))
    }
}
