package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatColorsTest {

    private val dave = Person("+14695550133", "Pastor Dave")
    private val sam = Person("+12145550122", "Sam")
    private val becca = Person("+19725550111", "Becca")
    private fun hues(people: List<Person>, picked: Map<String, PersonHue> = emptyMap()) =
        ChatColors.assign(people, picked).let { colors -> people.map { colors.getValue(ChatColors.key(it.address)) } }

    @Test
    fun `a name picks the same color Newt Phone gives it`() {
        assertEquals(PersonHue.TEAL, PersonHue.fromName("Sam"))
        assertEquals(PersonHue.TEAL, PersonHue.fromName("Pastor Dave"))
        assertEquals(PersonHue.YELLOW, PersonHue.fromName("Becca"))
    }

    @Test
    fun `two people whose names pick the same color get different ones`() {
        val (d, s, b) = hues(listOf(dave, sam, becca))
        assertNotEquals(d, s)
        assertEquals(PersonHue.YELLOW, b)
        // The earlier number keeps its own color; only the other one moves.
        assertEquals(PersonHue.TEAL, s)
    }

    @Test
    fun `nobody gets my blue unless I pick it`() {
        val tim = Person("5550001", "Tim")
        assertEquals(PersonHue.BLUE, PersonHue.fromName("Tim"))
        assertNotEquals(PersonHue.BLUE, hues(listOf(tim)).single())
        assertEquals(PersonHue.BLUE, hues(listOf(tim), mapOf(ChatColors.key(tim.address) to PersonHue.BLUE)).single())
    }

    @Test
    fun `a color I picked stays, and others move out of its way`() {
        val (d, s, b) = hues(listOf(dave, sam, becca), mapOf(ChatColors.key(becca.address) to PersonHue.TEAL))
        assertEquals(PersonHue.TEAL, b)
        assertTrue(d != PersonHue.TEAL && s != PersonHue.TEAL && d != s)
    }

    @Test
    fun `eleven other people all get different colors, and more than that repeat without failing`() {
        val eleven = (1..11).map { Person("97255501%02d".format(it), "Person $it") }
        assertEquals(11, hues(eleven).toSet().size)
        val fifteen = (1..15).map { Person("97255501%02d".format(it), "Person $it") }
        assertEquals(15, hues(fifteen).size)
    }

    @Test
    fun `the same number written two ways is one person`() {
        assertEquals(ChatColors.key("+1 (972) 555-0111"), ChatColors.key("9725550111"))
        val colors = ChatColors.assign(listOf(becca, Person("972-555-0111", "Becca")), emptyMap())
        assertEquals(1, colors.size)
    }

    @Test
    fun `the order Android lists people in doesn't change anyone's color`() {
        assertEquals(hues(listOf(dave, sam, becca)), hues(listOf(sam, becca, dave)).let { listOf(it[2], it[0], it[1]) })
    }
}
