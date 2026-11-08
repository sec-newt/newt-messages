package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDetailsTest {

    private val same = { a: String, b: String -> a.filter(Char::isDigit).takeLast(10) == b.filter(Char::isDigit).takeLast(10) }
    private val names = mapOf("+12545550121" to "Shilo", "+12545550120" to "Landon", "9725550199" to "Jerry")
    private fun person(n: String) = Person(n, names[n])
    private val me = "+19725550100"

    private fun sort(vararg rows: Pair<String, Int>, mine: List<String> = listOf(me)) =
        MessageDetails.sort(rows.toList(), mine, same, ::person)

    @Test
    fun `a group message shows its full To and Cc lists, with me marked`() {
        val (from, to, cc) = sort(
            "+12545550121" to MessageDetails.FROM,
            "9725550199" to MessageDetails.TO,
            "(972) 555-0100" to MessageDetails.CC,
            "+12545550120" to MessageDetails.CC,
        )
        assertEquals("Shilo", from?.person?.name)
        assertEquals(listOf("Jerry"), to.map { it.person.name })
        assertEquals(listOf(true, false), cc.map { it.me })
        assertEquals(listOf(null, "Landon"), cc.map { it.person.name })
    }

    @Test
    fun `my own sent message's placeholder is me, and Bcc counts as Cc`() {
        val (from, to, cc) = sort(
            "insert-address-token" to MessageDetails.FROM,
            "+12545550121" to MessageDetails.TO,
            "+12545550120" to MessageDetails.BCC,
        )
        assertEquals(true, from?.me)
        assertEquals(listOf("Shilo"), to.map { it.person.name })
        assertEquals(listOf("Landon"), cc.map { it.person.name })
    }

    @Test
    fun `someone listed twice shows once, and no From is null`() {
        val (from, to, _) = sort("+12545550121" to MessageDetails.TO, "2545550121" to MessageDetails.TO)
        assertNull(from)
        assertEquals(1, to.size)
    }

    @Test
    fun `without my number, nobody is marked me`() {
        val (_, to, _) = sort(me to MessageDetails.TO, mine = emptyList())
        assertEquals(listOf(false), to.map { it.me })
    }
}
